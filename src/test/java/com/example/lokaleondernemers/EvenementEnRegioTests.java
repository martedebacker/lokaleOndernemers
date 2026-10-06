package com.example.lokaleondernemers;

import com.example.lokaleondernemers.model.Evenement;
import com.example.lokaleondernemers.model.Onderneming;
import com.example.lokaleondernemers.model.OndernemingStatus;
import com.example.lokaleondernemers.model.Regio;
import com.example.lokaleondernemers.repository.EvenementRepository;
import com.example.lokaleondernemers.repository.OndernemingRepository;
import com.example.lokaleondernemers.repository.RegioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EvenementEnRegioTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    OndernemingRepository ondernemingRepository;
    @Autowired
    RegioRepository regioRepository;
    @Autowired
    EvenementRepository evenementRepository;

    private Onderneming onderneming(String slug) {
        return ondernemingRepository.findBySlug(slug).orElseThrow();
    }

    private Regio regio(String naam) {
        return regioRepository.findAll().stream().filter(r -> r.getNaam().equals(naam)).findFirst().orElseThrow();
    }

    private String binnenEenWeek(int uur) {
        return LocalDateTime.now().plusDays(7).withHour(uur).withMinute(0).toString().substring(0, 16);
    }

    @Test
    void demoEvenementenZijnZichtbaar() throws Exception {
        Regio antwerpen = regio("Antwerpen");
        mvc.perform(get("/evenementen")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Lokale ambachtenmarkt")))
                .andExpect(content().string(containsString("Brood- en kaasproeverij")));
        mvc.perform(get("/regio/" + antwerpen.getId())).andExpect(content().string(containsString("Lokale ambachtenmarkt")));
        mvc.perform(get("/evenementen").param("regio", regio("Limburg").getId().toString()))
                .andExpect(content().string(not(containsString("Lokale ambachtenmarkt"))));
        // Mede-organisator: het evenement staat ook op de winkelpagina van de hoeve.
        mvc.perform(get("/winkel/hoeve-de-linde")).andExpect(content().string(containsString("Brood- en kaasproeverij")));
        mvc.perform(get("/")).andExpect(content().string(containsString("Binnenkort te doen")));
    }

    @Test
    void beheerPaginasVoorEvenementenTonen() throws Exception {
        var admin = user("admin@test.be").roles("ADMIN");
        var bakker = user("bakker@demo.be").roles("ONDERNEMER");
        Evenement proeverij = evenementRepository.allesVan(onderneming("bakkerij-peeters")).stream()
                .filter(e -> e.getTitel().equals("Brood- en kaasproeverij")).findFirst().orElseThrow();
        mvc.perform(get("/admin/evenementen").with(admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/evenementen/nieuw").with(admin)).andExpect(status().isOk());
        mvc.perform(get("/admin/evenementen/" + proeverij.getId()).with(admin)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Je past het aan als beheerder")));
        mvc.perform(get("/beheer/evenementen").with(bakker)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Organisator")));
        mvc.perform(get("/beheer/evenementen/nieuw").with(bakker)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Hoeve De Linde")));
        mvc.perform(get("/beheer/evenementen/" + proeverij.getId()).with(bakker)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Brood- en kaasproeverij")));
        mvc.perform(get("/beheer/evenementen").with(user("hoeve@demo.be").roles("ONDERNEMER"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Niet meer deelnemen")));
    }

    @Test
    void beheerderMaaktPlatformEvenement() throws Exception {
        Regio limburg = regio("Limburg");
        mvc.perform(post("/admin/evenementen/nieuw").with(user("admin@test.be").roles("ADMIN")).with(csrf())
                        .param("titel", "Week van de lokale handel")
                        .param("startMoment", binnenEenWeek(10))
                        .param("eindMoment", binnenEenWeek(18))
                        .param("locatie", "Marktplein").param("gemeente", "Hasselt")
                        .param("regioId", limburg.getId().toString()))
                .andExpect(status().is3xxRedirection());
        Evenement e = evenementRepository.findByRegio(limburg).getFirst();
        assertThat(e.isDoorPlatform()).isTrue();
        mvc.perform(get("/regio/" + limburg.getId())).andExpect(content().string(containsString("Week van de lokale handel")));
        mvc.perform(get("/evenementen/" + e.getId())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Lokale Ondernemers")));
    }

    @Test
    void ondernemerOrganiseertSamenMetAndereOnderneming() throws Exception {
        Onderneming bakker = onderneming("bakkerij-peeters");
        Onderneming atelier = onderneming("atelier-klei-co");
        var bakkerUser = user("bakker@demo.be").roles("ONDERNEMER");
        mvc.perform(post("/beheer/evenementen/nieuw").with(bakkerUser).with(csrf())
                        .param("titel", "Workshop brood en bord")
                        .param("startMoment", binnenEenWeek(14))
                        .param("locatie", "Bruul 12").param("gemeente", "Mechelen")
                        .param("regioId", bakker.getRegio().getId().toString())
                        .param("ondernemingIds", atelier.getId().toString()))
                .andExpect(status().is3xxRedirection());
        Evenement e = evenementRepository.allesVan(atelier).stream()
                .filter(x -> x.getTitel().equals("Workshop brood en bord")).findFirst().orElseThrow();
        assertThat(e.getOrganisator().getId()).isEqualTo(bakker.getId());

        // De deelnemer mag het evenement niet bewerken, wel uitstappen.
        var atelierUser = user("atelier@demo.be").roles("ONDERNEMER");
        mvc.perform(get("/beheer/evenementen/" + e.getId()).with(atelierUser)).andExpect(status().isForbidden());
        mvc.perform(post("/beheer/evenementen/" + e.getId() + "/uitstappen").with(atelierUser).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(e.getOndernemingen()).isEmpty();

        // Een einde vóór het begin wordt geweigerd.
        mvc.perform(post("/beheer/evenementen/" + e.getId()).with(bakkerUser).with(csrf())
                        .param("titel", "Workshop brood en bord")
                        .param("startMoment", binnenEenWeek(14)).param("eindMoment", binnenEenWeek(10))
                        .param("locatie", "Bruul 12").param("gemeente", "Mechelen")
                        .param("regioId", bakker.getRegio().getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("vóór het begin")));
    }

    @Test
    void regioZonderActieveOndernemingenKanVerwijderdWorden() throws Exception {
        var admin = user("admin@test.be").roles("ADMIN");
        Regio antwerpen = regio("Antwerpen");
        Regio limburg = regio("Limburg");
        Regio brussel = regio("Brussel");

        // Antwerpen heeft actieve ondernemingen: blijft bestaan.
        mvc.perform(post("/admin/regios/" + antwerpen.getId() + "/verwijderen").with(admin).with(csrf())
                .param("verplaatsNaar", limburg.getId().toString()));
        assertThat(regioRepository.existsById(antwerpen.getId())).isTrue();

        // Brussel heeft niets gekoppeld: gewoon verwijderen.
        mvc.perform(post("/admin/regios/" + brussel.getId() + "/verwijderen").with(admin).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(regioRepository.existsById(brussel.getId())).isFalse();

        // Limburg met een onderneming in afwachting: kan verwijderd worden mits verplaatsing.
        Onderneming hoeve = onderneming("hoeve-de-linde");
        hoeve.setRegio(limburg);
        hoeve.setStatus(OndernemingStatus.IN_AFWACHTING);
        ondernemingRepository.flush();
        mvc.perform(post("/admin/regios/" + limburg.getId() + "/verwijderen").with(admin).with(csrf()));
        assertThat(regioRepository.existsById(limburg.getId())).isTrue();

        mvc.perform(get("/admin/regios").with(admin)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Verplaats niet-actieve ondernemingen")));
        mvc.perform(post("/admin/regios/" + limburg.getId() + "/verwijderen").with(admin).with(csrf())
                        .param("verplaatsNaar", antwerpen.getId().toString()))
                .andExpect(status().is3xxRedirection());
        assertThat(regioRepository.existsById(limburg.getId())).isFalse();
        assertThat(hoeve.getRegio().getId()).isEqualTo(antwerpen.getId());
    }
}
