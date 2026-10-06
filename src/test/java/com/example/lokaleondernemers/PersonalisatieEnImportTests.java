package com.example.lokaleondernemers;

import com.example.lokaleondernemers.model.Onderneming;
import com.example.lokaleondernemers.model.Product;
import com.example.lokaleondernemers.repository.OndernemingRepository;
import com.example.lokaleondernemers.repository.ProductRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PersonalisatieEnImportTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    OndernemingRepository ondernemingRepository;
    @Autowired
    ProductRepository productRepository;

    private final RequestPostProcessor bakker = user("bakker@demo.be").roles("ONDERNEMER");

    private Onderneming bakkerij() {
        return ondernemingRepository.findBySlug("bakkerij-peeters").orElseThrow();
    }

    private Map<String, Product> productenPerNaam() {
        return productRepository.findByOndernemingAndVerwijderdFalseOrderByNaamAsc(bakkerij()).stream()
                .collect(Collectors.toMap(Product::getNaam, p -> p));
    }

    private static byte[] excel(List<List<Object>> rijen) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream uit = new ByteArrayOutputStream()) {
            Sheet blad = wb.createSheet();
            for (int r = 0; r < rijen.size(); r++) {
                Row rij = blad.createRow(r);
                for (int c = 0; c < rijen.get(r).size(); c++) {
                    Object w = rijen.get(r).get(c);
                    if (w instanceof Number n) {
                        rij.createCell(c).setCellValue(n.doubleValue());
                    } else if (w != null) {
                        rij.createCell(c).setCellValue(w.toString());
                    }
                }
            }
            wb.write(uit);
            return uit.toByteArray();
        }
    }

    @Test
    void ondernemerKiestPaletEnZichtbareInfo() throws Exception {
        Onderneming o = bakkerij();
        mvc.perform(get("/winkel/bakkerij-peeters")).andExpect(content().string(containsString("Bruul 12, 2800 Mechelen")));

        mvc.perform(multipart("/beheer/onderneming").with(bakker).with(csrf())
                        .param("naam", o.getNaam()).param("gemeente", o.getGemeente())
                        .param("straat", o.getStraat()).param("postcode", o.getPostcode())
                        .param("regioId", o.getRegio().getId().toString())
                        .param("telefoon", "015 12 34 56")
                        .param("themaKleur", "#1f4e79").param("accentKleur", "#e0a526").param("achtergrondKleur", "#f4f8fb")
                        // aangevinkt: openingsuren/e-mail/voorraad/evenementen; uitgevinkt: adres en telefoon
                        .param("_toonAdres", "on").param("_toonTelefoon", "on")
                        .param("toonOphaalInfo", "true").param("_toonOphaalInfo", "on")
                        .param("toonEmail", "true").param("_toonEmail", "on")
                        .param("toonVoorraad", "true").param("_toonVoorraad", "on")
                        .param("toonEvenementen", "true").param("_toonEvenementen", "on"))
                .andExpect(status().is3xxRedirection());

        assertThat(o.isToonAdres()).isFalse();
        assertThat(o.isToonTelefoon()).isFalse();
        assertThat(o.getAccentKleur()).isEqualTo("#e0a526");
        mvc.perform(get("/winkel/bakkerij-peeters")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Bruul 12, 2800 Mechelen"))))
                .andExpect(content().string(not(containsString("015 12 34 56"))))
                .andExpect(content().string(containsString("--winkel-accent:#e0a526")))
                .andExpect(content().string(containsString("Het exacte ophaaladres krijg je bij je bestelling")));
        mvc.perform(get("/beheer/onderneming").with(bakker)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Kleurenpalet")))
                .andExpect(content().string(containsString("Wat zien klanten")));
        mvc.perform(get("/beheer").with(bakker)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Maak je winkelpagina helemaal de jouwe")));
    }

    @Test
    void productenImporterenOpBasisVanKopregel() throws Exception {
        BigDecimal oudePrijsKoffiekoek = productenPerNaam().get("Volkoren boterkoeken").getPrijs();
        byte[] bestand = excel(List.of(
                List.of("Product", "Omschrijving", "Prijs (€)", "Stock", "Online", "Iets anders"),
                List.of("Speltbrood", "Met spelt uit de streek", "4,80", 15, "ja", "x"),
                List.of("Desembrood", "", 4.5, 8, "ja", ""),
                List.of("Pistolets", "", "geen idee", 10, "ja", ""),
                List.of("Speltbrood", "", 3, 1, "nee", "")));
        MockHttpSession sessie = new MockHttpSession();

        mvc.perform(multipart("/beheer/producten/importeren")
                        .file(new MockMultipartFile("bestand", "producten.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bestand))
                        .session(sessie).with(bakker).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/beheer/producten/importeren").session(sessie).with(bakker)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Importeer 2 producten")))
                .andExpect(content().string(containsString("is geen geldige prijs")))
                .andExpect(content().string(containsString("Komt meer dan eens voor")));

        // Koppeling aanpassen: zonder prijskolom kan er niet geïmporteerd worden.
        mvc.perform(post("/beheer/producten/importeren/koppeling").session(sessie).with(bakker).with(csrf())
                        .param("kolom0", "NAAM").param("kolom3", "VOORRAAD"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/beheer/producten/importeren").session(sessie).with(bakker))
                .andExpect(content().string(containsString("Duid aan welke kolom de prijs bevat")));
        mvc.perform(post("/beheer/producten/importeren/koppeling").session(sessie).with(bakker).with(csrf())
                        .param("kolom0", "NAAM").param("kolom1", "BESCHRIJVING").param("kolom2", "PRIJS")
                        .param("kolom3", "VOORRAAD").param("kolom4", "ZICHTBAAR"))
                .andExpect(status().is3xxRedirection());

        mvc.perform(post("/beheer/producten/importeren/bevestigen").session(sessie).with(bakker).with(csrf()))
                .andExpect(status().is3xxRedirection());

        Map<String, Product> producten = productenPerNaam();
        assertThat(producten.get("Speltbrood").getPrijs()).isEqualByComparingTo("4.80");
        assertThat(producten.get("Speltbrood").getVoorraad()).isEqualTo(15);
        assertThat(producten.get("Speltbrood").getBeschrijving()).isEqualTo("Met spelt uit de streek");
        assertThat(producten.get("Desembrood").getPrijs()).isEqualByComparingTo("4.50");
        assertThat(producten.get("Desembrood").getVoorraad()).isEqualTo(8);
        assertThat(producten).doesNotContainKey("Pistolets");
        assertThat(producten.get("Volkoren boterkoeken").getPrijs()).isEqualByComparingTo(oudePrijsKoffiekoek);
        assertThat(sessie.getAttribute("productImport")).isNull();
    }

    @Test
    void sjabloonKanTerugGeimporteerdWorden() throws Exception {
        byte[] sjabloon = mvc.perform(get("/beheer/producten/importeren/sjabloon").with(bakker))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        MockHttpSession sessie = new MockHttpSession();
        mvc.perform(multipart("/beheer/producten/importeren")
                .file(new MockMultipartFile("bestand", "sjabloon.xlsx", "application/octet-stream", sjabloon))
                .session(sessie).with(bakker).with(csrf()));
        mvc.perform(get("/beheer/producten/importeren").session(sessie).with(bakker))
                .andExpect(content().string(containsString("Importeer 2 producten")))
                .andExpect(content().string(not(containsString("Duid aan welke kolom"))));
    }

    @Test
    void ongeldigBestandGeeftDuidelijkeFout() throws Exception {
        MockHttpSession sessie = new MockHttpSession();
        mvc.perform(multipart("/beheer/producten/importeren")
                        .file(new MockMultipartFile("bestand", "foto.xlsx", "application/octet-stream", "geen excel".getBytes()))
                        .session(sessie).with(bakker).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(String.valueOf(result.getFlashMap().get("fout")))
                        .contains("kon niet gelezen worden"));
    }
}
