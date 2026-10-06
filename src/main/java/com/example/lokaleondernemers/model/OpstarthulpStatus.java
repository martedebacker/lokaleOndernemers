package com.example.lokaleondernemers.model;

/** Status van de betalende opstarthulp: het platform richt de winkelpagina in voor de ondernemer. */
public enum OpstarthulpStatus {
    AANGEVRAAGD("Aangevraagd"),
    IN_UITVOERING("Wordt ingericht"),
    AFGEROND("Afgerond");

    private final String label;

    OpstarthulpStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
