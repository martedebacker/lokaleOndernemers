package com.example.lokaleondernemers.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Feedback van een ondernemer over het platform, met een antwoord van de beheerders. */
@Entity
@Table(name = "feedback")
@Getter
@Setter
@NoArgsConstructor
public class Feedback {

    public enum Soort {
        IDEE("Idee of suggestie"),
        PROBLEEM("Probleem of fout"),
        VRAAG("Vraag"),
        COMPLIMENT("Compliment");

        private final String label;

        Soort(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum Status {
        NIEUW("Nieuw"),
        IN_BEHANDELING("In behandeling"),
        AFGEHANDELD("Afgehandeld");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Onderneming onderneming;

    @ManyToOne(optional = false)
    private Gebruiker afzender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Soort soort;

    @Column(nullable = false)
    private String onderwerp;

    @Column(nullable = false, length = 4000)
    private String bericht;

    /** Hoe tevreden over het platform, 1 tot 5 (optioneel). */
    private Integer tevredenheid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.NIEUW;

    @Column(length = 4000)
    private String antwoord;

    private LocalDateTime beantwoordOp;

    private LocalDateTime aangemaaktOp = LocalDateTime.now();
}
