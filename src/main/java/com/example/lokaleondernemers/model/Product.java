package com.example.lokaleondernemers.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String naam;

    @Column(length = 4000)
    private String beschrijving;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal prijs;

    /** Bv. "per stuk", "per kg", "per pot". */
    private String eenheid;

    private String categorie;

    @Column(nullable = false)
    private int voorraad;

    /** Ondernemer kan een product tijdelijk verbergen zonder het te verwijderen. */
    private boolean zichtbaar = true;

    /** Producten die al besteld werden, worden niet echt verwijderd maar gearchiveerd. */
    private boolean verwijderd = false;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Afbeelding afbeelding;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Onderneming onderneming;

    private LocalDateTime aangemaaktOp = LocalDateTime.now();

    /** Optimistische locking: voorkomt dat twee gelijktijdige bestellingen dezelfde voorraad verkopen. */
    @Version
    private long versie;

    public boolean isOpVoorraad() {
        return voorraad > 0;
    }

    /** Zichtbaar en bestelbaar voor klanten. */
    public boolean isTeKoop() {
        return zichtbaar && !verwijderd && onderneming.isActief();
    }

    public String getInitiaal() {
        return naam == null || naam.isBlank() ? "?" : naam.substring(0, 1).toUpperCase();
    }
}
