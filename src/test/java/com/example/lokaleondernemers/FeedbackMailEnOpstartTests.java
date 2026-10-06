package com.example.lokaleondernemers;

import com.example.lokaleondernemers.mail.BestellingMailEvent;
import com.example.lokaleondernemers.mail.MailService;
import com.example.lokaleondernemers.model.Bestelling;
import com.example.lokaleondernemers.model.Feedback;
import com.example.lokaleondernemers.model.Onderneming;
import com.example.lokaleondernemers.model.OpstarthulpStatus;
import com.example.lokaleondernemers.model.Product;
import com.example.lokaleondernemers.repository.BestellingRepository;
import com.example.lokaleondernemers.repository.FeedbackRepository;
import com.example.lokaleondernemers.repository.OndernemingRepository;
import com.example.lokaleondernemers.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class FeedbackMailEnOpstartTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    OndernemingRepository ondernemingRepository;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    BestellingRepository bestellingRepository;
    @Autowired
    FeedbackRepository feedbackRepository;
    @Autowired
    MailService mailService;
    @Autowired
    ApplicationEvents events;

    private final RequestPostProcessor bakker = user("bakker@demo.be").roles("ONDERNEMER");
    private final RequestPostProcessor admin = user("admin@test.be").roles("ADMIN");
    private final RequestPostProcessor klant = user("klant@demo.be").roles("KLANT");

    private Onderneming bakkerij() {
        return ondernemingRepository.findBySlug("bakkerij-peeters").orElseThrow();
    }

    private Bestelling plaatsBestelling() throws Exception {
        Product p = productRepository.findByOndernemingAndVerwijderdFalseOrderByNaamAsc(bakkerij()).getFirst();
        MockHttpSession sessie = new MockHttpSession();
        mvc.perform(post("/winkelmand/toevoegen").session(sessie).with(csrf())
                .param("productId", p.getId().toString()).param("aantal", "1"));
        mvc.perform(post("/afrekenen").session(sessie).with(klant).with(csrf())
                .param("gewensteOphaaldatum", LocalDate.now().plusDays(1).toString()));
        return bestellingRepository.findByOndernemingOrderByGeplaatstOpDesc(bakkerij()).getFirst();
    }

    @Test
    void registratieToontTestfaseEnOpstarthulp() throws Exception {
        mvc.perform(get("/registreren/ondernemer")).andExpect(status().isOk())
                .andExpect(content().string(containsString("testfase tot")))
                .andExpect(content().string(containsString("1 februari 2027")))
                .andExpect(content().string(containsString("geheel vrijblijvend")))
                .andExpect(content().string(containsString("Laat het ons doen")))
                .andExpect(content().string(containsString("40,00")));

        Long regioId = bakkerij().getRegio().getId();
        // Zonder akkoord met de testfase lukt registreren niet.
        mvc.perform(post("/registreren/ondernemer").with(csrf())
                        .param("ondernemingNaam", "Kaasmakerij Test").param("regioId", regioId.toString())
                        .param("gemeente", "Lier").param("voornaam", "An").param("achternaam", "Kaas")
                        .param("email", "an@test.be").param("wachtwoord", "geheim1234").param("wachtwoordHerhaling", "geheim1234")
                        .param("opstarthulp", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bevestig dat je de voorwaarden van de testfase gelezen hebt")));

        mvc.perform(post("/registreren/ondernemer").with(csrf())
                        .param("ondernemingNaam", "Kaasmakerij Test").param("regioId", regioId.toString())
                        .param("gemeente", "Lier").param("voornaam", "An").param("achternaam", "Kaas")
                        .param("email", "an@test.be").param("wachtwoord", "geheim1234").param("wachtwoordHerhaling", "geheim1234")
                        .param("opstarthulp", "true").param("opstartWensen", "Graag warme kleuren")
                        .param("akkoordTestfase", "true"))
                .andExpect(status().is3xxRedirection());
        Onderneming o = ondernemingRepository.findBySlug("kaasmakerij-test").orElseThrow();
        assertThat(o.getOpstarthulp()).isEqualTo(OpstarthulpStatus.AANGEVRAAGD);
        assertThat(o.getOpstartWensen()).isEqualTo("Graag warme kleuren");

        mvc.perform(get("/admin").with(admin))
                .andExpect(content().string(containsString("Opstarthulp: winkels die wij inrichten")))
                .andExpect(content().string(containsString("Graag warme kleuren")));
    }

    @Test
    void bestaandeOndernemerVraagtOpstarthulpAchterafAan() throws Exception {
        mvc.perform(get("/beheer").with(bakker)).andExpect(content().string(containsString("Opstarthulp aanvragen")));
        mvc.perform(post("/beheer/opstarthulp").with(bakker).with(csrf()).param("wensen", "Nieuwe foto's"))
                .andExpect(status().is3xxRedirection());
        assertThat(bakkerij().getOpstarthulp()).isEqualTo(OpstarthulpStatus.AANGEVRAAGD);
        mvc.perform(get("/beheer").with(bakker)).andExpect(content().string(containsString("Wij richten je winkelpagina voor je in en nemen contact met je op")));
    }

    @Test
    void beheerderRichtWinkelInVoorOndernemer() throws Exception {
        Onderneming o = bakkerij();
        mvc.perform(get("/beheer").with(admin)).andExpect(redirectedUrl("/admin/ondernemingen?kies"));

        MockHttpSession sessie = new MockHttpSession();
        mvc.perform(post("/admin/ondernemingen/" + o.getId() + "/beheren").session(sessie).with(admin).with(csrf()))
                .andExpect(redirectedUrl("/beheer"));
        mvc.perform(get("/beheer/producten").session(sessie).with(admin)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Je richt deze winkel in als beheerder")))
                .andExpect(content().string(containsString("Desembrood")));
        mvc.perform(post("/beheer/producten/nieuw").session(sessie).with(admin).with(csrf())
                        .param("naam", "Suikerbrood").param("prijs", "6.50").param("voorraad", "5").param("zichtbaar", "true"))
                .andExpect(status().is3xxRedirection());
        assertThat(productRepository.findByOndernemingAndVerwijderdFalseOrderByNaamAsc(o))
                .extracting(Product::getNaam).contains("Suikerbrood");

        mvc.perform(post("/admin/beheren/stoppen").session(sessie).with(admin).with(csrf()));
        mvc.perform(get("/beheer").session(sessie).with(admin)).andExpect(redirectedUrl("/admin/ondernemingen?kies"));
    }

    @Test
    void feedbackVanOndernemerEnAntwoordVanBeheerder() throws Exception {
        mvc.perform(get("/beheer/feedback").with(bakker)).andExpect(status().isOk())
                .andExpect(content().string(containsString("testfase")));
        mvc.perform(post("/beheer/feedback").with(bakker).with(csrf())
                        .param("soort", "IDEE").param("onderwerp", "Producten sorteren")
                        .param("bericht", "Graag sorteren op prijs.").param("tevredenheid", "4"))
                .andExpect(status().is3xxRedirection());
        Feedback f = feedbackRepository.findByOndernemingOrderByAangemaaktOpDesc(bakkerij()).getFirst();
        assertThat(f.getTevredenheid()).isEqualTo(4);

        mvc.perform(get("/admin/feedback").with(admin)).andExpect(content().string(containsString("Producten sorteren")));
        mvc.perform(get("/admin").with(admin)).andExpect(content().string(containsString("Nieuwe feedback")));
        mvc.perform(post("/admin/feedback/" + f.getId()).with(admin).with(csrf())
                        .param("antwoord", "Goed idee, staat op de planning!").param("status", "NIEUW"))
                .andExpect(status().is3xxRedirection());
        assertThat(f.getStatus()).isEqualTo(Feedback.Status.IN_BEHANDELING);

        mvc.perform(get("/beheer/feedback").with(bakker))
                .andExpect(content().string(containsString("Goed idee, staat op de planning!")));
        // Lege feedback wordt geweigerd.
        mvc.perform(post("/beheer/feedback").with(bakker).with(csrf()).param("soort", "VRAAG"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vertel ons wat je wil delen")));
    }

    @Test
    void elkeWijzigingAanEenBestellingGeeftEenMailEvent() throws Exception {
        Bestelling b = plaatsBestelling();
        assertThat(events.stream(BestellingMailEvent.class))
                .anyMatch(e -> e.soort() == BestellingMailEvent.Soort.GEPLAATST && e.bestellingIds().contains(b.getId()));

        mvc.perform(post("/beheer/bestellingen/" + b.getId()).with(bakker).with(csrf()).param("status", "BEVESTIGD"));
        mvc.perform(post("/beheer/bestellingen/" + b.getId()).with(bakker).with(csrf()).param("ophaalAfspraak", "Zaterdag 10u"));
        // Niets gewijzigd: geen nieuwe mail.
        mvc.perform(post("/beheer/bestellingen/" + b.getId()).with(bakker).with(csrf()).param("ophaalAfspraak", "Zaterdag 10u"));

        List<BestellingMailEvent> updates = events.stream(BestellingMailEvent.class)
                .filter(e -> e.soort() == BestellingMailEvent.Soort.BIJGEWERKT).toList();
        assertThat(updates).hasSize(2);
        assertThat(updates.get(0).statusGewijzigd()).isTrue();
        assertThat(updates.get(1).statusGewijzigd()).isFalse();
    }

    @Test
    void mailsWordenInDeHuisstijlVanDeOndernemerOpgesteld() throws Exception {
        Bestelling b = plaatsBestelling();
        Path map = Path.of("target/mail-voorbeelden");
        long voor = Files.exists(map) ? Files.list(map).count() : 0;

        // Geen mailserver ingesteld in de test: de mails worden als bestand bewaard.
        mailService.verstuur(BestellingMailEvent.geplaatst(List.of(b.getId())));

        List<Path> bestanden;
        try (Stream<Path> s = Files.list(map)) {
            bestanden = s.filter(p -> p.getFileName().toString().contains(b.getNummer())).toList();
        }
        assertThat(Files.list(map).count()).isEqualTo(voor + 2);
        String klantMail = Files.readString(bestanden.stream().filter(p -> p.toString().endsWith("-klant.html")).findFirst().orElseThrow());
        String ondernemerMail = Files.readString(bestanden.stream().filter(p -> p.toString().endsWith("-ondernemer.html")).findFirst().orElseThrow());

        assertThat(klantMail).contains("Aan: klant@demo.be", "Bedankt voor je bestelling!", "Bakkerij Peeters",
                "background:#b5651d", "Dag Kim", "/mijn/bestellingen/" + b.getId(), "Bruul 12");
        assertThat(ondernemerMail).contains("Je hebt een nieuwe bestelling", "klant@demo.be", "/beheer/bestellingen/" + b.getId());

        mvc.perform(get("/beheer/onderneming/mail-voorbeeld").param("status", "KLAAR").with(bakker))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Je bestelling staat klaar!")))
                .andExpect(content().string(containsString("#b5651d")));
    }
}
