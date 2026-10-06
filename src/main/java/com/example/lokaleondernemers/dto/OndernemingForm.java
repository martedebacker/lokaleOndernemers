package com.example.lokaleondernemers.dto;

import com.example.lokaleondernemers.model.Onderneming;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class OndernemingForm {

    @NotBlank(message = "Vul de naam van je onderneming in.")
    @Size(max = 120)
    private String naam;

    @Size(max = 200)
    private String slogan;

    @Size(max = 4000, message = "Maximaal 4000 tekens.")
    private String beschrijving;

    @Size(max = 200)
    private String straat;

    @Size(max = 10)
    private String postcode;

    @NotBlank(message = "Vul de gemeente in.")
    @Size(max = 100)
    private String gemeente;

    @NotNull(message = "Kies een regio.")
    private Long regioId;

    @Size(max = 30)
    private String telefoon;

    @Email(message = "Dit is geen geldig e-mailadres.")
    @Size(max = 200)
    private String email;

    @Size(max = 2000, message = "Maximaal 2000 tekens.")
    private String ophaalInfo;

    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Kies een geldige kleur.")
    private String themaKleur = Onderneming.STANDAARD_HOOFDKLEUR;

    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Kies een geldige kleur.")
    private String accentKleur = Onderneming.STANDAARD_ACCENTKLEUR;

    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Kies een geldige kleur.")
    private String achtergrondKleur = Onderneming.STANDAARD_ACHTERGRONDKLEUR;

    private boolean toonAdres = true;
    private boolean toonTelefoon = true;
    private boolean toonEmail = true;
    private boolean toonOphaalInfo = true;
    private boolean toonVoorraad = true;
    private boolean toonEvenementen = true;

    private MultipartFile logo;

    private MultipartFile banner;

    private boolean logoVerwijderen;

    private boolean bannerVerwijderen;

    public static OndernemingForm van(Onderneming o) {
        OndernemingForm f = new OndernemingForm();
        f.naam = o.getNaam();
        f.slogan = o.getSlogan();
        f.beschrijving = o.getBeschrijving();
        f.straat = o.getStraat();
        f.postcode = o.getPostcode();
        f.gemeente = o.getGemeente();
        f.regioId = o.getRegio().getId();
        f.telefoon = o.getTelefoon();
        f.email = o.getEmail();
        f.ophaalInfo = o.getOphaalInfo();
        f.themaKleur = o.getThemaKleur();
        f.accentKleur = o.getAccentKleur();
        f.achtergrondKleur = o.getAchtergrondKleur();
        f.toonAdres = o.isToonAdres();
        f.toonTelefoon = o.isToonTelefoon();
        f.toonEmail = o.isToonEmail();
        f.toonOphaalInfo = o.isToonOphaalInfo();
        f.toonVoorraad = o.isToonVoorraad();
        f.toonEvenementen = o.isToonEvenementen();
        return f;
    }
}
