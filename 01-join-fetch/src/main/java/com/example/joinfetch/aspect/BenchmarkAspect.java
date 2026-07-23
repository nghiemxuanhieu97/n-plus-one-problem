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
    private final PersistenceContextInspector inspector;

    private final EntityManagerFactory entityManagerFactory;

    @Around("@annotation(scenario)")
    public Object benchmark(
            ProceedingJoinPoint joinPoint,
            BenchmarkScenario scenario
    ) throws Throwable {

        Statistics statistics = getStatistics();

        statistics.clear();
        inspector.reset();

        // Bắt đầu thu SQL ở tầng JDBC.
        CollectingP6SpyLogger.begin();

        /*
         * Bean tiêu chuẩn dùng để đo CPU time
         * của request thread.
         */
        ThreadMXBean cpuMxBean =
                ManagementFactory.getThreadMXBean();

        enableThreadCpuTiming(cpuMxBean);

        /*
         * Bean mở rộng của JVM dùng để đo tổng số byte
         * được cấp phát bởi request thread.
         */
        com.sun.management.ThreadMXBean allocationMxBean =
                getAllocationMxBean();

        enableThreadAllocationTracking(allocationMxBean);

        BenchmarkSnapshot snapshot = new BenchmarkSnapshot(
                System.nanoTime(),
                gcCount(),
                currentThreadCpuNanos(cpuMxBean),
                currentThreadAllocatedBytes(allocationMxBean)
        );

        try {
            Object returnedValue = joinPoint.proceed();

            if (!(returnedValue instanceof Response<?> response)) {
                throw new IllegalStateException(
                        "@BenchmarkScenario method must return Response<?>: "
                                + joinPoint.getSignature()
                );
            }

            double executionTimeMs = nanosToMillis(
                    System.nanoTime()
                            - snapshot.startedAtNanos()
            );

            var persistenceContextProof = inspector.capture(
                    response.result()
            );
            List<String> ormQueries =
                    Arrays.asList(statistics.getQueries());

            /*
             * SQL được P6Spy dựng lại cùng giá trị bind.
             */
            List<String> sqlStatements =
                    CollectingP6SpyLogger.end();

            int sqlPreviewLimit = 20;

            List<String> sqlStatementsPreview =
                    sqlStatements.stream()
                            .limit(sqlPreviewLimit)
                            .toList();

            return response.withBenchmark(
                    scenario.value(),
                    statistics.getQueryExecutionCount(),
                    ormQueries,
                    sqlStatements.size(),
                    sqlStatementsPreview,
                    executionTimeMs
//                    cpuTimeMs,
//                    threadAllocatedMb,
//                    gcCountDelta
            ).withPersistenceContextProof(persistenceContextProof);
        } catch (Throwable throwable) {
            /*
             * Luôn xóa ThreadLocal, kể cả khi API bị lỗi.
             */
            CollectingP6SpyLogger.end();
            throw throwable;
        }
    }

    private Statistics getStatistics() {
        return entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();
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
            ThreadMXBean cpuMxBean
    ) {
        if (cpuMxBean.isThreadCpuTimeSupported()
                && !cpuMxBean.isThreadCpuTimeEnabled()) {

            cpuMxBean.setThreadCpuTimeEnabled(true);
        }
    }

    private long currentThreadCpuNanos(
            ThreadMXBean cpuMxBean
    ) {
        if (!cpuMxBean.isCurrentThreadCpuTimeSupported()
                || !cpuMxBean.isThreadCpuTimeEnabled()) {

            return 0;
        }

        return cpuMxBean.getCurrentThreadCpuTime();
    }

    private com.sun.management.ThreadMXBean getAllocationMxBean() {
        ThreadMXBean threadMxBean =
                ManagementFactory.getThreadMXBean();

        if (threadMxBean
                instanceof com.sun.management.ThreadMXBean allocationMxBean) {

            return allocationMxBean;
        }

        return null;
    }

    private void enableThreadAllocationTracking(
            com.sun.management.ThreadMXBean allocationMxBean
    ) {
        if (allocationMxBean == null) {
            return;
        }

        if (allocationMxBean.isThreadAllocatedMemorySupported()
                && !allocationMxBean.isThreadAllocatedMemoryEnabled()) {

            allocationMxBean.setThreadAllocatedMemoryEnabled(true);
        }
    }

    private long currentThreadAllocatedBytes(
            com.sun.management.ThreadMXBean allocationMxBean
    ) {
        if (allocationMxBean == null
                || !allocationMxBean.isThreadAllocatedMemorySupported()
                || !allocationMxBean.isThreadAllocatedMemoryEnabled()) {

            return -1;
        }

        return allocationMxBean.getCurrentThreadAllocatedBytes();
    }

    private double allocationDeltaMb(
            long allocatedBefore,
            long allocatedAfter
    ) {
        if (allocatedBefore < 0
                || allocatedAfter < allocatedBefore) {

            return -1;
        }

        return (allocatedAfter - allocatedBefore)
                / BYTES_PER_MB;
    }

    private double nanosToMillis(long nanos) {
        return Math.max(
                0,
                nanos / 1_000_000.0
        );
    }
}