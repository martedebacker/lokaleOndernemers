package com.example.lokaleondernemers.controller;

import com.example.lokaleondernemers.dto.EvenementForm;
import com.example.lokaleondernemers.dto.RegioForm;
import com.example.lokaleondernemers.model.BestelStatus;
import com.example.lokaleondernemers.model.Feedback;
import com.example.lokaleondernemers.model.OpstarthulpStatus;
import com.example.lokaleondernemers.model.Onderneming;
import com.example.lokaleondernemers.model.OndernemingStatus;
import com.example.lokaleondernemers.model.Rol;
import com.example.lokaleondernemers.service.BedrijfsregelException;
import com.example.lokaleondernemers.service.BestellingService;
import com.example.lokaleondernemers.service.EvenementService;
import com.example.lokaleondernemers.service.FeedbackService;
import com.example.lokaleondernemers.service.GebruikerService;
import com.example.lokaleondernemers.service.OndernemingService;
import com.example.lokaleondernemers.service.RegioService;
import com.example.lokaleondernemers.web.HuidigeGebruiker;
import jakarta.servlet.http.HttpSession;
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

/** Platformbeheer: ondernemingen goedkeuren, regio's, gebruikers en een overzicht van alle bestellingen. */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final OndernemingService ondernemingService;
    private final RegioService regioService;
    private final GebruikerService gebruikerService;
    private final BestellingService bestellingService;
    private final EvenementService evenementService;
    private final FeedbackService feedbackService;
    private final HuidigeGebruiker huidigeGebruiker;

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("aantalInAfwachting", ondernemingService.aantalMetStatus(OndernemingStatus.IN_AFWACHTING));
        model.addAttribute("aantalActief", ondernemingService.aantalMetStatus(OndernemingStatus.ACTIEF));
        model.addAttribute("aantalKlanten", gebruikerService.aantalMetRol(Rol.KLANT));
        model.addAttribute("aantalBestellingen", bestellingService.aantal());
        model.addAttribute("aantalOpen", bestellingService.aantalMetStatus(BestelStatus.GEPLAATST)
                + bestellingService.aantalMetStatus(BestelStatus.BEVESTIGD)
                + bestellingService.aantalMetStatus(BestelStatus.KLAAR));
        model.addAttribute("teKeuren", ondernemingService.alle().stream()
                .filter(o -> o.getStatus() == OndernemingStatus.IN_AFWACHTING).toList());
        model.addAttribute("opstarthulpOpen", ondernemingService.alle().stream()
                .filter(o -> o.getOpstarthulp() != null && o.getOpstarthulp() != OpstarthulpStatus.AFGEROND).toList());
        model.addAttribute("aantalNieuweFeedback", feedbackService.aantalNieuw());
        return "admin/dashboard";
    }

    // ---- ondernemingen ----

    @GetMapping("/ondernemingen")
    public String ondernemingen(Model model) {
        model.addAttribute("ondernemingen", ondernemingService.alle());
        model.addAttribute("statussen", OndernemingStatus.values());
        model.addAttribute("opstarthulpStatussen", OpstarthulpStatus.values());
        return "admin/ondernemingen";
    }

    @PostMapping("/ondernemingen/{id}/status")
    public String ondernemingStatus(@PathVariable Long id, @RequestParam OndernemingStatus status,
                                    @RequestParam(defaultValue = "/admin/ondernemingen") String terug,
                                    RedirectAttributes redirect) {
        Onderneming o = ondernemingService.wijzigStatus(id, status);
        redirect.addFlashAttribute("succes", "'" + o.getNaam() + "' heeft nu de status '" + status.getLabel() + "'.");
        return "redirect:" + (terug.startsWith("/admin") ? terug : "/admin/ondernemingen");
    }

    @PostMapping("/ondernemingen/{id}/opstarthulp")
    public String opstarthulp(@PathVariable Long id, @RequestParam OpstarthulpStatus status,
                              @RequestParam(defaultValue = "/admin/ondernemingen") String terug,
                              RedirectAttributes redirect) {
        try {
            var o = ondernemingService.wijzigOpstarthulp(id, status);
            redirect.addFlashAttribute("succes", "Opstarthulp voor '" + o.getNaam() + "': " + status.getLabel() + ".");
        } catch (BedrijfsregelException e) {
            redirect.addFlashAttribute("fout", e.getMessage());
        }
        return "redirect:" + (terug.startsWith("/admin") ? terug : "/admin/ondernemingen");
    }

    /** De winkel van een ondernemer inrichten alsof je die ondernemer bent (opstarthulp). */
    @PostMapping("/ondernemingen/{id}/beheren")
    public String beheren(@PathVariable Long id, HttpSession sessie) {
        ondernemingService.get(id);
        sessie.setAttribute(OndernemerController.BEHEER_ALS, id);
        return "redirect:/beheer";
    }

    @PostMapping("/beheren/stoppen")
    public String stopBeheren(HttpSession sessie) {
        sessie.removeAttribute(OndernemerController.BEHEER_ALS);
        return "redirect:/admin/ondernemingen";
    }

    // ---- feedback van ondernemers ----

    @GetMapping("/feedback")
    public String feedback(@RequestParam(defaultValue = "false") boolean alle, Model model) {
        model.addAttribute("alle", alle);
        model.addAttribute("feedbackLijst", feedbackService.alle(!alle));
        model.addAttribute("feedbackStatussen", Feedback.Status.values());
        return "admin/feedback";
    }

    @PostMapping("/feedback/{id}")
    public String feedbackBehandelen(@PathVariable Long id, @RequestParam(required = false) Feedback.Status status,
                                     @RequestParam(required = false) String antwoord, RedirectAttributes redirect) {
        feedbackService.behandel(id, status, antwoord);
        redirect.addFlashAttribute("succes", "Feedback bijgewerkt.");
        return "redirect:/admin/feedback";
    }

    // ---- regio's ----

    @GetMapping("/regios")
    public String regios(Model model) {
        model.addAttribute("regios", regioService.overzicht());
        model.addAttribute("form", new RegioForm());
        return "admin/regios";
    }

    @PostMapping("/regios")
    public String nieuweRegio(@Valid @ModelAttribute("form") RegioForm form, BindingResult result, Model model,
                              RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                regioService.maakAan(form);
                redirect.addFlashAttribute("succes", "Regio '" + form.getNaam() + "' toegevoegd.");
                return "redirect:/admin/regios";
            } catch (BedrijfsregelException e) {
                result.reject("regio", e.getMessage());
            }
        }
        model.addAttribute("regios", regioService.overzicht());
        return "admin/regios";
    }

    @PostMapping("/regios/{id}")
    public String regioBijwerken(@PathVariable Long id, @Valid @ModelAttribute("form") RegioForm form,
                                 BindingResult result, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            redirect.addFlashAttribute("fout", "Een regio moet een naam hebben.");
            return "redirect:/admin/regios";
        }
        try {
            regioService.bijwerken(id, form);
            redirect.addFlashAttribute("succes", "Regio bijgewerkt.");
        } catch (BedrijfsregelException e) {
            redirect.addFlashAttribute("fout", e.getMessage());
        }
        return "redirect:/admin/regios";
    }

    @PostMapping("/regios/{id}/verwijderen")
    public String regioVerwijderen(@PathVariable Long id, @RequestParam(required = false) Long verplaatsNaar,
                                   RedirectAttributes redirect) {
        try {
            regioService.verwijderen(id, verplaatsNaar);
            redirect.addFlashAttribute("succes", "Regio verwijderd.");
        } catch (BedrijfsregelException e) {
            redirect.addFlashAttribute("fout", e.getMessage());
        }
        return "redirect:/admin/regios";
    }

    // ---- gebruikers ----

    @GetMapping("/gebruikers")
    public String gebruikers(Model model) {
        model.addAttribute("gebruikers", gebruikerService.alle());
        return "admin/gebruikers";
    }

    @PostMapping("/gebruikers/{id}/actief")
    public String gebruikerActief(@PathVariable Long id, @RequestParam boolean actief, RedirectAttributes redirect) {
        try {
            gebruikerService.zetActief(id, actief, huidigeGebruiker.get());
            redirect.addFlashAttribute("succes", actief ? "Account geactiveerd." : "Account geblokkeerd.");
        } catch (BedrijfsregelException e) {
            redirect.addFlashAttribute("fout", e.getMessage());
        }
        return "redirect:/admin/gebruikers";
    }

    @PostMapping("/gebruikers/{id}/rol")
    public String gebruikerRol(@PathVariable Long id, @RequestParam Rol rol, RedirectAttributes redirect) {
        try {
            gebruikerService.wijzigRol(id, rol, huidigeGebruiker.get());
            redirect.addFlashAttribute("succes", "Rol gewijzigd naar " + rol.getLabel() + ".");
        } catch (BedrijfsregelException e) {
            redirect.addFlashAttribute("fout", e.getMessage());
        }
        return "redirect:/admin/gebruikers";
    }

    // ---- bestellingen ----

    @GetMapping("/bestellingen")
    public String bestellingen(Model model) {
        model.addAttribute("bestellingen", bestellingService.alle());
        return "admin/bestellingen";
    }

    // ---- evenementen ----

    @GetMapping("/evenementen")
    public String evenementen(Model model) {
        model.addAttribute("evenementen", evenementService.alle());
        return "admin/evenementen";
    }

    @GetMapping("/evenementen/nieuw")
    public String nieuwEvenement(Model model) {
        model.addAttribute("form", new EvenementForm());
        return toonEvenementForm(model);
    }

    @PostMapping("/evenementen/nieuw")
    public String nieuwEvenement(@Valid @ModelAttribute("form") EvenementForm form, BindingResult result,
                                 Model model, RedirectAttributes redirect) {
        return evenementOpslaan(null, form, result, model, redirect);
    }

    @GetMapping("/evenementen/{id}")
    public String bewerkEvenement(@PathVariable Long id, Model model) {
        var evenement = evenementService.get(id);
        model.addAttribute("evenement", evenement);
        model.addAttribute("form", EvenementForm.van(evenement));
        return toonEvenementForm(model);
    }

    @PostMapping("/evenementen/{id}")
    public String bewerkEvenement(@PathVariable Long id, @Valid @ModelAttribute("form") EvenementForm form,
                                  BindingResult result, Model model, RedirectAttributes redirect) {
        model.addAttribute("evenement", evenementService.get(id));
        return evenementOpslaan(id, form, result, model, redirect);
    }

    @PostMapping("/evenementen/{id}/verwijderen")
    public String verwijderEvenement(@PathVariable Long id, RedirectAttributes redirect) {
        evenementService.verwijderen(id);
        redirect.addFlashAttribute("succes", "Het evenement werd verwijderd.");
        return "redirect:/admin/evenementen";
    }

    private String evenementOpslaan(Long id, EvenementForm form, BindingResult result, Model model,
                                    RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                evenementService.opslaanAlsBeheerder(id, form);
                redirect.addFlashAttribute("succes", "'" + form.getTitel() + "' werd opgeslagen.");
                return "redirect:/admin/evenementen";
            } catch (BedrijfsregelException e) {
                result.reject("evenement", e.getMessage());
            }
        }
        return toonEvenementForm(model);
    }

    private String toonEvenementForm(Model model) {
        model.addAttribute("regios", regioService.alle());
        model.addAttribute("kiesbareOndernemingen", evenementService.kiesbareOndernemingen(null));
        model.addAttribute("formActie", "/admin/evenementen");
        return "evenement-form";
    }
}
