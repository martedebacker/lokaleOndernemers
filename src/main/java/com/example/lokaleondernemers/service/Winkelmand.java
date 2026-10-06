package com.example.lokaleondernemers.service;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Winkelmand van de huidige bezoeker, bewaard in de sessie (productId → aantal). */
@Component
@SessionScope
public class Winkelmand implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Map<Long, Integer> items = new LinkedHashMap<>();

    public synchronized void zetAantal(Long productId, int aantal) {
        if (aantal <= 0) {
            items.remove(productId);
        } else {
            items.put(productId, aantal);
        }
    }

    public synchronized int getAantal(Long productId) {
        return items.getOrDefault(productId, 0);
    }

    public synchronized void verwijder(Long productId) {
        items.remove(productId);
    }

    public synchronized void leegmaken() {
        items.clear();
    }

    public synchronized Map<Long, Integer> getItems() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(items));
    }

    public synchronized int getAantalStuks() {
        return items.values().stream().mapToInt(Integer::intValue).sum();
    }

    public synchronized boolean isLeeg() {
        return items.isEmpty();
    }
}
