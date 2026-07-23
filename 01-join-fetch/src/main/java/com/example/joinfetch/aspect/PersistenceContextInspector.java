package com.example.joinfetch.aspect;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.stat.SessionStatistics;
import org.hibernate.stat.Statistics;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class PersistenceContextInspector {

    private final EntityManager entityManager;
    private final EntityManagerFactory entityManagerFactory;

    public void reset() {
        // Xóa entity còn sót lại trong Session hiện tại
        entityManager.clear();

        // Xóa global counters
        getStatistics().clear();
    }

    public PersistenceContextProof capture(
            List<?> result
    ) {
        Statistics statistics = getStatistics();

        Session session = entityManager.unwrap(Session.class);
        SessionStatistics sessionStatistics =
                session.getStatistics();

        Map<String, Long> entityLoadsByType =
                new LinkedHashMap<>();

        Arrays.stream(statistics.getEntityNames())
                .forEach(entityName -> {
                    long loadCount = statistics
                            .getEntityStatistics(entityName)
                            .getLoadCount();

                    if (loadCount > 0) {
                        entityLoadsByType.put(
                                entityName,
                                loadCount
                        );
                    }
                });

        Object firstResult = result.isEmpty()
                ? null
                : result.get(0);

        String returnedObjectClass = firstResult == null
                ? null
                : firstResult.getClass().getName();

        /*
         * Chỉ gọi contains() khi object thực sự là entity.
         * Không nên truyền projection proxy trực tiếp vào contains().
         */
        Boolean returnedObjectManaged =
                firstResult instanceof com.example.joinfetch.entity.Author
                        ? entityManager.contains(firstResult)
                        : null;

        List<String> managedEntityKeys =
                sessionStatistics.getEntityKeys()
                        .stream()
                        .map(Object::toString)
                        .limit(30)
                        .toList();

        List<String> managedCollectionKeys =
                sessionStatistics.getCollectionKeys()
                        .stream()
                        .map(Object::toString)
                        .limit(30)
                        .toList();

        return new PersistenceContextProof(
                result.size(),
                returnedObjectClass,
//                returnedObjectManaged,
                statistics.getEntityLoadCount(),
                entityLoadsByType
//                sessionStatistics.getEntityCount(),
//                sessionStatistics.getCollectionCount(),
//                managedEntityKeys,
//                managedCollectionKeys
        );
    }

    private Statistics getStatistics() {
        return entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();
    }

    public PersistenceContextProof capture(
            Object result
    ) {
        List<?> returnedObjects;

        if (result instanceof Page<?> page) {
            returnedObjects = page.getContent();
        } else if (result instanceof List<?> list) {
            returnedObjects = list;
        } else if (result instanceof Optional<?> optional) {
            returnedObjects = optional.stream().toList();
        } else if (result == null) {
            returnedObjects = List.of();
        } else {
            returnedObjects = List.of(result);
        }

        return capture(returnedObjects);
    }
}

