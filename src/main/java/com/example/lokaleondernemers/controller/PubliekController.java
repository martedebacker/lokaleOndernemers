package com.example.lokaleondernemers.controller;

import com.example.lokaleondernemers.model.Afbeelding;
import com.example.lokaleondernemers.model.Onderneming;
import com.example.lokaleondernemers.model.Product;
import com.example.lokaleondernemers.model.Regio;
import com.example.lokaleondernemers.service.AfbeeldingService;
import com.example.lokaleondernemers.service.EvenementService;
import com.example.lokaleondernemers.service.OndernemingService;
import com.example.lokaleondernemers.service.ProductService;
import com.example.lokaleondernemers.service.RegioService;
import com.example.lokaleondernemers.web.HuidigeGebruiker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Duration;
import java.util.List;

/** Pagina's die iedereen kan bekijken, ook zonder account. */
@Controller
@RequiredArgsConstructor
public class PubliekController {

    private final RegioService regioService;
    private final OndernemingService ondernemingService;
    private final ProductService productService;
    private final AfbeeldingService afbeeldingService;
    private final EvenementService evenementService;
    private final HuidigeGebruiker huidigeGebruiker;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("regios", regioService.metAantalOndernemingen());
        model.addAttribute("producten", productService.zoekTeKoop(null, null, 8));
        List<Onderneming> ondernemingen = ondernemingService.zoekActief(null, null);
        model.addAttribute("ondernemingen", ondernemingen.subList(0, Math.min(6, ondernemingen.size())));
        model.addAttribute("evenementen", evenementService.komende(null, 3));
        return "index";
    }

    /** Doel van de regiokiezer: stuurt door naar de overzichtspagina van de gekozen regio. */
    @GetMapping("/regio")
    public String kiesRegio(@RequestParam(required = false) Long regioId) {
        return regioId == null ? "redirect:/" : "redirect:/regio/" + regioId;
    }

    @GetMapping("/regio/{id}")
    public String regio(@PathVariable Long id, @RequestParam(required = false) String zoek, Model model) {
        Regio regio = regioService.get(id);
        model.addAttribute("regio", regio);
        model.addAttribute("alleRegios", regioService.alle());
        model.addAttribute("zoek", zoek);
        model.addAttribute("ondernemingen", ondernemingService.zoekActief(id, zoek));
        model.addAttribute("producten", productService.zoekTeKoop(regio, zoek, 48));
        model.addAttribute("evenementen", evenementService.komende(regio, 6));
        return "regio";
    }

    @GetMapping("/ondernemingen")
    public String ondernemingen(@RequestParam(required = false) Long regio,
                                @RequestParam(required = false) String zoek, Model model) {
        model.addAttribute("alleRegios", regioService.alle());
        model.addAttribute("gekozenRegio", regio);
        model.addAttribute("zoek", zoek);
        model.addAttribute("ondernemingen", ondernemingService.zoekActief(regio, zoek));
        return "ondernemingen";
    }

    @GetMapping("/producten")
    public String producten(@RequestParam(required = false) Long regio,
                            @RequestParam(required = false) String zoek, Model model) {
        model.addAttribute("alleRegios", regioService.alle());
        model.addAttribute("gekozenRegio", regio);
        model.addAttribute("zoek", zoek);
        model.addAttribute("producten",
                productService.zoekTeKoop(regio == null ? null : regioService.get(regio), zoek, 96));
        return "producten";
    }

    @GetMapping("/winkel/{slug}")
    public String winkel(@PathVariable String slug, Model model) {
        Onderneming onderneming = ondernemingService.getWinkel(slug, huidigeGebruiker.zoek().orElse(null));
        model.addAttribute("onderneming", onderneming);
        model.addAttribute("productenPerCategorie", productService.teKoopPerCategorie(onderneming));
        model.addAttribute("evenementen", evenementService.komendeVan(onderneming));
        return "winkel";
    }

    @GetMapping("/product/{id}")
    public String product(@PathVariable Long id, Model model) {
        Product product = productService.getTeKoop(id);
        model.addAttribute("product", product);
        model.addAttribute("onderneming", product.getOnderneming());
        model.addAttribute("meer", productService.teKoopPerCategorie(product.getOnderneming()).values().stream()
                .flatMap(List::stream)
                .filter(p -> !p.getId().equals(id))
                .limit(4)
                .toList());
        return "product";
    }

    @GetMapping("/evenementen")
    public String evenementen(@RequestParam(required = false) Long regio, Model model) {
        model.addAttribute("alleRegios", regioService.alle());
        model.addAttribute("gekozenRegio", regio);
        model.addAttribute("evenementen",
                evenementService.komende(regio == null ? null : regioService.get(regio), 200));
        return "evenementen";
    }

    @GetMapping("/evenementen/{id}")
    public String evenement(@PathVariable Long id, Model model) {
        model.addAttribute("evenement", evenementService.getPubliek(id, huidigeGebruiker.zoek().orElse(null)));
        return "evenement";
    }

    @GetMapping("/afbeelding/{id}")
    @ResponseBody
    public ResponseEntity<byte[]> afbeelding(@PathVariable Long id) {
        Afbeelding afbeelding = afbeeldingService.get(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(afbeelding.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(afbeelding.getData());
    }

    @GetMapping("/hoe-werkt-het")
    public String hoeWerktHet() {
        return "hoe-werkt-het";
    }
}
