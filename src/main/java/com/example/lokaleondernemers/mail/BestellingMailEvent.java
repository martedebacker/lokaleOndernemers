package com.example.lokaleondernemers.mail;

import java.util.List;

/**
 * Er gebeurde iets met één of meer bestellingen waarover een e-mail verstuurd moet worden.
 *
 * @param statusGewijzigd bij BIJGEWERKT: veranderde de status (anders enkel het bericht van de ondernemer)
 */
public record BestellingMailEvent(List<Long> bestellingIds, Soort soort, boolean statusGewijzigd) {

    public enum Soort {
        /** Nieuwe bestelling: bevestiging naar de klant + melding naar de ondernemer. */
        GEPLAATST,
        /** De ondernemer wijzigde de status of het ophaalbericht: mail naar de klant. */
        BIJGEWERKT,
        /** De klant annuleerde: melding naar de ondernemer. */
        GEANNULEERD_DOOR_KLANT
    }

    public static BestellingMailEvent geplaatst(List<Long> ids) {
        return new BestellingMailEvent(ids, Soort.GEPLAATST, true);
    }

    public static BestellingMailEvent bijgewerkt(Long id, boolean statusGewijzigd) {
        return new BestellingMailEvent(List.of(id), Soort.BIJGEWERKT, statusGewijzigd);
    }

    public static BestellingMailEvent geannuleerdDoorKlant(Long id) {
        return new BestellingMailEvent(List.of(id), Soort.GEANNULEERD_DOOR_KLANT, true);
    }
}
