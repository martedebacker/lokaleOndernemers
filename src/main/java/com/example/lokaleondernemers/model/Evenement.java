package com.example.lokaleondernemers.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Een evenement in een regio (markt, workshop, opendeurdag…). Het wordt georganiseerd door het
 * platform zelf (organisator == null) of door een onderneming, eventueel samen met andere ondernemingen.
 */
@Entity
@Table(name = "evenement")
@Getter
@Setter
@NoArgsConstructor
public class Evenement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titel;

    @Column(length = 4000)
    private String beschrijving;

    @Column(nullable = false)
    private LocalDateTime startMoment;

    private LocalDateTime eindMoment;

    /** Adres of plaatsomschrijving, bv. "Grote Markt" of "Bij Hoeve De Linde". */
    @Column(nullable = false)
    private String locatie;

    @Column(nullable = false)
    private String gemeente;

    @ManyToOne(optional = false)
    private Regio regio;

    /** Bv. "Gratis", "€ 5 per persoon", "Inschrijven via telefoon". */
    private String prijsInfo;

    /** De onderneming die het evenement aanmaakte en beheert; null = georganiseerd door het platform. */
    @ManyToOne
    private Onderneming organisator;

    /** Andere ondernemingen die meedoen of mee organiseren. */
    @ManyToMany
    @JoinTable(name = "evenement_onderneming")
    @OrderBy("naam")
    private Set<Onderneming> ondernemingen = new LinkedHashSet<>();

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Afbeelding afbeelding;

    private LocalDateTime aangemaaktOp = LocalDateTime.now();

    public boolean isDoorPlatform() {
        return organisator == null;
    }

    public boolean isAfgelopen() {
        LocalDateTime einde = eindMoment != null ? eindMoment : startMoment;
        return einde.isBefore(LocalDateTime.now());
    }

    /** Alle (actieve) ondernemingen die aan het evenement meewerken, organisator eerst. */
    public List<Onderneming> getBetrokkenOndernemingen() {
        Set<Onderneming> alle = new LinkedHashSet<>();
        if (organisator != null) {
            alle.add(organisator);
        }
        alle.addAll(ondernemingen);
        return alle.stream().filter(Onderneming::isActief).toList();
    }

    public boolean isBetrokken(Onderneming onderneming) {
        return (organisator != null && organisator.getId().equals(onderneming.getId()))
                || ondernemingen.stream().anyMatch(o -> o.getId().equals(onderneming.getId()));
    }

    public String getInitiaal() {
        return titel == null || titel.isBlank() ? "?" : titel.substring(0, 1).toUpperCase();
    }
}
