package com.example.lokaleondernemers.web;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Opmaakhulp voor de templates, te gebruiken als ${@fmt.euro(...)}. */
@Component("fmt")
public class Opmaak {

    private static final Locale NL_BE = Locale.of("nl", "BE");
    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", NL_BE);
    private static final DateTimeFormatter KORTE_DATUM = DateTimeFormatter.ofPattern("dd/MM/yyyy", NL_BE);
    private static final DateTimeFormatter DATUM_TIJD = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", NL_BE);
    private static final DateTimeFormatter TIJD = DateTimeFormatter.ofPattern("HH:mm", NL_BE);

    public String euro(BigDecimal bedrag) {
        if (bedrag == null) {
            return "";
        }
        NumberFormat formaat = NumberFormat.getCurrencyInstance(NL_BE);
        return formaat.format(bedrag);
    }

    public String datum(LocalDate datum) {
        return datum == null ? "" : DATUM.format(datum);
    }

    public String korteDatum(LocalDate datum) {
        return datum == null ? "" : KORTE_DATUM.format(datum);
    }

    public String datumTijd(LocalDateTime moment) {
        return moment == null ? "" : DATUM_TIJD.format(moment);
    }

    public String dag(LocalDateTime moment) {
        return moment == null ? "" : String.valueOf(moment.getDayOfMonth());
    }

    public String maandKort(LocalDateTime moment) {
        return moment == null ? "" : DateTimeFormatter.ofPattern("MMM", NL_BE).format(moment).replace(".", "");
    }

    /** Leesbare periode, bv. "zaterdag 12 oktober 2026, 10:00 – 17:00". */
    public String periode(LocalDateTime start, LocalDateTime eind) {
        if (start == null) {
            return "";
        }
        String begin = DATUM.format(start) + ", " + TIJD.format(start);
        if (eind == null) {
            return begin;
        }
        if (eind.toLocalDate().equals(start.toLocalDate())) {
            return begin + " – " + TIJD.format(eind);
        }
        return begin + " tot " + DATUM.format(eind) + ", " + TIJD.format(eind);
    }

    public String vandaag() {
        return LocalDate.now().toString();
    }
}
