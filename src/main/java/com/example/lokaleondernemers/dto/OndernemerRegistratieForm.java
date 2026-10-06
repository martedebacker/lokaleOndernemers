package com.example.lokaleondernemers.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OndernemerRegistratieForm extends RegistratieForm {

    @NotBlank(message = "Vul de naam van je onderneming in.")
    @Size(max = 120)
    private String ondernemingNaam;

    @NotNull(message = "Kies een regio.")
    private Long regioId;

    @Size(max = 200)
    private String straat;

    @Size(max = 10)
    private String postcode;

    @NotBlank(message = "Vul de gemeente in.")
    @Size(max = 100)
    private String gemeente;

    /** true = wij richten de winkelpagina in (betalend), false = de ondernemer doet het zelf. */
    private boolean opstarthulp;

    @Size(max = 2000, message = "Maximaal 2000 tekens.")
    private String opstartWensen;

    @AssertTrue(message = "Bevestig dat je de voorwaarden van de testfase gelezen hebt.")
    private boolean akkoordTestfase;
}
