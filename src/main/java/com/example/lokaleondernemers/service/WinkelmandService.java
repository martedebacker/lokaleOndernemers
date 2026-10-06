package com.example.lokaleondernemers.service;

import com.example.lokaleondernemers.model.Onderneming;
import com.example.lokaleondernemers.model.Product;
import com.example.lokaleondernemers.repository.ProductRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WinkelmandService {

    /** Maximaal aantal stuks van één product per bestelling. */
    public static final int MAX_PER_PRODUCT = 99;

    private final ProductRepository productRepository;

    public void toevoegen(Winkelmand winkelmand, Long productId, int aantal) {
        Product product = productRepository.findById(productId)
                .filter(Product::isTeKoop)
                .orElseThrow(() -> new NietGevondenException("Product niet gevonden"));
        int nieuwAantal = winkelmand.getAantal(productId) + Math.max(1, aantal);
        controleerVoorraad(product, nieuwAantal);
        winkelmand.zetAantal(productId, nieuwAantal);
    }

    public void wijzigAantal(Winkelmand winkelmand, Long productId, int aantal) {
        if (aantal <= 0) {
            winkelmand.verwijder(productId);
            return;
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NietGevondenException("Product niet gevonden"));
        controleerVoorraad(product, aantal);
        winkelmand.zetAantal(productId, aantal);
    }

    /**
     * Het overzicht van de winkelmand, gegroepeerd per onderneming (elke onderneming wordt een
     * aparte bestelling met een eigen ophaaladres). Producten die intussen niet meer te koop zijn,
     * worden uit de winkelmand gehaald.
     */
    public List<Groep> overzicht(Winkelmand winkelmand) {
        Map<Long, Groep> groepen = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> item : winkelmand.getItems().entrySet()) {
            Optional<Product> product = productRepository.findById(item.getKey()).filter(Product::isTeKoop);
            if (product.isEmpty()) {
                winkelmand.verwijder(item.getKey());
                continue;
            }
            Product p = product.get();
            groepen.computeIfAbsent(p.getOnderneming().getId(), id -> new Groep(p.getOnderneming()))
                    .regels.add(new Regel(p, item.getValue()));
        }
        return new ArrayList<>(groepen.values());
    }

    public static BigDecimal totaal(List<Groep> groepen) {
        return groepen.stream().map(Groep::getTotaal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void controleerVoorraad(Product product, int aantal) {
        if (aantal > MAX_PER_PRODUCT) {
            throw new BedrijfsregelException("Je kan maximaal " + MAX_PER_PRODUCT + " stuks per product bestellen.");
        }
        if (aantal > product.getVoorraad()) {
            throw new BedrijfsregelException(product.getVoorraad() == 0
                    ? "'" + product.getNaam() + "' is momenteel uitverkocht."
                    : "Er zijn nog maar " + product.getVoorraad() + " stuks van '" + product.getNaam() + "' beschikbaar.");
        }
    }

    @Getter
    public static class Groep {
        private final Onderneming onderneming;
        private final List<Regel> regels = new ArrayList<>();

        Groep(Onderneming onderneming) {
            this.onderneming = onderneming;
        }

        public BigDecimal getTotaal() {
            return regels.stream().map(Regel::getSubtotaal).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    @Getter
    public static class Regel {
        private final Product product;
        private final int aantal;

        Regel(Product product, int aantal) {
            this.product = product;
            this.aantal = aantal;
        }

        public BigDecimal getSubtotaal() {
            return product.getPrijs().multiply(BigDecimal.valueOf(aantal));
        }

        public boolean isVoorraadTeKort() {
            return aantal > product.getVoorraad();
        }
    }
}
