package com.bank.ata.orchestrator.config;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * JPA configuration for the orchestrator.
 *
 * In Spring Boot 4.x, @EntityScan was removed. To scan entities from external
 * modules (ata-audit), we configure LocalContainerEntityManagerFactoryBean
 * with explicit packages to scan.
 */
@Configuration
public class JpaConfig {

    @Value("${spring.jpa.hibernate.ddl-auto:update}")
    private String ddlAuto;

    @Value("${spring.jpa.database-platform:}")
    private String databasePlatform;

    /**
     * Configure EntityManagerFactory to scan entity packages from ata-audit module.
     */
    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.bank.ata.audit.entity");
        em.setPersistenceUnitName("default");
        em.setPersistenceProviderClass(HibernatePersistenceProvider.class);

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);

        // Apply Hibernate properties
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", ddlAuto);
        if (databasePlatform != null && !databasePlatform.isEmpty()) {
            properties.put("hibernate.dialect", databasePlatform);
        }
        em.setJpaPropertyMap(properties);

        return em;
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
