package com.example.lokaleondernemers.service;

import com.example.lokaleondernemers.model.Afbeelding;
import com.example.lokaleondernemers.repository.AfbeeldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AfbeeldingService {

    private static final Set<String> TOEGELATEN_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final AfbeeldingRepository repository;

    /** Maakt een (nog niet opgeslagen) afbeelding van een upload, of null als er niets geüpload werd. */
    public Afbeelding vanUpload(MultipartFile bestand) {
        if (bestand == null || bestand.isEmpty()) {
            return null;
        }
        String type = bestand.getContentType();
        if (type == null || !TOEGELATEN_TYPES.contains(type.toLowerCase())) {
            throw new BedrijfsregelException("Enkel JPG-, PNG-, WebP- of GIF-afbeeldingen zijn toegelaten.");
        }
        try {
            return new Afbeelding(type.toLowerCase(), bestand.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Transactional(readOnly = true)
    public Afbeelding get(Long id) {
        Afbeelding afbeelding = repository.findById(id)
                .orElseThrow(() -> new NietGevondenException("Afbeelding niet gevonden"));
        afbeelding.getData(); // lazy data laden binnen de transactie
        return afbeelding;
    }
}
