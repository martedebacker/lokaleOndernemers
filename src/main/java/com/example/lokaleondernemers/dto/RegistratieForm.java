package com.example.lokaleondernemers.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistratieForm {

    @NotBlank(message = "Vul je voornaam in.")
    @Size(max = 100)
    private String voornaam;

    @NotBlank(message = "Vul je achternaam in.")
    @Size(max = 100)
    private String achternaam;

    @NotBlank(message = "Vul je e-mailadres in.")
    @Email(message = "Dit is geen geldig e-mailadres.")
    @Size(max = 200)
    private String email;

    @Size(max = 30)
    private String telefoon;

    @NotBlank(message = "Kies een wachtwoord.")
    @Size(min = 8, max = 100, message = "Je wachtwoord moet minstens 8 tekens lang zijn.")
    private String wachtwoord;

    @NotBlank(message = "Herhaal je wachtwoord.")
    private String wachtwoordHerhaling;
}
