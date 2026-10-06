package com.example.lokaleondernemers.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Algemene voorwaarden van het platform, te gebruiken in templates als ${@platform...}. */
@Component("platform")
public class PlatformInfo {

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.of("nl", "BE"));

    private final LocalDate testfaseEinde;
    private final BigDecimal opstarthulpPrijs;

    public PlatformInfo(@Value("${app.testfase.einde}") LocalDate testfaseEinde,
                        @Value("${app.opstarthulp.prijs}") BigDecimal opstarthulpPrijs) {
        this.testfaseEinde = testfaseEinde;
        this.opstarthulpPrijs = opstarthulpPrijs;
    }

    public boolean isInTestfase() {
        return !LocalDate.now().isAfter(testfaseEinde.minusDays(1));
    }

    public LocalDate getTestfaseEinde() {
        return testfaseEinde;
    }

    /** Bv. "1 februari 2027". */
    public String getTestfaseEindeTekst() {
        return DATUM.format(testfaseEinde);
    }

    public BigDecimal getOpstarthulpPrijs() {
        return opstarthulpPrijs;
    }
}
