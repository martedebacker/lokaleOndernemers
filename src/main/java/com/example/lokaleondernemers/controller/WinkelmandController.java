package com.example.lokaleondernemers.controller;

import com.example.lokaleondernemers.dto.AfrekenForm;
import com.example.lokaleondernemers.model.Bestelling;
import com.example.lokaleondernemers.service.BedrijfsregelException;
import com.example.lokaleondernemers.service.BestellingService;
import com.example.lokaleondernemers.service.Winkelmand;
import com.example.lokaleondernemers.service.WinkelmandService;
import com.example.lokaleondernemers.web.HuidigeGebruiker;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class WinkelmandController {

    private final Winkelmand winkelmand;
    private final WinkelmandService winkelmandService;
    private final BestellingService bestellingService;
    private final HuidigeGebruiker huidigeGebruiker;

    @GetMapping("/winkelmand")
    public String winkelmand(Model model) {
        List<WinkelmandService.Groep> groepen = winkelmandService.overzicht(winkelmand);
        model.addAttribute("groepen", groepen);
        model.addAttribute("totaal", WinkelmandService.totaal(groepen));
        return "winkelmand";
    }

    @PostMapping("/winkelmand/toevoegen")
    public String toevoegen(@RequestParam Long productId, @RequestParam(defaultValue = "1") int aantal,
                            RedirectAttributes redirect) {
        try {
            winkelmandService.toevoegen(winkelmand, productId, aantal);
            redirect.addFlashAttribute("succes", "Toegevoegd aan je winkelmand.");
        } catch (BedrijfsregelException e) {
            redirect.addFlashAttribute("fout", e.getMessage());
        }
        return "redirect:/product/" + productId;
    }

    @PostMapping("/winkelmand/wijzigen")
    public String wijzigen(@RequestParam Long productId, @RequestParam int aantal, RedirectAttributes redirect) {
        try {
            winkelmandService.wijzigAantal(winkelmand, productId, aantal);
        } catch (BedrijfsregelException e) {
            redirect.addFlashAttribute("fout", e.getMessage());
        }
        return "redirect:/winkelmand";
    }

    @PostMapping("/winkelmand/verwijderen")
    public String verwijderen(@RequestParam Long productId) {
        winkelmand.verwijder(productId);
        return "redirect:/winkelmand";
    }

    @GetMapping("/afrekenen")
    public String afrekenen(Model model) {
        if (winkelmand.isLeeg()) {
            return "redirect:/winkelmand";
        }
        AfrekenForm form = new AfrekenForm();
        form.setGewensteOphaaldatum(LocalDate.now().plusDays(1));
        model.addAttribute("form", form);
        return toonAfrekenen(model);
    }

    @PostMapping("/afrekenen")
    public String afrekenen(@Valid @ModelAttribute("form") AfrekenForm form, BindingResult result, Model model,
                            RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return toonAfrekenen(model);
        }
        try {
            List<Bestelling> bestellingen = bestellingService.plaats(huidigeGebruiker.get(), winkelmand, form);
            String ids = bestellingen.stream().map(b -> b.getId().toString()).collect(Collectors.joining(","));
            return "redirect:/mijn/bestellingen/bevestigd?ids=" + ids;
        } catch (BedrijfsregelException e) {
            redirect.addFlashAttribute("fout", e.getMessage());
            return "redirect:/winkelmand";
        }
    }

    private String toonAfrekenen(Model model) {
        List<WinkelmandService.Groep> groepen = winkelmandService.overzicht(winkelmand);
        model.addAttribute("groepen", groepen);
        model.addAttribute("totaal", WinkelmandService.totaal(groepen));
        return "afrekenen";
    }
}
