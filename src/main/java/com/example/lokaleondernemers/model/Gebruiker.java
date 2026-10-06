package com.example.lokaleondernemers.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "gebruiker")
@Getter
@Setter
@NoArgsConstructor
public class Gebruiker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String voornaam;

    @Column(nullable = false)
    private String achternaam;

    /** Dient ook als gebruikersnaam om in te loggen. */
    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String wachtwoord;

    private String telefoon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol = Rol.KLANT;

    private boolean actief = true;

    private LocalDateTime aangemaaktOp = LocalDateTime.now();

    public Gebruiker(String voornaam, String achternaam, String email, String wachtwoord, Rol rol) {
        this.voornaam = voornaam;
        this.achternaam = achternaam;
        this.email = email;
        this.wachtwoord = wachtwoord;
        this.rol = rol;
    }

    public String getVolledigeNaam() {
        return voornaam + " " + achternaam;
    }
}
