package com.example.lokaleondernemers.mail;

import com.example.lokaleondernemers.model.Afbeelding;
import com.example.lokaleondernemers.model.BestelRegel;
import com.example.lokaleondernemers.model.BestelStatus;
import com.example.lokaleondernemers.model.Bestelling;
import com.example.lokaleondernemers.model.Gebruiker;
import com.example.lokaleondernemers.model.Onderneming;
import com.example.lokaleondernemers.model.Product;
import com.example.lokaleondernemers.repository.BestellingRepository;
import com.example.lokaleondernemers.repository.ProductRepository;
import com.example.lokaleondernemers.web.Opmaak;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Stelt de e-mails over bestellingen op in de huisstijl van de ondernemer (kleuren en logo) en verstuurt ze.
 * Zonder ingestelde mailserver (spring.mail.host) worden ze als HTML-bestand bewaard in app.mail.voorbeeld-map.
 */
@Slf4j
@Service
public class MailService {

    private static final Locale NL_BE = Locale.of("nl", "BE");

    private final ObjectProvider<JavaMailSender> mailSender;
    private final TemplateEngine templateEngine;
    private final BestellingRepository bestellingRepository;
    private final ProductRepository productRepository;
    private final Opmaak opmaak;
    private final String basisUrl;
    private final String afzender;
    private final Path voorbeeldMap;

    public MailService(ObjectProvider<JavaMailSender> mailSender, TemplateEngine templateEngine,
                       BestellingRepository bestellingRepository, ProductRepository productRepository, Opmaak opmaak,
                       @Value("${app.basis-url}") String basisUrl,
                       @Value("${app.mail.afzender}") String afzender,
                       @Value("${app.mail.voorbeeld-map}") String voorbeeldMap) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.bestellingRepository = bestellingRepository;
        this.productRepository = productRepository;
        this.opmaak = opmaak;
        this.basisUrl = basisUrl.replaceAll("/+$", "");
        this.afzender = afzender;
        this.voorbeeldMap = Path.of(voorbeeldMap);
    }

    /** Wie de mail ontvangt bepaalt de inhoud (en de link in de knop). */
    public enum Ontvanger { KLANT, ONDERNEMER }

    /** De tekstuele inhoud van één e-mail. */
    public record Inhoud(String onderwerp, String titel, String intro, String knopTekst, String knopUrl) {
    }

    @Transactional(readOnly = true)
    public void verstuur(BestellingMailEvent event) {
        for (Long id : event.bestellingIds()) {
            Bestelling b = bestellingRepository.findById(id).orElse(null);
            if (b == null) {
                continue;
            }
            switch (event.soort()) {
                case GEPLAATST -> {
                    stuur(b, Ontvanger.KLANT, inhoudVoorKlant(b, true));
                    stuur(b, Ontvanger.ONDERNEMER, inhoudVoorOndernemer(b));
                }
                case BIJGEWERKT -> stuur(b, Ontvanger.KLANT, inhoudVoorKlant(b, event.statusGewijzigd()));
                case GEANNULEERD_DOOR_KLANT -> stuur(b, Ontvanger.ONDERNEMER, inhoudVoorOndernemer(b));
            }
        }
    }

    /** Hoe de klantenmail bij een bepaalde status eruitziet, met een voorbeeldbestelling (voor de ondernemer). */
    @Transactional(readOnly = true)
    public String voorbeeld(Onderneming onderneming, BestelStatus status) {
        Bestelling b = voorbeeldBestelling(onderneming, status);
        String logo = onderneming.getLogo() == null ? null : "/afbeelding/" + onderneming.getLogo().getId();
        return render(b, Ontvanger.KLANT, inhoudVoorKlant(b, true), logo);
    }

    // ---- inhoud ----

    Inhoud inhoudVoorKlant(Bestelling b, boolean statusGewijzigd) {
        String winkel = b.getOnderneming().getNaam();
        String nr = b.getNummer();
        String url = basisUrl + "/mijn/bestellingen/" + b.getId();
        if (!statusGewijzigd) {
            return new Inhoud("Nieuw bericht over je bestelling " + nr + " bij " + winkel,
                    "Nieuw bericht over je bestelling",
                    winkel + " heeft een bericht toegevoegd aan je bestelling. Je leest het hieronder.",
                    "Bekijk je bestelling", url);
        }
        return switch (b.getStatus()) {
            case GEPLAATST -> new Inhoud("Je bestelling " + nr + " bij " + winkel,
                    "Bedankt voor je bestelling!",
                    winkel + " heeft je bestelling goed ontvangen en bevestigt ze zo snel mogelijk. "
                            + "Er wordt niet geleverd: je haalt je bestelling persoonlijk op en betaalt bij het afhalen.",
                    "Bekijk je bestelling", url);
            case BEVESTIGD -> new Inhoud("Je bestelling " + nr + " is bevestigd",
                    "Je bestelling is bevestigd",
                    winkel + " heeft je bestelling bevestigd. Je krijgt opnieuw een bericht zodra ze klaarstaat.",
                    "Bekijk je bestelling", url);
            case KLAAR -> new Inhoud("Je bestelling " + nr + " staat klaar om af te halen",
                    "Je bestelling staat klaar!",
                    "Je kan je bestelling komen ophalen bij " + winkel + ". Je betaalt ter plaatse.",
                    "Bekijk ophaalgegevens", url);
            case AFGEHAALD -> new Inhoud("Bedankt voor je bezoek aan " + winkel,
                    "Bedankt om langs te komen!",
                    "Je bestelling werd afgehaald. " + winkel + " hoopt je snel terug te zien.",
                    "Ontdek meer van " + winkel, basisUrl + "/winkel/" + b.getOnderneming().getSlug());
            case GEANNULEERD -> new Inhoud("Je bestelling " + nr + " werd geannuleerd",
                    "Je bestelling werd geannuleerd",
                    "Je bestelling bij " + winkel + " werd geannuleerd. Heb je vragen? Neem gerust contact op met "
                            + winkel + ".",
                    "Bekijk je bestelling", url);
        };
    }

    Inhoud inhoudVoorOndernemer(Bestelling b) {
        String nr = b.getNummer();
        String url = basisUrl + "/beheer/bestellingen/" + b.getId();
        if (b.getStatus() == BestelStatus.GEANNULEERD) {
            return new Inhoud("Bestelling " + nr + " geannuleerd door de klant",
                    "Bestelling geannuleerd",
                    b.getKlant().getVolledigeNaam() + " heeft bestelling " + nr
                            + " geannuleerd. De voorraad werd automatisch teruggezet.",
                    "Bekijk de bestelling", url);
        }
        return new Inhoud("Nieuwe bestelling " + nr + " van " + b.getKlant().getVolledigeNaam(),
                "Je hebt een nieuwe bestelling",
                b.getKlant().getVolledigeNaam() + " wil deze bestelling komen ophalen. Bevestig ze en spreek "
                        + "eventueel een ophaalmoment af.",
                "Bestelling verwerken", url);
    }

    // ---- opmaken en versturen ----

    String render(Bestelling b, Ontvanger ontvanger, Inhoud inhoud, String logoSrc) {
        Context ctx = new Context(NL_BE);
        ctx.setVariable("b", b);
        ctx.setVariable("o", b.getOnderneming());
        ctx.setVariable("inhoud", inhoud);
        ctx.setVariable("voorOndernemer", ontvanger == Ontvanger.ONDERNEMER);
        ctx.setVariable("logoSrc", logoSrc);
        ctx.setVariable("fmt", opmaak);
        ctx.setVariable("basisUrl", basisUrl);
        return templateEngine.process("mail/bestelling", ctx);
    }

    private void stuur(Bestelling b, Ontvanger ontvanger, Inhoud inhoud) {
        Onderneming o = b.getOnderneming();
        String aan = ontvanger == Ontvanger.KLANT ? b.getKlant().getEmail() : contactAdres(o);
        // Antwoorden gaan rechtstreeks naar de andere partij: zo blijft het contact persoonlijk.
        String antwoordAan = ontvanger == Ontvanger.KLANT ? contactAdres(o) : b.getKlant().getEmail();
        Afbeelding logo = o.getLogo();

        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            String logoSrc = logo == null ? null : basisUrl + "/afbeelding/" + logo.getId();
            bewaarAlsBestand(b, ontvanger, aan, inhoud, render(b, ontvanger, inhoud, logoSrc));
            return;
        }
        try {
            MimeMessage bericht = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(bericht, true, StandardCharsets.UTF_8.name());
            InternetAddress van = new InternetAddress(afzender);
            helper.setFrom(van.getAddress(), o.getNaam() + " via Lokale Ondernemers");
            helper.setTo(aan);
            helper.setReplyTo(antwoordAan);
            helper.setSubject(inhoud.onderwerp());
            helper.setText(render(b, ontvanger, inhoud, logo == null ? null : "cid:logo"), true);
            if (logo != null) {
                helper.addInline("logo", new ByteArrayResource(logo.getData()), logo.getContentType());
            }
            sender.send(bericht);
            log.info("E-mail '{}' verstuurd naar {}", inhoud.onderwerp(), aan);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new IllegalStateException("E-mail kon niet opgesteld worden", e);
        }
    }

    private void bewaarAlsBestand(Bestelling b, Ontvanger ontvanger, String aan, Inhoud inhoud, String html) {
        try {
            Files.createDirectories(voorbeeldMap);
            String naam = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").format(LocalDateTime.now())
                    + "-" + b.getNummer() + "-" + ontvanger.name().toLowerCase(Locale.ROOT) + ".html";
            String kop = "<!-- Aan: " + aan + "\n     Onderwerp: " + inhoud.onderwerp() + " -->\n";
            Files.writeString(voorbeeldMap.resolve(naam), kop + html, StandardCharsets.UTF_8);
            log.info("Geen mailserver ingesteld: e-mail '{}' aan {} bewaard als {}", inhoud.onderwerp(), aan,
                    voorbeeldMap.resolve(naam).toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String contactAdres(Onderneming o) {
        return o.getEmail() != null ? o.getEmail() : o.getEigenaar().getEmail();
    }

    /** Een niet-opgeslagen bestelling om te tonen hoe een e-mail eruitziet. */
    private Bestelling voorbeeldBestelling(Onderneming o, BestelStatus status) {
        Bestelling b = new Bestelling();
        b.setId(12345L);
        b.setOnderneming(o);
        Gebruiker klant = new Gebruiker("Kim", "Voorbeeld", "kim@voorbeeld.be", "", null);
        b.setKlant(klant);
        b.setStatus(status);
        b.setGewensteOphaaldatum(LocalDate.now().plusDays(2));
        b.setOpmerkingKlant("Liefst na 17u, alvast bedankt!");
        if (status != BestelStatus.GEPLAATST) {
            b.setOphaalAfspraak("Je kan je bestelling zaterdag vanaf 10u ophalen.");
        }
        List<Product> producten = productRepository.findByOndernemingAndVerwijderdFalseOrderByNaamAsc(o);
        if (producten.isEmpty()) {
            Product p = new Product();
            p.setNaam("Voorbeeldproduct");
            p.setPrijs(new BigDecimal("4.50"));
            p.setEenheid("per stuk");
            producten = List.of(p);
        }
        producten.stream().limit(3).forEach(p -> {
            BestelRegel r = new BestelRegel(p, 2);
            r.setProduct(null); // niet koppelen aan echte entiteiten
            b.getRegels().add(r);
        });
        return b;
    }
}
