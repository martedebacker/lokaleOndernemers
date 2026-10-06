package com.example.lokaleondernemers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegioForm {

    @NotBlank(message = "Geef de regio een naam.")
    @Size(max = 100)
    private String naam;

    @Size(max = 1000)
    private String beschrijving;
}
