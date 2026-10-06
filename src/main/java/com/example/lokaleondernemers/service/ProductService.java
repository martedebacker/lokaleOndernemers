package com.example.lokaleondernemers.service;

import com.example.lokaleondernemers.dto.ProductForm;
import com.example.lokaleondernemers.model.Afbeelding;
import com.example.lokaleondernemers.model.Onderneming;
import com.example.lokaleondernemers.model.OndernemingStatus;
import com.example.lokaleondernemers.model.Product;
import com.example.lokaleondernemers.model.Regio;
import com.example.lokaleondernemers.repository.BestellingRepository;
import com.example.lokaleondernemers.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.example.lokaleondernemers.service.RegioService.leegNaarNull;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    public static final String ZONDER_CATEGORIE = "Overige";

    private final ProductRepository repository;
    private final BestellingRepository bestellingRepository;
    private final AfbeeldingService afbeeldingService;

    public Product get(Long id) {
        return repository.findById(id).orElseThrow(() -> new NietGevondenException("Product niet gevonden"));
    }

    /** Een product zoals een klant het ziet: enkel zichtbare producten van actieve ondernemingen. */
    public Product getTeKoop(Long id) {
        Product p = get(id);
        if (!p.isTeKoop()) {
            throw new NietGevondenException("Product niet gevonden");
        }
        return p;
    }

    /** Een product dat de ondernemer zelf beheert; producten van anderen zijn verboden terrein. */
    public Product getVanOnderneming(Long id, Onderneming onderneming) {
        Product p = get(id);
        if (!p.getOnderneming().getId().equals(onderneming.getId()) || p.isVerwijderd()) {
            throw new AccessDeniedException("Dit product behoort niet tot jouw onderneming.");
        }
        return p;
    }

    public List<Product> vanOnderneming(Onderneming onderneming) {
        return repository.findByOndernemingAndVerwijderdFalseOrderByNaamAsc(onderneming);
    }

    public long aantalVanOnderneming(Onderneming onderneming) {
        return repository.countByOndernemingAndVerwijderdFalse(onderneming);
    }

    /** Zichtbare producten van een winkel, gegroepeerd per categorie. */
    public Map<String, List<Product>> teKoopPerCategorie(Onderneming onderneming) {
        return repository.findByOndernemingAndZichtbaarTrueAndVerwijderdFalseOrderByCategorieAscNaamAsc(onderneming)
                .stream()
                .collect(Collectors.groupingBy(
                        p -> p.getCategorie() == null || p.getCategorie().isBlank() ? ZONDER_CATEGORIE : p.getCategorie(),
                        TreeMap::new,
                        Collectors.toList()));
    }

    public List<Product> zoekTeKoop(Regio regio, String zoekterm, int max) {
        return repository.zoekTeKoop(OndernemingStatus.ACTIEF, regio, leegNaarNull(zoekterm), PageRequest.of(0, max));
    }

    @Transactional
    public Product opslaan(Onderneming onderneming, Long productId, ProductForm form) {
        Product p = productId == null ? new Product() : getVanOnderneming(productId, onderneming);
        if (productId == null) {
            p.setOnderneming(onderneming);
        }
        p.setNaam(form.getNaam().trim());
        p.setBeschrijving(leegNaarNull(form.getBeschrijving()));
        p.setPrijs(form.getPrijs());
        p.setEenheid(leegNaarNull(form.getEenheid()));
        p.setCategorie(leegNaarNull(form.getCategorie()));
        p.setVoorraad(form.getVoorraad());
        p.setZichtbaar(form.isZichtbaar());

        Afbeelding afbeelding = afbeeldingService.vanUpload(form.getAfbeelding());
        if (afbeelding != null) {
            p.setAfbeelding(afbeelding);
        } else if (form.isAfbeeldingVerwijderen()) {
            p.setAfbeelding(null);
        }
        return repository.save(p);
    }

    @Transactional
    public void wisselZichtbaarheid(Long productId, Onderneming onderneming) {
        Product p = getVanOnderneming(productId, onderneming);
        p.setZichtbaar(!p.isZichtbaar());
    }

    /**
     * Verwijdert een product. Als het product al in een bestelling voorkomt, wordt het enkel
     * gearchiveerd zodat de bestelgeschiedenis intact blijft.
     */
    @Transactional
    public void verwijderen(Long productId, Onderneming onderneming) {
        Product p = getVanOnderneming(productId, onderneming);
        if (bestellingRepository.isProductBesteld(p)) {
            p.setVerwijderd(true);
            p.setZichtbaar(false);
        } else {
            repository.delete(p);
        }
    }
}
