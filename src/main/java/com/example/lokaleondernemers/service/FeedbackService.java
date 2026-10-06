package com.example.lokaleondernemers.service;

import com.example.lokaleondernemers.dto.FeedbackForm;
import com.example.lokaleondernemers.model.Feedback;
import com.example.lokaleondernemers.model.Gebruiker;
import com.example.lokaleondernemers.model.Onderneming;
import com.example.lokaleondernemers.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

import static com.example.lokaleondernemers.service.RegioService.leegNaarNull;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackService {

    private final FeedbackRepository repository;

    public List<Feedback> vanOnderneming(Onderneming onderneming) {
        return repository.findByOndernemingOrderByAangemaaktOpDesc(onderneming);
    }

    public List<Feedback> alle(boolean enkelOpen) {
        return enkelOpen
                ? repository.findByStatusInOrderByAangemaaktOpDesc(EnumSet.of(Feedback.Status.NIEUW, Feedback.Status.IN_BEHANDELING))
                : repository.findAllByOrderByAangemaaktOpDesc();
    }

    public long aantalNieuw() {
        return repository.countByStatus(Feedback.Status.NIEUW);
    }

    @Transactional
    public Feedback geef(Onderneming onderneming, Gebruiker afzender, FeedbackForm form) {
        Feedback f = new Feedback();
        f.setOnderneming(onderneming);
        f.setAfzender(afzender);
        f.setSoort(form.getSoort());
        f.setOnderwerp(form.getOnderwerp().trim());
        f.setBericht(form.getBericht().trim());
        f.setTevredenheid(form.getTevredenheid());
        return repository.save(f);
    }

    /** Een beheerder antwoordt en/of wijzigt de status. */
    @Transactional
    public void behandel(Long id, Feedback.Status status, String antwoord) {
        Feedback f = repository.findById(id).orElseThrow(() -> new NietGevondenException("Feedback niet gevonden"));
        String nieuwAntwoord = leegNaarNull(antwoord);
        if (nieuwAntwoord != null && !nieuwAntwoord.equals(f.getAntwoord())) {
            f.setAntwoord(nieuwAntwoord);
            f.setBeantwoordOp(LocalDateTime.now());
            if (status == null || status == Feedback.Status.NIEUW) {
                status = Feedback.Status.IN_BEHANDELING;
            }
        }
        if (status != null) {
            f.setStatus(status);
        }
    }
}
