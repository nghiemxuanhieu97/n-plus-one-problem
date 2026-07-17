package com.example.entitygraph.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
public class DemoMetrics {

    private final EntityManager entityManager;
    private final EntityManagerFactory entityManagerFactory;

    public void measure(String label, Runnable action) {
        measure(label, () -> {
            action.run();
            return null;
        });
    }

    public <T> T measure(String label, Supplier<T> action) {
        entityManager.clear();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        long startedAt = System.nanoTime();

        try {
            return action.get();
        } finally {
            long elapsedNanos = System.nanoTime() - startedAt;

            log.info("");
            log.info("  [METRICS] {}", label);
            log.info("      elapsed:              {} ms", elapsedNanos / 1_000_000);
            log.info("      JDBC statements:      {}", statistics.getPrepareStatementCount());
            log.info("      entities loaded:      {}", statistics.getEntityLoadCount());
            log.info("      collections loaded:   {}", statistics.getCollectionLoadCount());
            log.info("      collections fetched:  {}", statistics.getCollectionFetchCount());
        }
    }
}
