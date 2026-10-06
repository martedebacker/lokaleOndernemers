package com.example.lokaleondernemers.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Verstuurt e-mails pas nadat de bestelling echt opgeslagen is, en op de achtergrond:
 * een trage of falende mailserver mag een bestelling nooit tegenhouden.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BestellingMailListener {

    private final MailService mailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void bijBestelling(BestellingMailEvent event) {
        try {
            mailService.verstuur(event);
        } catch (RuntimeException e) {
            log.error("E-mail voor bestelling(en) {} kon niet verstuurd worden", event.bestellingIds(), e);
        }
    }
}
