package com.example.lokaleondernemers.dto;

import com.example.lokaleondernemers.model.Feedback;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FeedbackForm {

    @NotNull(message = "Kies waarover je feedback gaat.")
    private Feedback.Soort soort = Feedback.Soort.IDEE;

    @NotBlank(message = "Geef je feedback een korte titel.")
    @Size(max = 150)
    private String onderwerp;

    @NotBlank(message = "Vertel ons wat je wil delen.")
    @Size(max = 4000, message = "Maximaal 4000 tekens.")
    private String bericht;

    @Min(1)
    @Max(5)
    private Integer tevredenheid;
}
