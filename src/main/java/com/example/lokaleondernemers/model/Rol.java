package com.example.lokaleondernemers.model;

public enum Rol {
    KLANT("Klant"),
    ONDERNEMER("Ondernemer"),
    ADMIN("Beheerder");

    private final String label;

    Rol(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
