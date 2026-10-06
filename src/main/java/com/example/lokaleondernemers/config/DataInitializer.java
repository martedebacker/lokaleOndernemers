package com.example.lokaleondernemers.config;

import com.example.lokaleondernemers.dto.EvenementForm;
import com.example.lokaleondernemers.dto.OndernemingForm;
import com.example.lokaleondernemers.dto.ProductForm;
import com.example.lokaleondernemers.model.Gebruiker;
import com.example.lokaleondernemers.model.Onderneming;
import com.example.lokaleondernemers.model.OndernemingStatus;
import com.example.lokaleondernemers.model.Regio;
import com.example.lokaleondernemers.model.Rol;
import com.example.lokaleondernemers.repository.GebruikerRepository;
import com.example.lokaleondernemers.repository.OndernemingRepository;
import com.example.lokaleondernemers.repository.RegioRepository;
import com.example.lokaleondernemers.service.EvenementService;
import com.example.lokaleondernemers.service.GebruikerService;
import com.example.lokaleondernemers.service.OndernemingService;
import com.example.lokaleondernemers.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.List;

/**
 * Vult een lege databank met de regio's en een beheerdersaccount, en optioneel met
 * voorbeeldondernemingen (app.demo-data=true) zodat de site meteen iets te tonen heeft.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final RegioRepository regioRepository;
    private final GebruikerRepository gebruikerRepository;
    private final OndernemingRepository ondernemingRepository;
    private final GebruikerService gebruikerService;
    private final OndernemingService ondernemingService;
    private final ProductService productService;
    private final EvenementService evenementService;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.wachtwoord}")
    private String adminWachtwoord;

    @Value("${app.demo-data:false}")
    private boolean demoData;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (regioRepository.count() == 0) {
            List.of(
                    new Regio("Antwerpen", "Van de Kempen tot de Scheldestad."),
                    new Regio("Limburg", "Mijnstreek, Haspengouw en het Maasland."),
                    new Regio("Oost-Vlaanderen", "Gent, het Meetjesland, de Vlaamse Ardennen en het Waasland."),
                    new Regio("Vlaams-Brabant", "Leuven, het Pajottenland en het Hageland."),
                    new Regio("West-Vlaanderen", "De kust, de Westhoek en de Leiestreek."),
                    new Regio("Brussel", "Het Brussels Hoofdstedelijk Gewest.")
            ).forEach(regioRepository::save);
            log.info("Standaardregio's aangemaakt.");
        }

        if (!gebruikerRepository.existsByRol(Rol.ADMIN)) {
            gebruikerService.maakAan("Beheerder", "Platform", adminEmail, adminWachtwoord, Rol.ADMIN);
            log.warn("Beheerdersaccount '{}' aangemaakt. Wijzig het wachtwoord na de eerste login!", adminEmail);
        }

        if (demoData && ondernemingRepository.count() == 0) {
            maakDemoData();
        }
    }

    private void maakDemoData() {
        List<Regio> regios = regioRepository.findAllByOrderByNaamAsc();
        Regio antwerpen = regios.stream().filter(r -> r.getNaam().equals("Antwerpen")).findFirst().orElse(regios.get(0));
        Regio oostVl = regios.stream().filter(r -> r.getNaam().equals("Oost-Vlaanderen")).findFirst().orElse(regios.get(0));

        Onderneming bakker = demoOnderneming("bakker@demo.be", "Jan", "Peeters", "Bakkerij Peeters", antwerpen,
                "Mechelen", "2800", "Bruul 12", "Ambachtelijk brood, elke ochtend vers.",
                "Al drie generaties bakken we brood en gebak met lokale bloem van de molen om de hoek. "
                        + "Bestel online en haal je bestelling vers af aan onze toonbank.",
                "Di-za: 7u - 18u\nZo: 7u - 12u\nMaandag gesloten", "#b5651d");
        demoProduct(bakker, "Desembrood", "Brood", "4.20", "per stuk", 20,
                "Langzaam gerezen desembrood met een krokante korst.");
        demoProduct(bakker, "Volkoren boterkoeken", "Koffiekoeken", "1.60", "per stuk", 40,
                "Luchtige boterkoeken met volkorenmeel.");
        demoProduct(bakker, "Rijsttaart", "Taarten", "14.50", "per taart", 6,
                "Klassieke Vlaamse rijsttaart, 6 personen.");

        Onderneming hoeve = demoOnderneming("hoeve@demo.be", "Els", "De Smet", "Hoeve De Linde", oostVl,
                "Oudenaarde", "9700", "Lindestraat 4", "Zuivel en groenten van eigen hoeve.",
                "Op onze familiehoeve in de Vlaamse Ardennen maken we kaas, yoghurt en boter van de melk "
                        + "van onze eigen koeien. In het seizoen verkopen we ook groenten uit de moestuin.",
                "Woensdag en vrijdag: 14u - 18u\nZaterdag: 9u - 12u", "#2f6f4f");
        demoProduct(hoeve, "Hoevekaas jong belegen", "Kaas", "18.90", "per kg", 15,
                "Zachte kaas, 8 weken gerijpt in onze eigen kelder.");
        demoProduct(hoeve, "Volle yoghurt", "Zuivel", "2.40", "per pot (500 g)", 30,
                "Romige yoghurt van volle hoevemelk.");
        demoProduct(hoeve, "Seizoensgroentepakket", "Groenten", "12.00", "per pakket", 10,
                "Een gevarieerd pakket met wat de moestuin deze week geeft.");

        Onderneming atelier = demoOnderneming("atelier@demo.be", "Lotte", "Wouters", "Atelier Klei & Co", antwerpen,
                "Antwerpen", "2000", "Kloosterstraat 88", "Handgemaakt keramiek uit Antwerpen.",
                "Elk stuk wordt met de hand gedraaid en geglazuurd in ons atelier. Kom je bestelling "
                        + "ophalen en neem meteen een kijkje achter de schermen.",
                "Donderdag t.e.m. zaterdag: 11u - 17u", "#6a4c93");
        demoProduct(atelier, "Mok 'Schelde'", "Servies", "28.00", "per stuk", 12,
                "Mok van steengoed met blauwgroen glazuur, 300 ml.");
        demoProduct(atelier, "Ontbijtbord", "Servies", "32.00", "per stuk", 8,
                "Bord van 21 cm, vaatwasbestendig.");

        LocalDate zaterdag = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
        EvenementForm markt = new EvenementForm();
        markt.setTitel("Lokale ambachtenmarkt");
        markt.setBeschrijving("Ontmoet de ondernemers van het platform, proef en koop rechtstreeks bij de makers.");
        markt.setStartMoment(LocalDateTime.of(zaterdag.plusWeeks(1), LocalTime.of(10, 0)));
        markt.setEindMoment(LocalDateTime.of(zaterdag.plusWeeks(1), LocalTime.of(17, 0)));
        markt.setLocatie("Grote Markt");
        markt.setGemeente("Mechelen");
        markt.setRegioId(antwerpen.getId());
        markt.setPrijsInfo("Gratis");
        markt.setOndernemingIds(List.of(bakker.getId(), atelier.getId()));
        evenementService.opslaanAlsBeheerder(null, markt);

        EvenementForm proeverij = new EvenementForm();
        proeverij.setTitel("Brood- en kaasproeverij");
        proeverij.setBeschrijving("Bakkerij Peeters en Hoeve De Linde laten je proeven van vers desembrood met hoevekaas.");
        proeverij.setStartMoment(LocalDateTime.of(zaterdag, LocalTime.of(14, 0)));
        proeverij.setEindMoment(LocalDateTime.of(zaterdag, LocalTime.of(17, 0)));
        proeverij.setLocatie("Bruul 12");
        proeverij.setGemeente("Mechelen");
        proeverij.setRegioId(antwerpen.getId());
        proeverij.setPrijsInfo("€ 5 per persoon");
        proeverij.setOndernemingIds(List.of(hoeve.getId()));
        evenementService.opslaanAlsOnderneming(bakker, null, proeverij);

        gebruikerService.maakAan("Kim", "Klant", "klant@demo.be", "demo1234", Rol.KLANT);
        log.info("Demodata aangemaakt (ondernemers: bakker@demo.be, hoeve@demo.be, atelier@demo.be; "
                + "klant: klant@demo.be; wachtwoord telkens 'demo1234').");
    }

    private Onderneming demoOnderneming(String email, String voornaam, String achternaam, String naam, Regio regio,
                                        String gemeente, String postcode, String straat, String slogan,
                                        String beschrijving, String ophaalInfo, String kleur) {
        Gebruiker eigenaar = gebruikerService.maakAan(voornaam, achternaam, email, "demo1234", Rol.ONDERNEMER);
        Onderneming o = ondernemingService.maakAan(eigenaar, naam, regio.getId(), gemeente, postcode, straat);
        OndernemingForm form = OndernemingForm.van(o);
        form.setSlogan(slogan);
        form.setBeschrijving(beschrijving);
        form.setOphaalInfo(ophaalInfo);
        form.setThemaKleur(kleur);
        ondernemingService.bijwerken(o.getId(), form);
        return ondernemingService.wijzigStatus(o.getId(), OndernemingStatus.ACTIEF);
    }

    private void demoProduct(Onderneming o, String naam, String categorie, String prijs, String eenheid,
                             int voorraad, String beschrijving) {
        ProductForm form = new ProductForm();
        form.setNaam(naam);
        form.setCategorie(categorie);
        form.setPrijs(new BigDecimal(prijs));
        form.setEenheid(eenheid);
        form.setVoorraad(voorraad);
        form.setBeschrijving(beschrijving);
        productService.opslaan(o, null, form);
    }
}
