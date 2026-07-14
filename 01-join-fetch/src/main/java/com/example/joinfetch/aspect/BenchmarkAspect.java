package com.example.joinfetch.aspect;

import com.example.joinfetch.dto.record.BenchmarkSnapshot;
import com.example.joinfetch.dto.record.Response;
import com.example.joinfetch.scenario.BenchmarkScenario;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.List;

@Aspect
@Component
@Order(0)
@RequiredArgsConstructor
public class BenchmarkAspect {

    private static final double BYTES_PER_MB =
            1024.0 * 1024.0;

    private final EntityManagerFactory entityManagerFactory;

    @Around("@annotation(scenario)")
    public Object benchmark(
            ProceedingJoinPoint joinPoint,
            BenchmarkScenario scenario
    ) throws Throwable {

        Statistics statistics = getStatistics();

        statistics.clear();
        CollectingQueryListener.getAndClear();

        ThreadMXBean threadMxBean =
                ManagementFactory.getThreadMXBean();

        enableThreadCpuTiming(threadMxBean);

        BenchmarkSnapshot snapshot = new BenchmarkSnapshot(
                System.nanoTime(),
                usedHeapMb(),
                gcCount(),
                currentThreadCpuNanos(threadMxBean)
        );

        try {
            Object returnedValue = joinPoint.proceed();

            if (!(returnedValue instanceof Response<?> response)) {
                throw new IllegalStateException(
                        "@BenchmarkScenario method must return Response<?>: "
                                + joinPoint.getSignature()
                );
            }

            double executionTimeMs =
                    nanosToMillis(
                            System.nanoTime()
                                    - snapshot.startedAtNanos()
                    );

            double cpuTimeMs =
                    nanosToMillis(
                            currentThreadCpuNanos(threadMxBean)
                                    - snapshot.cpuBeforeNanos()
                    );

            double heapDeltaMb =
                    usedHeapMb() - snapshot.heapBeforeMb();

            long gcCountDelta =
                    gcCount() - snapshot.gcBefore();

            List<String> ormQueries =
                    Arrays.asList(statistics.getQueries());

            List<String> sqlStatements =
                    CollectingQueryListener.getAndClear();

            return response.withBenchmark(
                    scenario.value(),
                    statistics.getQueryExecutionCount(),
                    ormQueries,
                    sqlStatements.size(),
                    statistics.getPrepareStatementCount(),
                    sqlStatements,
                    executionTimeMs,
                    cpuTimeMs,
                    heapDeltaMb,
                    gcCountDelta
            );
        } catch (Throwable throwable) {
            CollectingQueryListener.getAndClear();
            throw throwable;
        }
    }

    private Statistics getStatistics() {
        return entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();
    }

    private double usedHeapMb() {
        Runtime runtime = Runtime.getRuntime();

        return (runtime.totalMemory() - runtime.freeMemory())
                / BYTES_PER_MB;
    }

    private long gcCount() {
        long total = 0;

        for (GarbageCollectorMXBean bean :
                ManagementFactory.getGarbageCollectorMXBeans()) {

            long count = bean.getCollectionCount();

            if (count > 0) {
                total += count;
            }
        }

        return total;
    }

    private void enableThreadCpuTiming(
            ThreadMXBean threadMxBean
    ) {
        if (threadMxBean.isThreadCpuTimeSupported()
                && !threadMxBean.isThreadCpuTimeEnabled()) {

            threadMxBean.setThreadCpuTimeEnabled(true);
        }
    }

    private long currentThreadCpuNanos(
            ThreadMXBean threadMxBean
    ) {
        if (!threadMxBean.isCurrentThreadCpuTimeSupported()
                || !threadMxBean.isThreadCpuTimeEnabled()) {
            return 0;
        }

        return threadMxBean.getCurrentThreadCpuTime();
    }

    private double nanosToMillis(long nanos) {
        return Math.max(0, nanos / 1_000_000.0);
    }
}