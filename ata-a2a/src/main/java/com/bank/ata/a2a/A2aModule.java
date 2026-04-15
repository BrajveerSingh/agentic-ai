package com.bank.ata.a2a;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration entry-point for the ata-a2a module.
 *
 * <p>Activates component scanning for all sub-packages:
 * {@code client}, {@code server}, {@code orchestration}, and {@code mock}.</p>
 */
@Configuration
@ComponentScan("com.bank.ata.a2a")
public class A2aModule {
}
