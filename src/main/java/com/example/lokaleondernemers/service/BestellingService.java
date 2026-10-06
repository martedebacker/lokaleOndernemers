package com.example.lokaleondernemers.service;

import com.example.lokaleondernemers.dto.AfrekenForm;
import com.example.lokaleondernemers.model.BestelRegel;
import com.example.lokaleondernemers.model.BestelStatus;
import com.example.lokaleondernemers.model.Bestelling;
import com.example.lokaleondernemers.model.Gebruiker;
import com.example.lokaleondernemers.model.Onderneming;
import com.example.lokaleondernemers.model.Product;
import com.example.lokaleondernemers.repository.BestellingRepository;
import com.example.lokaleondernemers.mail.BestellingMailEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.example.lokaleondernemers.service.RegioService.leegNaarNull;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BestellingService {

    private final BestellingRepository repository;
    private final WinkelmandService winkelmandService;
    private final ApplicationEventPublisher events;

    /**
     * Zet de winkelmand om in bestellingen: één per onderneming, want elke ondernemer wordt
     * apart bezocht om de bestelling op te halen. De voorraad wordt meteen gereserveerd.
     */
    @Transactional
    public List<Bestelling> plaats(Gebruiker klant, Winkelmand winkelmand, AfrekenForm form) {
        List<WinkelmandService.Groep> groepen = winkelmandService.overzicht(winkelmand);
        if (groepen.isEmpty()) {
            throw new BedrijfsregelException("Je winkelmand is leeg.");
        }
        List<Bestelling> bestellingen = new ArrayList<>();
        for (WinkelmandService.Groep groep : groepen) {
            Bestelling bestelling = new Bestelling();
            bestelling.setKlant(klant);
            bestelling.setOnderneming(groep.getOnderneming());
            bestelling.setGewensteOphaaldatum(form.getGewensteOphaaldatum());
            bestelling.setOpmerkingKlant(leegNaarNull(form.getOpmerking()));
            for (WinkelmandService.Regel regel : groep.getRegels()) {
                Product product = regel.getProduct();
                if (regel.getAantal() > product.getVoorraad()) {
                    throw new BedrijfsregelException(product.getVoorraad() == 0
                            ? "'" + product.getNaam() + "' is intussen uitverkocht. Pas je winkelmand aan."
                            : "Er zijn nog maar " + product.getVoorraad() + " stuks van '" + product.getNaam()
                              + "' beschikbaar. Pas je winkelmand aan.");
                }
                product.setVoorraad(product.getVoorraad() - regel.getAantal());
                bestelling.voegRegelToe(new BestelRegel(product, regel.getAantal()));
            }
            bestellingen.add(repository.save(bestelling));
        }
        winkelmand.leegmaken();
        events.publishEvent(BestellingMailEvent.geplaatst(bestellingen.stream().map(Bestelling::getId).toList()));
        return bestellingen;
    }

    // ---- klant ----

    public List<Bestelling> vanKlant(Gebruiker klant) {
        return repository.findByKlantOrderByGeplaatstOpDesc(klant);
    }

    public Bestelling getVoorKlant(Long id, Gebruiker klant) {
        Bestelling b = get(id);
        if (!b.getKlant().getId().equals(klant.getId())) {
            throw new AccessDeniedException("Dit is niet jouw bestelling.");
        }
        return b;
    }

    /** Een klant kan annuleren zolang de ondernemer de bestelling nog niet bevestigd heeft. */
    @Transactional
    public void annuleerDoorKlant(Long id, Gebruiker klant) {
        Bestelling b = getVoorKlant(id, klant);
        if (b.getStatus() != BestelStatus.GEPLAATST) {
            throw new BedrijfsregelException("Deze bestelling werd al bevestigd door de ondernemer. "
                    + "Neem contact op met de ondernemer om ze te annuleren.");
        }
        annuleer(b);
        events.publishEvent(BestellingMailEvent.geannuleerdDoorKlant(b.getId()));
    }

    // ---- ondernemer ----

    public List<Bestelling> vanOnderneming(Onderneming onderneming, boolean enkelOpen) {
        if (enkelOpen) {
            return repository.findByOndernemingAndStatusInOrderByGeplaatstOpDesc(onderneming,
                    EnumSet.of(BestelStatus.GEPLAATST, BestelStatus.BEVESTIGD, BestelStatus.KLAAR));
        }
        return repository.findByOndernemingOrderByGeplaatstOpDesc(onderneming);
    }

    public long aantalNieuw(Onderneming onderneming) {
        return repository.countByOndernemingAndStatus(onderneming, BestelStatus.GEPLAATST);
    }

    public long aantalKlaar(Onderneming onderneming) {
        return repository.countByOndernemingAndStatus(onderneming, BestelStatus.KLAAR);
    }

    public Bestelling getVoorOnderneming(Long id, Onderneming onderneming) {
        Bestelling b = get(id);
        if (!b.getOnderneming().getId().equals(onderneming.getId())) {
            throw new AccessDeniedException("Deze bestelling hoort niet bij jouw onderneming.");
        }
        return b;
    }

    @Transactional
    public void wijzigStatus(Long id, Onderneming onderneming, BestelStatus nieuweStatus, String ophaalAfspraak) {
        Bestelling b = getVoorOnderneming(id, onderneming);
        boolean statusGewijzigd = false;
        boolean afspraakGewijzigd = false;
        if (nieuweStatus != null && nieuweStatus != b.getStatus()) {
            statusGewijzigd = true;
            if (!b.getStatus().getVolgendeStatussen().contains(nieuweStatus)) {
                throw new BedrijfsregelException("Een bestelling met status '" + b.getStatus().getLabel()
                        + "' kan niet naar '" + nieuweStatus.getLabel() + "' gezet worden.");
            }
            if (nieuweStatus == BestelStatus.GEANNULEERD) {
                annuleer(b);
            } else {
                b.setStatus(nieuweStatus);
            }
        }
        if (ophaalAfspraak != null && !java.util.Objects.equals(leegNaarNull(ophaalAfspraak), b.getOphaalAfspraak())) {
            b.setOphaalAfspraak(leegNaarNull(ophaalAfspraak));
            afspraakGewijzigd = true;
        }
        b.setBijgewerktOp(LocalDateTime.now());
        // Bij elke wijziging krijgt de klant een e-mail.
        if (statusGewijzigd || afspraakGewijzigd) {
            events.publishEvent(BestellingMailEvent.bijgewerkt(b.getId(), statusGewijzigd));
        }
    }

    // ---- beheerder ----

    public List<Bestelling> alle() {
        return repository.findAllByOrderByGeplaatstOpDesc();
    }

    public long aantalMetStatus(BestelStatus status) {
        return repository.countByStatus(status);
    }

    public long aantal() {
        return repository.count();
    }

    // ---- intern ----

    private Bestelling get(Long id) {
        return repository.findById(id).orElseThrow(() -> new NietGevondenException("Bestelling niet gevonden"));
    }

    /** Annuleert en zet de gereserveerde voorraad terug. */
    private void annuleer(Bestelling b) {
        for (BestelRegel regel : b.getRegels()) {
            if (regel.getProduct() != null) {
                regel.getProduct().setVoorraad(regel.getProduct().getVoorraad() + regel.getAantal());
            }
        }
        b.setStatus(BestelStatus.GEANNULEERD);
        b.setBijgewerktOp(LocalDateTime.now());
    }
}
