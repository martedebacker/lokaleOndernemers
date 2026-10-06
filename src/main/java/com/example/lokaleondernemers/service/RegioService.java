package com.example.lokaleondernemers.service;

import com.example.lokaleondernemers.dto.RegioForm;
import com.example.lokaleondernemers.model.Evenement;
import com.example.lokaleondernemers.model.Onderneming;
import com.example.lokaleondernemers.model.OndernemingStatus;
import com.example.lokaleondernemers.model.Regio;
import com.example.lokaleondernemers.repository.EvenementRepository;
import com.example.lokaleondernemers.repository.OndernemingRepository;
import com.example.lokaleondernemers.repository.RegioRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegioService {

    private final RegioRepository repository;
    private final OndernemingRepository ondernemingRepository;
    private final EvenementRepository evenementRepository;

    public List<Regio> alle() {
        return repository.findAllByOrderByNaamAsc();
    }

    public Regio get(Long id) {
        return repository.findById(id).orElseThrow(() -> new NietGevondenException("Regio niet gevonden"));
    }

    /** Alle regio's met het aantal actieve ondernemingen erin. */
    public Map<Regio, Long> metAantalOndernemingen() {
        Map<Regio, Long> resultaat = new LinkedHashMap<>();
        for (Regio regio : alle()) {
            resultaat.put(regio, ondernemingRepository.countByRegioAndStatus(regio, OndernemingStatus.ACTIEF));
        }
        return resultaat;
    }

    @Transactional
    public Regio maakAan(RegioForm form) {
        String naam = form.getNaam().trim();
        if (repository.existsByNaamIgnoreCase(naam)) {
            throw new BedrijfsregelException("Er bestaat al een regio met de naam '" + naam + "'.");
        }
        return repository.save(new Regio(naam, leegNaarNull(form.getBeschrijving())));
    }

    @Transactional
    public void bijwerken(Long id, RegioForm form) {
        Regio regio = get(id);
        String naam = form.getNaam().trim();
        if (!regio.getNaam().equalsIgnoreCase(naam) && repository.existsByNaamIgnoreCase(naam)) {
            throw new BedrijfsregelException("Er bestaat al een regio met de naam '" + naam + "'.");
        }
        regio.setNaam(naam);
        regio.setBeschrijving(leegNaarNull(form.getBeschrijving()));
    }

    /** Per regio wat er nog aan gekoppeld is, voor het beheerscherm. */
    public List<Overzicht> overzicht() {
        return alle().stream().map(regio -> {
            List<Onderneming> ondernemingen = ondernemingRepository.findByRegio(regio);
            long actief = ondernemingen.stream().filter(Onderneming::isActief).count();
            return new Overzicht(regio, actief, ondernemingen.size() - actief, evenementRepository.countByRegio(regio));
        }).toList();
    }

    /**
     * Verwijdert een regio. Dat kan enkel als er geen actieve ondernemingen meer in zitten.
     * Niet-actieve ondernemingen (in afwachting of geblokkeerd) en evenementen worden verplaatst
     * naar de opgegeven regio.
     */
    @Transactional
    public void verwijderen(Long id, Long verplaatsNaarId) {
        Regio regio = get(id);
        List<Onderneming> ondernemingen = ondernemingRepository.findByRegio(regio);
        if (ondernemingen.stream().anyMatch(Onderneming::isActief)) {
            throw new BedrijfsregelException("De regio '" + regio.getNaam()
                    + "' kan niet verwijderd worden omdat er nog actieve ondernemingen in zitten.");
        }
        List<Evenement> evenementen = evenementRepository.findByRegio(regio);
        if (!ondernemingen.isEmpty() || !evenementen.isEmpty()) {
            if (verplaatsNaarId == null || verplaatsNaarId.equals(id)) {
                throw new BedrijfsregelException("Kies naar welke regio de niet-actieve ondernemingen en evenementen van '"
                        + regio.getNaam() + "' verplaatst moeten worden.");
            }
            Regio doel = get(verplaatsNaarId);
            ondernemingen.forEach(o -> o.setRegio(doel));
            evenementen.forEach(e -> e.setRegio(doel));
            ondernemingRepository.flush();
            evenementRepository.flush();
        }
        repository.delete(regio);
    }

    @Getter
    public static class Overzicht {
        private final Regio regio;
        private final long actief;
        private final long nietActief;
        private final long evenementen;

        Overzicht(Regio regio, long actief, long nietActief, long evenementen) {
            this.regio = regio;
            this.actief = actief;
            this.nietActief = nietActief;
            this.evenementen = evenementen;
        }

        public boolean isVerwijderbaar() {
            return actief == 0;
        }

        public boolean isVerplaatsingNodig() {
            return nietActief > 0 || evenementen > 0;
        }
    }

    static String leegNaarNull(String waarde) {
        return waarde == null || waarde.isBlank() ? null : waarde.trim();
    }
}
