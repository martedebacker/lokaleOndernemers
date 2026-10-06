package com.example.lokaleondernemers.service;

/**
 * Een actie die niet toegelaten is volgens de regels van het platform (bv. onvoldoende voorraad).
 * De boodschap is bedoeld om rechtstreeks aan de gebruiker te tonen.
 */
public class BedrijfsregelException extends RuntimeException {

    public BedrijfsregelException(String message) {
        super(message);
    }
}
