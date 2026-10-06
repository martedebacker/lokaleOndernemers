package com.example.lokaleondernemers.web;

import com.example.lokaleondernemers.model.Gebruiker;
import com.example.lokaleondernemers.service.Winkelmand;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;
import java.net.URISyntaxException;

/** Gegevens die elke pagina nodig heeft (navigatiebalk) en algemene foutafhandeling. */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobaleModelAttributen {

    private final HuidigeGebruiker huidigeGebruiker;
    private final Winkelmand winkelmand;

    @ModelAttribute("ingelogd")
    public Gebruiker ingelogd() {
        return huidigeGebruiker.zoek().orElse(null);
    }

    @ModelAttribute("winkelmandAantal")
    public int winkelmandAantal() {
        return winkelmand.getAantalStuks();
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String teGroot(HttpServletRequest request, RedirectAttributes redirect) {
        redirect.addFlashAttribute("fout", "Het bestand is te groot. Een afbeelding mag maximaal 5 MB zijn.");
        return "redirect:" + terug(request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public String gelijktijdigGewijzigd(HttpServletRequest request, RedirectAttributes redirect) {
        redirect.addFlashAttribute("fout", "Iemand anders wijzigde dit net tegelijk met jou. Probeer het opnieuw.");
        return "redirect:" + terug(request);
    }

    private static String terug(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        // Enkel naar een pad op onze eigen site terugsturen.
        try {
            URI uri = referer == null ? null : new URI(referer);
            if (uri != null && request.getServerName().equalsIgnoreCase(uri.getHost()) && uri.getRawPath() != null) {
                return uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            }
        } catch (URISyntaxException e) {
            // ongeldige referer: val terug op de startpagina
        }
        return "/";
    }
}
