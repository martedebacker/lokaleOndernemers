package com.example.lokaleondernemers.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

@Entity
@Table(name = "onderneming")
@Getter
@Setter
@NoArgsConstructor
public class Onderneming {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String naam;

    /** Leesbare unieke naam in de url van de winkelpagina, bv. /winkel/bakkerij-janssens. */
    @Column(nullable = false, unique = true)
    private String slug;

    private String slogan;

    @Column(length = 4000)
    private String beschrijving;

    private String straat;

    private String postcode;

    @Column(nullable = false)
    private String gemeente;

    @ManyToOne(optional = false)
    private Regio regio;

    private String telefoon;

    private String email;

    /** Openingsuren / afhaalmomenten, vrije tekst. */
    @Column(length = 2000)
    private String ophaalInfo;

    // ---- kleurenpalet van de winkelpagina (hex, bv. #2f6f4f) ----

    public static final String STANDAARD_HOOFDKLEUR = "#2f6f4f";
    public static final String STANDAARD_ACCENTKLEUR = "#c0643b";
    public static final String STANDAARD_ACHTERGRONDKLEUR = "#faf7f2";

    /** Hoofdkleur: knoppen, prijzen, banner zonder foto. */
    private String themaKleur = STANDAARD_HOOFDKLEUR;

    /** Accentkleur: kopjes, labels en details. */
    private String accentKleur;

    /** Achtergrondkleur van de winkelpagina. */
    private String achtergrondKleur;

    // ---- wat klanten op de winkelpagina zien ----

    /** Volledig adres publiek tonen. Uit = enkel de gemeente; klanten zien het adres pas na het bestellen. */
    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean toonAdres = true;

    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean toonTelefoon = true;

    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean toonEmail = true;

    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean toonOphaalInfo = true;

    /** "Nog maar 3 beschikbaar" tonen bij producten met weinig voorraad. */
    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean toonVoorraad = true;

    @Column(nullable = false)
    @ColumnDefault("true")
    private boolean toonEvenementen = true;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Afbeelding logo;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Afbeelding banner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OndernemingStatus status = OndernemingStatus.IN_AFWACHTING;

    // ---- opstarthulp: wij richten de winkelpagina in (betalend) ----

    /** null = de ondernemer richt de winkel zelf in. */
    @Enumerated(EnumType.STRING)
    private OpstarthulpStatus opstarthulp;

    /** Wat de ondernemer graag op de pagina ziet: stijl, producten, teksten… */
    @Column(length = 2000)
    private String opstartWensen;

    private LocalDateTime opstarthulpAangevraagdOp;

    @OneToOne(optional = false)
    @JoinColumn(unique = true)
    private Gebruiker eigenaar;

    private LocalDateTime aangemaaktOp = LocalDateTime.now();

    public boolean isActief() {
        return status == OndernemingStatus.ACTIEF;
    }

    public String getVolledigAdres() {
        StringBuilder sb = new StringBuilder();
        if (straat != null && !straat.isBlank()) {
            sb.append(straat).append(", ");
        }
        if (postcode != null && !postcode.isBlank()) {
            sb.append(postcode).append(" ");
        }
        return sb.append(gemeente).toString();
    }

    public String getAccentKleur() {
        return accentKleur == null ? STANDAARD_ACCENTKLEUR : accentKleur;
    }

    public String getAchtergrondKleur() {
        return achtergrondKleur == null ? STANDAARD_ACHTERGRONDKLEUR : achtergrondKleur;
    }

    /** Heeft de ondernemer al een eigen kleurenpalet gekozen? */
    public boolean isEigenKleuren() {
        return !STANDAARD_HOOFDKLEUR.equalsIgnoreCase(themaKleur) || accentKleur != null || achtergrondKleur != null;
    }

    /** De CSS-variabelen voor de winkelpagina. Kleuren zijn bij het opslaan gevalideerd als #rrggbb. */
    public String getKleurStijl() {
        return "--thema:" + themaKleur + ";--winkel-accent:" + getAccentKleur()
                + ";--winkel-achtergrond:" + getAchtergrondKleur();
    }

    /** Wat publiek als locatie getoond wordt, afhankelijk van de privacykeuze van de ondernemer. */
    public String getPubliekAdres() {
        return toonAdres ? getVolledigAdres() : gemeente;
    }

    public String getInitiaal() {
        return naam == null || naam.isBlank() ? "?" : naam.substring(0, 1).toUpperCase();
    }
}
