package com.example.lokaleondernemers.dto;

import com.example.lokaleondernemers.model.Gebruiker;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfielForm {

    @NotBlank(message = "Vul je voornaam in.")
    @Size(max = 100)
    private String voornaam;

    @NotBlank(message = "Vul je achternaam in.")
    @Size(max = 100)
    private String achternaam;

    @Size(max = 30)
    private String telefoon;

    /** Enkel nodig als het wachtwoord gewijzigd wordt. */
    private String huidigWachtwoord;

    @Size(max = 100)
    private String nieuwWachtwoord;

    public static ProfielForm van(Gebruiker g) {
        ProfielForm f = new ProfielForm();
        f.voornaam = g.getVoornaam();
        f.achternaam = g.getAchternaam();
        f.telefoon = g.getTelefoon();
        return f;
    }
}
