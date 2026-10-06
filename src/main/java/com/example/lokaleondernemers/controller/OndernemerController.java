package com.example.lokaleondernemers.controller;

import com.example.lokaleondernemers.dto.EvenementForm;
import com.example.lokaleondernemers.dto.FeedbackForm;
import com.example.lokaleondernemers.dto.OndernemingForm;
import com.example.lokaleondernemers.dto.ProductForm;
import com.example.lokaleondernemers.mail.MailService;
import com.example.lokaleondernemers.model.BestelStatus;
import com.example.lokaleondernemers.model.Feedback;
import com.example.lokaleondernemers.model.Gebruiker;
import com.example.lokaleondernemers.model.Rol;
import com.example.lokaleondernemers.model.Onderneming;
import com.example.lokaleondernemers.model.Product;
import com.example.lokaleondernemers.service.BedrijfsregelException;
import com.example.lokaleondernemers.service.BestellingService;
import com.example.lokaleondernemers.service.EvenementService;
import com.example.lokaleondernemers.service.FeedbackService;
import com.example.lokaleondernemers.service.ProductImportService;
import com.example.lokaleondernemers.service.OndernemingService;
import com.example.lokaleondernemers.service.ProductService;
import com.example.lokaleondernemers.service.RegioService;
import com.example.lokaleondernemers.web.HuidigeGebruiker;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Het beheergedeelte van een ondernemer: winkelpagina, producten en bestellingen. */
@Controller
@RequestMapping("/beheer")
@RequiredArgsConstructor
public class OndernemerController {

    private final OndernemingService ondernemingService;
    private final ProductService productService;
    private final BestellingService bestellingService;
    private final RegioService regioService;
    private final EvenementService evenementService;
    private final ProductImportService importService;
    private final FeedbackService feedbackService;
    private final MailService mailService;

    /** Sessie-attribuut: de onderneming die een beheerder tijdelijk beheert (opstarthulp). */
    public static final String BEHEER_ALS = "beheerAlsOnderneming";
    private final HuidigeGebruiker huidigeGebruiker;

    /**
     * De onderneming die beheerd wordt: die van de ingelogde ondernemer, of voor een beheerder de
     * onderneming die hij via "Winkel inrichten" koos (bv. bij opstarthulp).
     */
    @ModelAttribute("onderneming")
    public Onderneming onderneming(HttpSession sessie) {
        Gebruiker g = huidigeGebruiker.get();
        if (g.getRol() == Rol.ADMIN) {
            if (!(sessie.getAttribute(BEHEER_ALS) instanceof Long id)) {
                throw new GeenOndernemingGekozen();
            }
            return ondernemingService.get(id);
        }
        return ondernemingService.vanEigenaarVerplicht(g);
    }

    static class GeenOndernemingGekozen extends RuntimeException {
    }

    @ExceptionHandler(GeenOndernemingGekozen.class)
    public String kiesEerstOnderneming() {
        return "redirect:/admin/ondernemingen?kies";
    }

    @GetMapping
    public String dashboard(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming, Model model) {
        List<Product> producten = productService.vanOnderneming(onderneming);
        model.addAttribute("aantalProducten", producten.size());
        model.addAttribute("uitverkocht", producten.stream().filter(p -> !p.isOpVoorraad()).toList());
        model.addAttribute("aantalNieuw", bestellingService.aantalNieuw(onderneming));
        model.addAttribute("aantalKlaar", bestellingService.aantalKlaar(onderneming));
        model.addAttribute("openBestellingen", bestellingService.vanOnderneming(onderneming, true));
        return "beheer/dashboard";
    }

    // ---- winkelpagina ----

    @GetMapping("/onderneming")
    public String onderneming(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming, Model model) {
        model.addAttribute("form", OndernemingForm.van(onderneming));
        model.addAttribute("regios", regioService.alle());
        return "beheer/onderneming";
    }

    @PostMapping("/onderneming")
    public String onderneming(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming,
                              @Valid @ModelAttribute("form") OndernemingForm form, BindingResult result,
                              Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                ondernemingService.bijwerken(onderneming.getId(), form);
                redirect.addFlashAttribute("succes", "Je winkelpagina is bijgewerkt.");
                return "redirect:/beheer/onderneming";
            } catch (BedrijfsregelException e) {
                result.reject("onderneming", e.getMessage());
            }
        }
        model.addAttribute("regios", regioService.alle());
        return "beheer/onderneming";
    }

    // ---- producten ----

    @GetMapping("/producten")
    public String producten(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming, Model model) {
        model.addAttribute("producten", productService.vanOnderneming(onderneming));
        return "beheer/producten";
    }

    @GetMapping("/producten/nieuw")
    public String nieuwProduct(Model model) {
        model.addAttribute("form", new ProductForm());
        return "beheer/product-form";
    }

    @PostMapping("/producten/nieuw")
    public String nieuwProduct(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming,
                               @Valid @ModelAttribute("form") ProductForm form, BindingResult result,
                               RedirectAttributes redirect) {
        return productOpslaan(onderneming, null, form, result, redirect);
    }

    @GetMapping("/producten/{id}")
    public String bewerkProduct(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming, @PathVariable Long id,
                                Model model) {
        Product product = productService.getVanOnderneming(id, onderneming);
        model.addAttribute("product", product);
        model.addAttribute("form", ProductForm.van(product));
        return "beheer/product-form";
    }

    @PostMapping("/producten/{id}")
    public String bewerkProduct(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming, @PathVariable Long id,
                                @Valid @ModelAttribute("form") ProductForm form, BindingResult result,
                                Model model, RedirectAttributes redirect) {
        model.addAttribute("product", productService.getVanOnderneming(id, onderneming));
        return productOpslaan(onderneming, id, form, result, redirect);
    }

    @PostMapping("/producten/{id}/zichtbaarheid")
    public String zichtbaarheid(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming, @PathVariable Long id) {
        productService.wisselZichtbaarheid(id, onderneming);
        return "redirect:/beheer/producten";
    }

    @PostMapping("/producten/{id}/verwijderen")
    public String verwijderProduct(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming, @PathVariable Long id,
                                   RedirectAttributes redirect) {
        productService.verwijderen(id, onderneming);
        redirect.addFlashAttribute("succes", "Het product werd verwijderd.");
        return "redirect:/beheer/producten";
    }

    private String productOpslaan(Onderneming onderneming, Long id, ProductForm form, BindingResult result,
                                  RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                productService.opslaan(onderneming, id, form);
                redirect.addFlashAttribute("succes", "'" + form.getNaam() + "' werd opgeslagen.");
                return "redirect:/beheer/producten";
            } catch (BedrijfsregelException e) {
                result.reject("product", e.getMessage());
            }
        }
        return "beheer/product-form";
    }

    // ---- bestellingen ----

    @GetMapping("/bestellingen")
    public String bestellingen(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming,
                               @RequestParam(defaultValue = "false") boolean alle, Model model) {
        model.addAttribute("alle", alle);
        model.addAttribute("bestellingen", bestellingService.vanOnderneming(onderneming, !alle));
        return "beheer/bestellingen";
    }

    @GetMapping("/bestellingen/{id}")
    public String bestelling(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming, @PathVariable Long id,
                             Model model) {
        model.addAttribute("bestelling", bestellingService.getVoorOnderneming(id, onderneming));
        return "beheer/bestelling";
    }

    @PostMapping("/bestellingen/{id}")
    public String bestellingBijwerken(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming, @PathVariable Long id,
                                      @RequestParam(required = false) BestelStatus status,
                                      @RequestParam(required = false) String ophaalAfspraak,
                                      RedirectAttributes redirect) {
        try {
            bestellingService.wijzigStatus(id, onderneming, status, ophaalAfspraak);
            redirect.addFlashAttribute("succes", "De bestelling werd bijgewerkt.");
        } catch (BedrijfsregelException e) {
            redirect.addFlashAttribute("fout", e.getMessage());
        }
        return "redirect:/beheer/bestellingen/" + id;
    }

    // ---- evenementen ----

    @GetMapping("/evenementen")
    public String evenementen(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming, Model model) {
        model.addAttribute("evenementen", evenementService.allesVan(onderneming));
        return "beheer/evenementen";
    }

    @GetMapping("/evenementen/nieuw")
    public String nieuwEvenement(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming, Model model) {
        model.addAttribute("form", EvenementForm.voor(onderneming));
        return toonEvenementForm(onderneming, model);
    }

    @PostMapping("/evenementen/nieuw")
    public String nieuwEvenement(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming,
                                 @Valid @ModelAttribute("form") EvenementForm form, BindingResult result,
                                 Model model, RedirectAttributes redirect) {
        return evenementOpslaan(onderneming, null, form, result, model, redirect);
    }

    @GetMapping("/evenementen/{id}")
    public String bewerkEvenement(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming, @PathVariable Long id,
                                  Model model) {
        var evenement = evenementService.getAlsOrganisator(id, onderneming);
        model.addAttribute("evenement", evenement);
        model.addAttribute("form", EvenementForm.van(evenement));
        return toonEvenementForm(onderneming, model);
    }

    @PostMapping("/evenementen/{id}")
    public String bewerkEvenement(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming, @PathVariable Long id,
                                  @Valid @ModelAttribute("form") EvenementForm form, BindingResult result,
                                  Model model, RedirectAttributes redirect) {
        model.addAttribute("evenement", evenementService.getAlsOrganisator(id, onderneming));
        return evenementOpslaan(onderneming, id, form, result, model, redirect);
    }

    @PostMapping("/evenementen/{id}/verwijderen")
    public String verwijderEvenement(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming, @PathVariable Long id,
                                     RedirectAttributes redirect) {
        evenementService.verwijderenAlsOnderneming(id, onderneming);
        redirect.addFlashAttribute("succes", "Het evenement werd verwijderd.");
        return "redirect:/beheer/evenementen";
    }

    @PostMapping("/evenementen/{id}/uitstappen")
    public String uitstappen(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming, @PathVariable Long id,
                             RedirectAttributes redirect) {
        try {
            evenementService.uitstappen(id, onderneming);
            redirect.addFlashAttribute("succes", "Je onderneming neemt niet langer deel aan dit evenement.");
        } catch (BedrijfsregelException e) {
            redirect.addFlashAttribute("fout", e.getMessage());
        }
        return "redirect:/beheer/evenementen";
    }

    private String evenementOpslaan(Onderneming onderneming, Long id, EvenementForm form, BindingResult result,
                                    Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                evenementService.opslaanAlsOnderneming(onderneming, id, form);
                redirect.addFlashAttribute("succes", "'" + form.getTitel() + "' werd opgeslagen.");
                return "redirect:/beheer/evenementen";
            } catch (BedrijfsregelException e) {
                result.reject("evenement", e.getMessage());
            }
        }
        return toonEvenementForm(onderneming, model);
    }

    private String toonEvenementForm(Onderneming onderneming, Model model) {
        model.addAttribute("regios", regioService.alle());
        model.addAttribute("kiesbareOndernemingen", evenementService.kiesbareOndernemingen(onderneming));
        model.addAttribute("formActie", "/beheer/evenementen");
        return "evenement-form";
    }

    // ---- producten importeren uit Excel ----

    @GetMapping("/producten/importeren")
    public String importeren(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming, HttpSession sessie, Model model) {
        ProductImportService.Sessie imp = importSessie(sessie, onderneming);
        if (imp != null) {
            model.addAttribute("imp", imp);
            model.addAttribute("velden", ProductImportService.Veld.values());
            model.addAttribute("voorbeeld", importService.controleer(imp.getTabel(), imp.getKoppeling(), onderneming));
        }
        model.addAttribute("maxRijen", ProductImportService.MAX_RIJEN);
        return "beheer/producten-importeren";
    }

    @GetMapping("/producten/importeren/sjabloon")
    @ResponseBody
    public ResponseEntity<byte[]> importSjabloon() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("producten-sjabloon.xlsx").build().toString())
                .body(importService.sjabloon());
    }

    @PostMapping("/producten/importeren")
    public String importBestand(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming, @RequestParam("bestand") MultipartFile bestand,
                                HttpSession sessie, RedirectAttributes redirect) {
        try {
            ProductImportService.Tabel tabel = importService.lees(bestand);
            sessie.setAttribute(ProductImportService.Sessie.ATTRIBUUT, new ProductImportService.Sessie(
                    onderneming.getId(), tabel, importService.raadKoppeling(tabel.getKolommen())));
        } catch (BedrijfsregelException e) {
            redirect.addFlashAttribute("fout", e.getMessage());
        }
        return "redirect:/beheer/producten/importeren";
    }

    /** De ondernemer past aan welke Excelkolom bij welk veld hoort (parameters kolom0, kolom1, ...). */
    @PostMapping("/producten/importeren/koppeling")
    public String importKoppeling(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming, @RequestParam Map<String, String> parameters,
                                  HttpSession sessie, RedirectAttributes redirect) {
        ProductImportService.Sessie imp = importSessie(sessie, onderneming);
        if (imp == null) {
            return "redirect:/beheer/producten/importeren";
        }
        Map<Integer, ProductImportService.Veld> koppeling = new HashMap<>();
        for (int i = 0; i < imp.getTabel().getKolommen().size(); i++) {
            String waarde = parameters.get("kolom" + i);
            if (waarde == null || waarde.isBlank()) {
                continue;
            }
            ProductImportService.Veld veld;
            try {
                veld = ProductImportService.Veld.valueOf(waarde);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (koppeling.containsValue(veld)) {
                redirect.addFlashAttribute("fout", "'" + veld.getLabel() + "' is aan meer dan één kolom gekoppeld.");
                return "redirect:/beheer/producten/importeren";
            }
            koppeling.put(i, veld);
        }
        sessie.setAttribute(ProductImportService.Sessie.ATTRIBUUT,
                new ProductImportService.Sessie(onderneming.getId(), imp.getTabel(), koppeling));
        return "redirect:/beheer/producten/importeren";
    }

    @PostMapping("/producten/importeren/bevestigen")
    public String importBevestigen(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming, HttpSession sessie, RedirectAttributes redirect) {
        ProductImportService.Sessie imp = importSessie(sessie, onderneming);
        if (imp == null) {
            return "redirect:/beheer/producten/importeren";
        }
        try {
            ProductImportService.Resultaat r = importService.importeer(imp.getTabel(), imp.getKoppeling(), onderneming);
            sessie.removeAttribute(ProductImportService.Sessie.ATTRIBUUT);
            String melding = r.nieuw() + " nieuwe producten toegevoegd, " + r.bijgewerkt() + " bestaande bijgewerkt.";
            if (r.overgeslagen() > 0) {
                melding += " " + r.overgeslagen() + " rijen met fouten werden overgeslagen.";
            }
            redirect.addFlashAttribute("succes", melding + " Voeg nu eventueel nog foto's toe.");
            return "redirect:/beheer/producten";
        } catch (BedrijfsregelException e) {
            redirect.addFlashAttribute("fout", e.getMessage());
            return "redirect:/beheer/producten/importeren";
        }
    }

    @PostMapping("/producten/importeren/annuleren")
    public String importAnnuleren(HttpSession sessie) {
        sessie.removeAttribute(ProductImportService.Sessie.ATTRIBUUT);
        return "redirect:/beheer/producten";
    }

    private ProductImportService.Sessie importSessie(HttpSession sessie, Onderneming onderneming) {
        Object imp = sessie.getAttribute(ProductImportService.Sessie.ATTRIBUUT);
        return imp instanceof ProductImportService.Sessie s && s.getOndernemingId().equals(onderneming.getId()) ? s : null;
    }

    // ---- feedback over het platform ----

    @GetMapping("/feedback")
    public String feedback(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming, Model model) {
        model.addAttribute("form", new FeedbackForm());
        return toonFeedback(onderneming, model);
    }

    @PostMapping("/feedback")
    public String feedback(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming, @Valid @ModelAttribute("form") FeedbackForm form,
                           BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return toonFeedback(onderneming, model);
        }
        feedbackService.geef(onderneming, huidigeGebruiker.get(), form);
        redirect.addFlashAttribute("succes", "Bedankt voor je feedback! We bekijken ze en laten je hier weten wat we ermee doen.");
        return "redirect:/beheer/feedback";
    }

    private String toonFeedback(Onderneming onderneming, Model model) {
        model.addAttribute("soorten", Feedback.Soort.values());
        model.addAttribute("feedbackLijst", feedbackService.vanOnderneming(onderneming));
        return "beheer/feedback";
    }

    // ---- opstarthulp achteraf aanvragen ----

    @PostMapping("/opstarthulp")
    public String opstarthulp(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming, @RequestParam(required = false) String wensen,
                              RedirectAttributes redirect) {
        try {
            ondernemingService.vraagOpstarthulpAan(onderneming.getId(), wensen);
            redirect.addFlashAttribute("succes", "Je aanvraag is goed ontvangen. We nemen contact met je op om je winkelpagina samen in orde te brengen.");
        } catch (BedrijfsregelException e) {
            redirect.addFlashAttribute("fout", e.getMessage());
        }
        return "redirect:/beheer";
    }

    // ---- voorbeeld van de e-mails aan klanten ----

    @GetMapping(value = "/onderneming/mail-voorbeeld", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String mailVoorbeeld(@ModelAttribute(name = "onderneming", binding = false) Onderneming onderneming,
                                @RequestParam(defaultValue = "GEPLAATST") BestelStatus status) {
        return mailService.voorbeeld(onderneming, status);
    }
}
