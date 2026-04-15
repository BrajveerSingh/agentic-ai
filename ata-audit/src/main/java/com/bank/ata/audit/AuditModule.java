package com.bank.ata.audit;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Audit module configuration.
 * Enables JPA repositories and AOP for audit interception.
 *
 * NOTE: @EntityScan is intentionally absent here — this is a library module,
 * not a Spring Boot application. Entity scanning is performed by Spring Boot
 * auto-configuration in the ata-app module when entities are on the classpath.
 */
@Configuration
@ComponentScan(basePackages = "com.bank.ata.audit")
@EnableJpaRepositories(basePackages = "com.bank.ata.audit.repository")
@EnableAspectJAutoProxy
public class AuditModule {
    // Configuration for audit trail components
}
