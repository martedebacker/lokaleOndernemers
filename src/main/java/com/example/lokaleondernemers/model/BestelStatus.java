package com.example.lokaleondernemers.model;

import java.util.List;

public enum BestelStatus {
    GEPLAATST("Nieuw"),
    BEVESTIGD("Bevestigd"),
    KLAAR("Klaar om af te halen"),
    AFGEHAALD("Afgehaald"),
    GEANNULEERD("Geannuleerd");

    private final String label;

    BestelStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Statussen waarnaar de ondernemer een bestelling vanuit deze status kan verzetten. */
    public List<BestelStatus> getVolgendeStatussen() {
        return switch (this) {
            case GEPLAATST -> List.of(BEVESTIGD, GEANNULEERD);
            case BEVESTIGD -> List.of(KLAAR, GEANNULEERD);
            case KLAAR -> List.of(AFGEHAALD, GEANNULEERD);
            case AFGEHAALD, GEANNULEERD -> List.of();
        };
    }

    public boolean isOpen() {
        return this == GEPLAATST || this == BEVESTIGD || this == KLAAR;
    }
}
