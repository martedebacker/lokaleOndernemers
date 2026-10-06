package com.example.lokaleondernemers.controller;

import com.example.lokaleondernemers.dto.OndernemerRegistratieForm;
import com.example.lokaleondernemers.dto.RegistratieForm;
import com.example.lokaleondernemers.model.Gebruiker;
import com.example.lokaleondernemers.service.BedrijfsregelException;
import com.example.lokaleondernemers.service.GebruikerService;
import com.example.lokaleondernemers.service.RegioService;
import com.example.lokaleondernemers.web.HuidigeGebruiker;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AccountController {

    private final GebruikerService gebruikerService;
    private final RegioService regioService;
    private final HuidigeGebruiker huidigeGebruiker;

    @GetMapping("/login")
    public String login() {
        return "account/login";
    }

    /** Startpunt na inloggen, afhankelijk van de rol. */
    @GetMapping("/na-login")
    public String naLogin() {
        Gebruiker g = huidigeGebruiker.get();
        return switch (g.getRol()) {
            case ADMIN -> "redirect:/admin";
            case ONDERNEMER -> "redirect:/beheer";
            case KLANT -> "redirect:/";
        };
    }

    @GetMapping("/registreren")
    public String registreren(Model model) {
        model.addAttribute("form", new RegistratieForm());
        return "account/registreren";
    }

    @PostMapping("/registreren")
    public String registreren(@Valid @ModelAttribute("form") RegistratieForm form, BindingResult result,
                              RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                gebruikerService.registreerKlant(form);
                redirect.addFlashAttribute("succes", "Je account is aangemaakt. Log in om verder te gaan.");
                return "redirect:/login";
            } catch (BedrijfsregelException e) {
                result.reject("registratie", e.getMessage());
            }
        }
        return "account/registreren";
    }

    @GetMapping("/registreren/ondernemer")
    public String registrerenOndernemer(Model model) {
        model.addAttribute("form", new OndernemerRegistratieForm());
        model.addAttribute("regios", regioService.alle());
        return "account/registreren-ondernemer";
    }

    @PostMapping("/registreren/ondernemer")
    public String registrerenOndernemer(@Valid @ModelAttribute("form") OndernemerRegistratieForm form,
                                        BindingResult result, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                gebruikerService.registreerOndernemer(form);
                redirect.addFlashAttribute("succes", "Welkom! Je onderneming is aangemaakt en wacht op goedkeuring "
                        + "door een beheerder. Log in om je winkel al in te richten en producten toe te voegen.");
                return "redirect:/login";
            } catch (BedrijfsregelException e) {
                result.reject("registratie", e.getMessage());
            }
        }
        model.addAttribute("regios", regioService.alle());
        return "account/registreren-ondernemer";
    }
}
