package com.example.lokaleondernemers;

import com.example.lokaleondernemers.model.BestelStatus;
import com.example.lokaleondernemers.model.Bestelling;
import com.example.lokaleondernemers.model.Onderneming;
import com.example.lokaleondernemers.model.OndernemingStatus;
import com.example.lokaleondernemers.model.Product;
import com.example.lokaleondernemers.repository.BestellingRepository;
import com.example.lokaleondernemers.repository.GebruikerRepository;
import com.example.lokaleondernemers.repository.OndernemingRepository;
import com.example.lokaleondernemers.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlatformFlowTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    OndernemingRepository ondernemingRepository;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    BestellingRepository bestellingRepository;
    @Autowired
    GebruikerRepository gebruikerRepository;

    private Onderneming bakkerij() {
        return ondernemingRepository.findBySlug("bakkerij-peeters").orElseThrow();
    }

    private Product eersteProduct(Onderneming o) {
        return productRepository.findByOndernemingAndVerwijderdFalseOrderByNaamAsc(o).getFirst();
    }

    @Test
    void publiekePaginasWerken() throws Exception {
        Onderneming o = bakkerij();
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Bakkerij Peeters")));
        mvc.perform(get("/regio/" + o.getRegio().getId())).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Bakkerij Peeters")));
        mvc.perform(get("/ondernemingen")).andExpect(status().isOk());
        mvc.perform(get("/producten").param("zoek", "brood")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Desembrood")));
        mvc.perform(get("/winkel/bakkerij-peeters")).andExpect(status().isOk());
        mvc.perform(get("/product/" + eersteProduct(o).getId())).andExpect(status().isOk());
        mvc.perform(get("/hoe-werkt-het")).andExpect(status().isOk());
        mvc.perform(get("/winkel/bestaat-niet")).andExpect(status().isNotFound());
    }

    @Test
    void beveiligdePaginasVereisenJuisteRol() throws Exception {
        mvc.perform(get("/beheer")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/admin").with(user("klant@demo.be").roles("KLANT"))).andExpect(status().isForbidden());
        mvc.perform(get("/beheer").with(user("klant@demo.be").roles("KLANT"))).andExpect(status().isForbidden());
        mvc.perform(get("/admin").with(user("admin@test.be").roles("ADMIN"))).andExpect(status().isOk());
        mvc.perform(get("/beheer").with(user("bakker@demo.be").roles("ONDERNEMER"))).andExpect(status().isOk());
    }

    @Test
    void klantBestelt_ondernemerVerwerkt_voorraadKlopt() throws Exception {
        Onderneming o = bakkerij();
        Product product = eersteProduct(o);
        int voorraadVooraf = product.getVoorraad();
        MockHttpSession sessie = new MockHttpSession();
        var klant = user("klant@demo.be").roles("KLANT");

        mvc.perform(post("/winkelmand/toevoegen").session(sessie).with(csrf())
                        .param("productId", product.getId().toString()).param("aantal", "2"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/winkelmand").session(sessie)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(product.getNaam())));

        mvc.perform(post("/afrekenen").session(sessie).with(klant).with(csrf())
                        .param("gewensteOphaaldatum", LocalDate.now().plusDays(2).toString())
                        .param("opmerking", "Graag na 17u"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/mijn/bestellingen/bevestigd?ids=*"));

        List<Bestelling> bestellingen = bestellingRepository.findByOndernemingOrderByGeplaatstOpDesc(o);
        assertThat(bestellingen).hasSize(1);
        Bestelling b = bestellingen.getFirst();
        assertThat(b.getStatus()).isEqualTo(BestelStatus.GEPLAATST);
        assertThat(b.getTotaal()).isEqualByComparingTo(product.getPrijs().multiply(java.math.BigDecimal.TWO));
        assertThat(productRepository.findById(product.getId()).orElseThrow().getVoorraad()).isEqualTo(voorraadVooraf - 2);

        // Een andere ondernemer mag deze bestelling niet zien.
        mvc.perform(get("/beheer/bestellingen/" + b.getId()).with(user("hoeve@demo.be").roles("ONDERNEMER")))
                .andExpect(status().isForbidden());

        var bakker = user("bakker@demo.be").roles("ONDERNEMER");
        mvc.perform(post("/beheer/bestellingen/" + b.getId()).with(bakker).with(csrf())
                        .param("status", "BEVESTIGD").param("ophaalAfspraak", "Zaterdag vanaf 10u"))
                .andExpect(status().is3xxRedirection());
        assertThat(b.getStatus()).isEqualTo(BestelStatus.BEVESTIGD);
        assertThat(b.getOphaalAfspraak()).isEqualTo("Zaterdag vanaf 10u");

        // Ongeldige overgang (bevestigd -> afgehaald zonder klaar) wordt geweigerd.
        mvc.perform(post("/beheer/bestellingen/" + b.getId()).with(bakker).with(csrf()).param("status", "AFGEHAALD"))
                .andExpect(status().is3xxRedirection());
        assertThat(b.getStatus()).isEqualTo(BestelStatus.BEVESTIGD);

        // Klant kan een bevestigde bestelling niet meer zelf annuleren.
        mvc.perform(post("/mijn/bestellingen/" + b.getId() + "/annuleren").with(klant).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(b.getStatus()).isEqualTo(BestelStatus.BEVESTIGD);

        // Ondernemer annuleert: voorraad komt terug.
        mvc.perform(post("/beheer/bestellingen/" + b.getId()).with(bakker).with(csrf()).param("status", "GEANNULEERD"))
                .andExpect(status().is3xxRedirection());
        assertThat(b.getStatus()).isEqualTo(BestelStatus.GEANNULEERD);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getVoorraad()).isEqualTo(voorraadVooraf);
    }

    @Test
    void nietMeerBestellenDanVoorraad() throws Exception {
        Product product = eersteProduct(bakkerij());
        MockHttpSession sessie = new MockHttpSession();
        mvc.perform(post("/winkelmand/toevoegen").session(sessie).with(csrf())
                .param("productId", product.getId().toString())
                .param("aantal", String.valueOf(product.getVoorraad() + 1)));
        mvc.perform(get("/winkelmand").session(sessie))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Je winkelmand is leeg")));
    }

    @Test
    void nieuweOndernemerWachtOpGoedkeuringVanAdmin() throws Exception {
        Long regioId = bakkerij().getRegio().getId();
        mvc.perform(post("/registreren/ondernemer").with(csrf())
                        .param("ondernemingNaam", "Brouwerij Het Anker Test")
                        .param("regioId", regioId.toString())
                        .param("gemeente", "Lier")
                        .param("voornaam", "Piet").param("achternaam", "Brouwer")
                        .param("email", "piet@test.be")
                        .param("wachtwoord", "geheim1234").param("wachtwoordHerhaling", "geheim1234")
                        .param("akkoordTestfase", "true"))
                .andExpect(status().is3xxRedirection());

        Onderneming nieuw = ondernemingRepository.findBySlug("brouwerij-het-anker-test").orElseThrow();
        assertThat(nieuw.getStatus()).isEqualTo(OndernemingStatus.IN_AFWACHTING);
        mvc.perform(get("/winkel/" + nieuw.getSlug())).andExpect(status().isNotFound());

        // De ondernemer kan al producten toevoegen, maar kan zichzelf niet goedkeuren via extra parameters.
        var piet = user("piet@test.be").roles("ONDERNEMER");
        mvc.perform(post("/beheer/producten/nieuw").with(piet).with(csrf())
                        .param("naam", "Tripel").param("prijs", "2.80").param("voorraad", "24")
                        .param("zichtbaar", "true").param("status", "ACTIEF"))
                .andExpect(status().is3xxRedirection());
        assertThat(nieuw.getStatus()).isEqualTo(OndernemingStatus.IN_AFWACHTING);
        assertThat(productRepository.findByOndernemingAndVerwijderdFalseOrderByNaamAsc(nieuw)).hasSize(1);

        // Andermans product bewerken mag niet.
        Product vanBakker = eersteProduct(bakkerij());
        mvc.perform(get("/beheer/producten/" + vanBakker.getId()).with(piet)).andExpect(status().isForbidden());

        mvc.perform(post("/admin/ondernemingen/" + nieuw.getId() + "/status")
                        .with(user("admin@test.be").roles("ADMIN")).with(csrf()).param("status", "ACTIEF"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/winkel/" + nieuw.getSlug())).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Tripel")));
    }

    @Test
    void dubbelEmailadresWordtGeweigerd() throws Exception {
        mvc.perform(post("/registreren").with(csrf())
                        .param("voornaam", "Kim").param("achternaam", "Dubbel")
                        .param("email", "klant@demo.be")
                        .param("wachtwoord", "geheim1234").param("wachtwoordHerhaling", "geheim1234"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Er bestaat al een account")));
        assertThat(gebruikerRepository.findAll().stream().filter(g -> g.getEmail().equals("klant@demo.be"))).hasSize(1);
    }
}
