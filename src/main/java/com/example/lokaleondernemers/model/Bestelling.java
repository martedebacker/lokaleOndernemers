package com.example.lokaleondernemers.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Een bestelling bij één onderneming. Er wordt niet geleverd: de klant haalt de bestelling
 * persoonlijk op bij de ondernemer. De betaling gebeurt buiten het platform.
 */
@Entity
@Table(name = "bestelling")
@Getter
@Setter
@NoArgsConstructor
public class Bestelling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Gebruiker klant;

    @ManyToOne(optional = false)
    private Onderneming onderneming;

    @OneToMany(mappedBy = "bestelling", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<BestelRegel> regels = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BestelStatus status = BestelStatus.GEPLAATST;

    private LocalDate gewensteOphaaldatum;

    @Column(length = 1000)
    private String opmerkingKlant;

    /** Afgesproken ophaalmoment / boodschap van de ondernemer aan de klant. */
    @Column(length = 1000)
    private String ophaalAfspraak;

    private LocalDateTime geplaatstOp = LocalDateTime.now();

    private LocalDateTime bijgewerktOp = LocalDateTime.now();

    public void voegRegelToe(BestelRegel regel) {
        regel.setBestelling(this);
        regels.add(regel);
    }

    public BigDecimal getTotaal() {
        return regels.stream().map(BestelRegel::getSubtotaal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int getAantalStuks() {
        return regels.stream().mapToInt(BestelRegel::getAantal).sum();
    }

    public String getNummer() {
        return String.format("LO-%05d", id);
    }
}
