package com.example.lokaleondernemers.service;

import com.example.lokaleondernemers.dto.OndernemerRegistratieForm;
import com.example.lokaleondernemers.dto.ProfielForm;
import com.example.lokaleondernemers.dto.RegistratieForm;
import com.example.lokaleondernemers.model.Gebruiker;
import com.example.lokaleondernemers.model.Rol;
import com.example.lokaleondernemers.repository.GebruikerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.example.lokaleondernemers.service.RegioService.leegNaarNull;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GebruikerService {

    private final GebruikerRepository repository;
    private final OndernemingService ondernemingService;
    private final PasswordEncoder passwordEncoder;

    public Optional<Gebruiker> zoekOpEmail(String email) {
        return repository.findByEmailIgnoreCase(email);
    }

    public Gebruiker getOpEmail(String email) {
        return zoekOpEmail(email).orElseThrow(() -> new NietGevondenException("Gebruiker niet gevonden"));
    }

    public Gebruiker get(Long id) {
        return repository.findById(id).orElseThrow(() -> new NietGevondenException("Gebruiker niet gevonden"));
    }

    public List<Gebruiker> alle() {
        return repository.findAllByOrderByAangemaaktOpDesc();
    }

    public long aantalMetRol(Rol rol) {
        return repository.countByRol(rol);
    }

    @Transactional
    public Gebruiker registreerKlant(RegistratieForm form) {
        return repository.save(nieuweGebruiker(form, Rol.KLANT));
    }

    /** Maakt een ondernemersaccount én een onderneming aan, die nog door een beheerder goedgekeurd moet worden. */
    @Transactional
    public Gebruiker registreerOndernemer(OndernemerRegistratieForm form) {
        Gebruiker gebruiker = repository.save(nieuweGebruiker(form, Rol.ONDERNEMER));
        var onderneming = ondernemingService.maakAan(gebruiker, form.getOndernemingNaam(), form.getRegioId(),
                form.getGemeente(), form.getPostcode(), form.getStraat());
        if (form.isOpstarthulp()) {
            ondernemingService.vraagOpstarthulpAan(onderneming.getId(), form.getOpstartWensen());
        }
        return gebruiker;
    }

    @Transactional
    public Gebruiker maakAan(String voornaam, String achternaam, String email, String wachtwoord, Rol rol) {
        return repository.save(new Gebruiker(voornaam, achternaam, email.toLowerCase(),
                passwordEncoder.encode(wachtwoord), rol));
    }

    @Transactional
    public void profielBijwerken(Long gebruikerId, ProfielForm form) {
        Gebruiker g = get(gebruikerId);
        if (form.getNieuwWachtwoord() != null && !form.getNieuwWachtwoord().isBlank()) {
            if (form.getHuidigWachtwoord() == null
                    || !passwordEncoder.matches(form.getHuidigWachtwoord(), g.getWachtwoord())) {
                throw new BedrijfsregelException("Je huidige wachtwoord is niet correct.");
            }
            if (form.getNieuwWachtwoord().length() < 8) {
                throw new BedrijfsregelException("Je nieuwe wachtwoord moet minstens 8 tekens lang zijn.");
            }
            g.setWachtwoord(passwordEncoder.encode(form.getNieuwWachtwoord()));
        }
        g.setVoornaam(form.getVoornaam().trim());
        g.setAchternaam(form.getAchternaam().trim());
        g.setTelefoon(leegNaarNull(form.getTelefoon()));
    }

    @Transactional
    public void zetActief(Long id, boolean actief, Gebruiker beheerder) {
        Gebruiker g = get(id);
        if (g.getId().equals(beheerder.getId())) {
            throw new BedrijfsregelException("Je kan je eigen account niet blokkeren.");
        }
        g.setActief(actief);
    }

    /** Beheerders kunnen klanten tot beheerder maken en omgekeerd. Ondernemers behouden hun rol. */
    @Transactional
    public void wijzigRol(Long id, Rol rol, Gebruiker beheerder) {
        Gebruiker g = get(id);
        if (g.getId().equals(beheerder.getId())) {
            throw new BedrijfsregelException("Je kan je eigen rol niet wijzigen.");
        }
        if (g.getRol() == Rol.ONDERNEMER || rol == Rol.ONDERNEMER) {
            throw new BedrijfsregelException("De rol van ondernemers wordt bepaald door hun registratie.");
        }
        g.setRol(rol);
    }

    private Gebruiker nieuweGebruiker(RegistratieForm form, Rol rol) {
        String email = form.getEmail().trim().toLowerCase();
        if (repository.existsByEmailIgnoreCase(email)) {
            throw new BedrijfsregelException("Er bestaat al een account met dit e-mailadres.");
        }
        if (!form.getWachtwoord().equals(form.getWachtwoordHerhaling())) {
            throw new BedrijfsregelException("De wachtwoorden komen niet overeen.");
        }
        Gebruiker g = new Gebruiker(form.getVoornaam().trim(), form.getAchternaam().trim(), email,
                passwordEncoder.encode(form.getWachtwoord()), rol);
        g.setTelefoon(leegNaarNull(form.getTelefoon()));
        return g;
    }
}
