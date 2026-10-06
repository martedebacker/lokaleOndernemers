package com.example.lokaleondernemers.model;

public enum OndernemingStatus {
    IN_AFWACHTING("Wacht op goedkeuring"),
    ACTIEF("Actief"),
    GEBLOKKEERD("Geblokkeerd");

    private final String label;

    OndernemingStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
