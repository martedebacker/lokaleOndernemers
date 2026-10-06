package com.example.lokaleondernemers.controller;

import com.example.lokaleondernemers.dto.ProfielForm;
import com.example.lokaleondernemers.model.Gebruiker;
import com.example.lokaleondernemers.service.BedrijfsregelException;
import com.example.lokaleondernemers.service.BestellingService;
import com.example.lokaleondernemers.service.GebruikerService;
import com.example.lokaleondernemers.web.HuidigeGebruiker;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/** Alles wat een ingelogde gebruiker over zichzelf kan bekijken: bestellingen en profiel. */
@Controller
@RequestMapping("/mijn")
@RequiredArgsConstructor
public class KlantController {

    private final BestellingService bestellingService;
    private final GebruikerService gebruikerService;
    private final HuidigeGebruiker huidigeGebruiker;

    @GetMapping("/bestellingen")
    public String bestellingen(Model model) {
        model.addAttribute("bestellingen", bestellingService.vanKlant(huidigeGebruiker.get()));
        return "klant/bestellingen";
    }

    @GetMapping("/bestellingen/bevestigd")
    public String bevestigd(@RequestParam List<Long> ids, Model model) {
        Gebruiker klant = huidigeGebruiker.get();
        model.addAttribute("bestellingen", ids.stream().map(id -> bestellingService.getVoorKlant(id, klant)).toList());
        return "klant/bevestigd";
    }

    @GetMapping("/bestellingen/{id}")
    public String bestelling(@PathVariable Long id, Model model) {
        model.addAttribute("bestelling", bestellingService.getVoorKlant(id, huidigeGebruiker.get()));
        return "klant/bestelling";
    }

    @PostMapping("/bestellingen/{id}/annuleren")
    public String annuleren(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            bestellingService.annuleerDoorKlant(id, huidigeGebruiker.get());
            redirect.addFlashAttribute("succes", "Je bestelling werd geannuleerd.");
        } catch (BedrijfsregelException e) {
            redirect.addFlashAttribute("fout", e.getMessage());
        }
        return "redirect:/mijn/bestellingen/" + id;
    }

    @GetMapping("/profiel")
    public String profiel(Model model) {
        model.addAttribute("form", ProfielForm.van(huidigeGebruiker.get()));
        return "klant/profiel";
    }

    @PostMapping("/profiel")
    public String profiel(@Valid @ModelAttribute("form") ProfielForm form, BindingResult result,
                          RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                gebruikerService.profielBijwerken(huidigeGebruiker.get().getId(), form);
                redirect.addFlashAttribute("succes", "Je gegevens zijn bijgewerkt.");
                return "redirect:/mijn/profiel";
            } catch (BedrijfsregelException e) {
                result.reject("profiel", e.getMessage());
            }
        }
        return "klant/profiel";
    }
}
