package com.gabrielpacheco.atendente.config;

import com.gabrielpacheco.atendente.adapter.out.persistence.RetentionJob;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class PersistenceConfig {

    @Bean
    @ConditionalOnProperty(name = "app.retention.enabled", havingValue = "true", matchIfMissing = true)
    RetentionJob retentionJob(JdbcClient jdbc, Clock clock, AppProperties app) {
        AppProperties.Retention retention = app.retention();
        return new RetentionJob(jdbc, clock, new RetentionJob.Windows(retention.processedMessages(),
                retention.webReplies(), retention.webCustomers(), retention.whatsappCustomers()));
    }
}
