package com.example.lokaleondernemers.dto;

import com.example.lokaleondernemers.model.Evenement;
import com.example.lokaleondernemers.model.Onderneming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class EvenementForm {

    /** Formaat van een HTML datetime-local veld. */
    public static final String DATUM_TIJD = "yyyy-MM-dd'T'HH:mm";

    @NotBlank(message = "Geef het evenement een titel.")
    @Size(max = 150)
    private String titel;

    @Size(max = 4000, message = "Maximaal 4000 tekens.")
    private String beschrijving;

    @NotNull(message = "Kies wanneer het evenement begint.")
    @DateTimeFormat(pattern = DATUM_TIJD)
    private LocalDateTime startMoment;

    @DateTimeFormat(pattern = DATUM_TIJD)
    private LocalDateTime eindMoment;

    @NotBlank(message = "Vul de locatie in.")
    @Size(max = 200)
    private String locatie;

    @NotBlank(message = "Vul de gemeente in.")
    @Size(max = 100)
    private String gemeente;

    @NotNull(message = "Kies een regio.")
    private Long regioId;

    @Size(max = 150)
    private String prijsInfo;

    /** Deelnemende ondernemingen (naast de organisator). */
    private List<Long> ondernemingIds = new ArrayList<>();

    private MultipartFile afbeelding;

    private boolean afbeeldingVerwijderen;

    public static EvenementForm van(Evenement e) {
        EvenementForm f = new EvenementForm();
        f.titel = e.getTitel();
        f.beschrijving = e.getBeschrijving();
        f.startMoment = e.getStartMoment();
        f.eindMoment = e.getEindMoment();
        f.locatie = e.getLocatie();
        f.gemeente = e.getGemeente();
        f.regioId = e.getRegio().getId();
        f.prijsInfo = e.getPrijsInfo();
        f.ondernemingIds = new ArrayList<>(e.getOndernemingen().stream().map(Onderneming::getId).toList());
        return f;
    }

    /** Een nieuw evenement van een onderneming start met haar eigen adres en regio. */
    public static EvenementForm voor(Onderneming o) {
        EvenementForm f = new EvenementForm();
        f.locatie = o.getPubliekAdres();
        f.gemeente = o.getGemeente();
        f.regioId = o.getRegio().getId();
        return f;
    }
}
