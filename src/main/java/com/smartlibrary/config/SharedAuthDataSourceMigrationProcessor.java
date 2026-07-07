package com.smartlibrary.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class SharedAuthDataSourceMigrationProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(SharedAuthDataSourceMigrationProcessor.class);
    private static final AtomicBoolean MIGRATED = new AtomicBoolean(false);

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!(bean instanceof DataSource) || !MIGRATED.compareAndSet(false, true)) {
            return bean;
        }
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(new ClassPathResource("db/phase3-shared-auth.sql"));
            populator.addScript(new ClassPathResource("db/pre-jpa-schema-fixes.sql"));
            populator.setContinueOnError(true);
            populator.execute((DataSource) bean);
            log.info("Applied shared auth and pre-JPA schema fixes");
        } catch (Exception ex) {
            log.warn("Phase 3 schema migration skipped or partially applied: {}", ex.getMessage());
        }
        return bean;
    }
}
