package com.example.lokaleondernemers.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Laat e-mails op de achtergrond versturen (@Async). */
@Configuration
@EnableAsync
public class AsyncConfig {
}
