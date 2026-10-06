package com.example.lokaleondernemers.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
public class AfrekenForm {

    @NotNull(message = "Kies wanneer je je bestelling wil ophalen.")
    @FutureOrPresent(message = "Kies een datum vanaf vandaag.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate gewensteOphaaldatum;

    @Size(max = 1000, message = "Maximaal 1000 tekens.")
    private String opmerking;
}
