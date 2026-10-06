package com.example.lokaleondernemers.service;

import com.example.lokaleondernemers.dto.OndernemingForm;
import com.example.lokaleondernemers.model.Afbeelding;
import com.example.lokaleondernemers.model.Gebruiker;
import com.example.lokaleondernemers.model.Onderneming;
import com.example.lokaleondernemers.model.OndernemingStatus;
import com.example.lokaleondernemers.model.OpstarthulpStatus;
import com.example.lokaleondernemers.model.Regio;
import com.example.lokaleondernemers.model.Rol;
import com.example.lokaleondernemers.repository.OndernemingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Optional;

import static com.example.lokaleondernemers.service.RegioService.leegNaarNull;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OndernemingService {

    private final OndernemingRepository repository;
    private final RegioService regioService;
    private final AfbeeldingService afbeeldingService;

    public Optional<Onderneming> vanEigenaar(Gebruiker eigenaar) {
        return repository.findByEigenaar(eigenaar);
    }

    public Onderneming vanEigenaarVerplicht(Gebruiker eigenaar) {
        return vanEigenaar(eigenaar)
                .orElseThrow(() -> new NietGevondenException("Er is geen onderneming gekoppeld aan je account."));
    }

    public Onderneming get(Long id) {
        return repository.findById(id).orElseThrow(() -> new NietGevondenException("Onderneming niet gevonden"));
    }

    /**
     * De publieke winkelpagina. Niet-actieve ondernemingen zijn enkel zichtbaar voor de eigenaar
     * (als voorbeeld) en voor beheerders.
     */
    public Onderneming getWinkel(String slug, Gebruiker bezoeker) {
        Onderneming o = repository.findBySlug(slug)
                .orElseThrow(() -> new NietGevondenException("Deze winkel bestaat niet"));
        boolean magVoorbeeldZien = bezoeker != null
                && (bezoeker.getRol() == Rol.ADMIN || o.getEigenaar().getId().equals(bezoeker.getId()));
        if (!o.isActief() && !magVoorbeeldZien) {
            throw new NietGevondenException("Deze winkel bestaat niet");
        }
        return o;
    }

    public List<Onderneming> zoekActief(Long regioId, String zoekterm) {
        Regio regio = regioId == null ? null : regioService.get(regioId);
        return repository.zoek(OndernemingStatus.ACTIEF, regio, leegNaarNull(zoekterm));
    }

    public List<Onderneming> alle() {
        return repository.findAllByOrderByAangemaaktOpDesc();
    }

    public long aantalMetStatus(OndernemingStatus status) {
        return repository.countByStatus(status);
    }

    @Transactional
    public Onderneming maakAan(Gebruiker eigenaar, String naam, Long regioId, String gemeente,
                               String postcode, String straat) {
        Onderneming o = new Onderneming();
        o.setEigenaar(eigenaar);
        o.setNaam(naam.trim());
        o.setSlug(uniekeSlug(naam));
        o.setRegio(regioService.get(regioId));
        o.setGemeente(gemeente.trim());
        o.setPostcode(leegNaarNull(postcode));
        o.setStraat(leegNaarNull(straat));
        o.setTelefoon(eigenaar.getTelefoon());
        o.setEmail(eigenaar.getEmail());
        return repository.save(o);
    }

    @Transactional
    public void bijwerken(Long ondernemingId, OndernemingForm form) {
        Onderneming o = get(ondernemingId);
        o.setNaam(form.getNaam().trim());
        o.setSlogan(leegNaarNull(form.getSlogan()));
        o.setBeschrijving(leegNaarNull(form.getBeschrijving()));
        o.setStraat(leegNaarNull(form.getStraat()));
        o.setPostcode(leegNaarNull(form.getPostcode()));
        o.setGemeente(form.getGemeente().trim());
        o.setRegio(regioService.get(form.getRegioId()));
        o.setTelefoon(leegNaarNull(form.getTelefoon()));
        o.setEmail(leegNaarNull(form.getEmail()));
        o.setOphaalInfo(leegNaarNull(form.getOphaalInfo()));
        o.setThemaKleur(form.getThemaKleur() == null ? Onderneming.STANDAARD_HOOFDKLEUR : form.getThemaKleur());
        // Standaardkleuren bewaren we als null, zodat we zien of iemand al een eigen palet koos.
        o.setAccentKleur(Onderneming.STANDAARD_ACCENTKLEUR.equalsIgnoreCase(form.getAccentKleur()) ? null : form.getAccentKleur());
        o.setAchtergrondKleur(Onderneming.STANDAARD_ACHTERGRONDKLEUR.equalsIgnoreCase(form.getAchtergrondKleur()) ? null : form.getAchtergrondKleur());
        o.setToonAdres(form.isToonAdres());
        o.setToonTelefoon(form.isToonTelefoon());
        o.setToonEmail(form.isToonEmail());
        o.setToonOphaalInfo(form.isToonOphaalInfo());
        o.setToonVoorraad(form.isToonVoorraad());
        o.setToonEvenementen(form.isToonEvenementen());

        Afbeelding logo = afbeeldingService.vanUpload(form.getLogo());
        if (logo != null) {
            o.setLogo(logo);
        } else if (form.isLogoVerwijderen()) {
            o.setLogo(null);
        }
        Afbeelding banner = afbeeldingService.vanUpload(form.getBanner());
        if (banner != null) {
            o.setBanner(banner);
        } else if (form.isBannerVerwijderen()) {
            o.setBanner(null);
        }
    }

    /** De ondernemer vraagt (bij registratie of later) dat wij de winkelpagina inrichten. */
    @Transactional
    public void vraagOpstarthulpAan(Long id, String wensen) {
        Onderneming o = get(id);
        if (o.getOpstarthulp() != null) {
            throw new BedrijfsregelException("Je hebt de opstarthulp al aangevraagd.");
        }
        o.setOpstarthulp(OpstarthulpStatus.AANGEVRAAGD);
        o.setOpstartWensen(leegNaarNull(wensen));
        o.setOpstarthulpAangevraagdOp(java.time.LocalDateTime.now());
    }

    @Transactional
    public Onderneming wijzigOpstarthulp(Long id, OpstarthulpStatus status) {
        Onderneming o = get(id);
        if (o.getOpstarthulp() == null) {
            throw new BedrijfsregelException("Deze onderneming heeft geen opstarthulp aangevraagd.");
        }
        o.setOpstarthulp(status);
        return o;
    }

    @Transactional
    public Onderneming wijzigStatus(Long id, OndernemingStatus status) {
        Onderneming o = get(id);
        o.setStatus(status);
        return o;
    }

    private String uniekeSlug(String naam) {
        String basis = Normalizer.normalize(naam, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (basis.isEmpty()) {
            basis = "winkel";
        }
        String slug = basis;
        int volgnummer = 2;
        while (repository.existsBySlug(slug)) {
            slug = basis + "-" + volgnummer++;
        }
        return slug;
    }
}
