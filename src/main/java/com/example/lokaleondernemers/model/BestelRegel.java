package com.example.lokaleondernemers.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "bestel_regel")
@Getter
@Setter
@NoArgsConstructor
public class BestelRegel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Bestelling bestelling;

    @ManyToOne
    private Product product;

    /** Naam en prijs worden bewaard zoals ze waren op het moment van bestellen. */
    @Column(nullable = false)
    private String productNaam;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal prijs;

    private String eenheid;

    private int aantal;

    public BestelRegel(Product product, int aantal) {
        this.product = product;
        this.productNaam = product.getNaam();
        this.prijs = product.getPrijs();
        this.eenheid = product.getEenheid();
        this.aantal = aantal;
    }

    public BigDecimal getSubtotaal() {
        return prijs.multiply(BigDecimal.valueOf(aantal));
    }
}
