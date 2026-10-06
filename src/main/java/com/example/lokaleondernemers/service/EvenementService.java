package com.example.lokaleondernemers.service;

import com.example.lokaleondernemers.dto.EvenementForm;
import com.example.lokaleondernemers.model.Afbeelding;
import com.example.lokaleondernemers.model.Evenement;
import com.example.lokaleondernemers.model.Gebruiker;
import com.example.lokaleondernemers.model.Onderneming;
import com.example.lokaleondernemers.model.OndernemingStatus;
import com.example.lokaleondernemers.model.Regio;
import com.example.lokaleondernemers.model.Rol;
import com.example.lokaleondernemers.repository.EvenementRepository;
import com.example.lokaleondernemers.repository.OndernemingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static com.example.lokaleondernemers.service.RegioService.leegNaarNull;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvenementService {

    private final EvenementRepository repository;
    private final OndernemingRepository ondernemingRepository;
    private final RegioService regioService;
    private final AfbeeldingService afbeeldingService;

    // ---- publiek ----

    public List<Evenement> komende(Regio regio, int max) {
        return repository.komende(OndernemingStatus.ACTIEF, regio, LocalDateTime.now(), PageRequest.of(0, max));
    }

    public List<Evenement> komendeVan(Onderneming onderneming) {
        return repository.komendeVan(OndernemingStatus.ACTIEF, onderneming, LocalDateTime.now());
    }

    /** Een evenement van een niet-actieve organisator is enkel zichtbaar voor de betrokkenen zelf en beheerders. */
    public Evenement getPubliek(Long id, Gebruiker bezoeker) {
        Evenement e = get(id);
        boolean zichtbaar = e.isDoorPlatform() || e.getOrganisator().isActief();
        boolean magVoorbeeldZien = bezoeker != null && (bezoeker.getRol() == Rol.ADMIN
                || (!e.isDoorPlatform() && e.getOrganisator().getEigenaar().getId().equals(bezoeker.getId())));
        if (!zichtbaar && !magVoorbeeldZien) {
            throw new NietGevondenException("Evenement niet gevonden");
        }
        return e;
    }

    // ---- ondernemer ----

    public List<Evenement> allesVan(Onderneming onderneming) {
        return repository.allesVan(onderneming);
    }

    /** Enkel de organiserende onderneming mag een evenement wijzigen of verwijderen. */
    public Evenement getAlsOrganisator(Long id, Onderneming onderneming) {
        Evenement e = get(id);
        if (e.isDoorPlatform() || !e.getOrganisator().getId().equals(onderneming.getId())) {
            throw new AccessDeniedException("Enkel de organisator kan dit evenement beheren.");
        }
        return e;
    }

    @Transactional
    public Evenement opslaanAlsOnderneming(Onderneming onderneming, Long id, EvenementForm form) {
        Evenement e = id == null ? new Evenement() : getAlsOrganisator(id, onderneming);
        if (id == null) {
            e.setOrganisator(onderneming);
        }
        vulIn(e, form, onderneming);
        return repository.save(e);
    }

    @Transactional
    public void verwijderenAlsOnderneming(Long id, Onderneming onderneming) {
        repository.delete(getAlsOrganisator(id, onderneming));
    }

    /** Een deelnemende (niet-organiserende) onderneming kan zich terugtrekken uit een evenement. */
    @Transactional
    public void uitstappen(Long id, Onderneming onderneming) {
        Evenement e = get(id);
        if (!e.getOndernemingen().removeIf(o -> o.getId().equals(onderneming.getId()))) {
            throw new BedrijfsregelException("Je onderneming neemt niet deel aan dit evenement.");
        }
    }

    // ---- beheerder ----

    public List<Evenement> alle() {
        return repository.findAllByOrderByStartMomentDesc();
    }

    public Evenement get(Long id) {
        return repository.findById(id).orElseThrow(() -> new NietGevondenException("Evenement niet gevonden"));
    }

    /** Beheerders maken evenementen van het platform aan en kunnen elk evenement aanpassen. */
    @Transactional
    public Evenement opslaanAlsBeheerder(Long id, EvenementForm form) {
        Evenement e = id == null ? new Evenement() : get(id);
        vulIn(e, form, e.getOrganisator());
        return repository.save(e);
    }

    @Transactional
    public void verwijderen(Long id) {
        repository.delete(get(id));
    }

    /** Ondernemingen die gekozen kunnen worden als deelnemer. */
    public List<Onderneming> kiesbareOndernemingen(Onderneming behalve) {
        return ondernemingRepository.findByStatusOrderByNaamAsc(OndernemingStatus.ACTIEF).stream()
                .filter(o -> behalve == null || !o.getId().equals(behalve.getId()))
                .toList();
    }

    // ---- intern ----

    private void vulIn(Evenement e, EvenementForm form, Onderneming organisator) {
        if (form.getEindMoment() != null && form.getEindMoment().isBefore(form.getStartMoment())) {
            throw new BedrijfsregelException("Het einde van het evenement ligt vóór het begin.");
        }
        e.setTitel(form.getTitel().trim());
        e.setBeschrijving(leegNaarNull(form.getBeschrijving()));
        e.setStartMoment(form.getStartMoment());
        e.setEindMoment(form.getEindMoment());
        e.setLocatie(form.getLocatie().trim());
        e.setGemeente(form.getGemeente().trim());
        e.setRegio(regioService.get(form.getRegioId()));
        e.setPrijsInfo(leegNaarNull(form.getPrijsInfo()));

        // Bestaande deelnemers die intussen niet meer actief zijn, blijven behouden;
        // nieuwe deelnemers moeten actieve ondernemingen zijn.
        List<Long> gekozen = form.getOndernemingIds() == null ? List.of() : form.getOndernemingIds();
        e.getOndernemingen().removeIf(o -> !gekozen.contains(o.getId()));
        for (Long ondernemingId : gekozen) {
            if (organisator != null && Objects.equals(ondernemingId, organisator.getId())) {
                continue;
            }
            if (e.getOndernemingen().stream().noneMatch(o -> o.getId().equals(ondernemingId))) {
                ondernemingRepository.findById(ondernemingId)
                        .filter(Onderneming::isActief)
                        .ifPresent(o -> e.getOndernemingen().add(o));
            }
        }

        Afbeelding afbeelding = afbeeldingService.vanUpload(form.getAfbeelding());
        if (afbeelding != null) {
            e.setAfbeelding(afbeelding);
        } else if (form.isAfbeeldingVerwijderen()) {
            e.setAfbeelding(null);
        }
    }
}
