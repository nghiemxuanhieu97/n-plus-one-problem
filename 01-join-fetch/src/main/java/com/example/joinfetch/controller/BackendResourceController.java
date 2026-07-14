package com.example.joinfetch.controller;

import com.sun.management.OperatingSystemMXBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.time.Duration;

@RestController
@RequestMapping("/api/system")
public class BackendResourceController {

    private static final double BYTES_PER_MB = 1024.0 * 1024.0;

    @GetMapping("/resources")
    public BackendResourceResponse getBackendResources() {
        Runtime runtime = Runtime.getRuntime();

        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        OperatingSystemMXBean operatingSystemMXBean =
                (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryMXBean.getNonHeapMemoryUsage();

        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();

        return new BackendResourceResponse(
                new CpuInfo(
                        runtime.availableProcessors(),
                        percentage(operatingSystemMXBean.getProcessCpuLoad()),
                        percentage(operatingSystemMXBean.getCpuLoad())
                ),
                new MemoryInfo(
                        toMb(heap.getUsed()),
                        toMb(heap.getCommitted()),
                        toMb(heap.getMax()),
                        calculatePercentage(heap.getUsed(), heap.getMax())
                ),
                new MemoryInfo(
                        toMb(nonHeap.getUsed()),
                        toMb(nonHeap.getCommitted()),
                        nonHeap.getMax() < 0 ? null : toMb(nonHeap.getMax()),
                        nonHeap.getMax() < 0
                                ? null
                                : calculatePercentage(nonHeap.getUsed(), nonHeap.getMax())
                ),
                new SystemMemoryInfo(
                        toMb(operatingSystemMXBean.getTotalMemorySize()),
                        toMb(operatingSystemMXBean.getFreeMemorySize()),
                        toMb(
                                operatingSystemMXBean.getTotalMemorySize()
                                        - operatingSystemMXBean.getFreeMemorySize()
                        )
                ),
                new ThreadInfo(
                        threadMXBean.getThreadCount(),
                        threadMXBean.getPeakThreadCount(),
                        threadMXBean.getDaemonThreadCount()
                ),
                formatDuration(uptimeMs)
        );
    }

    private double toMb(long bytes) {
        return round(bytes / BYTES_PER_MB);
    }

    private Double percentage(double value) {
        // JVM có thể trả về -1 nếu CPU load chưa lấy được.
        return value < 0 ? null : round(value * 100);
    }

    private Double calculatePercentage(long used, long max) {
        if (max <= 0) {
            return null;
        }

        return round((double) used / max * 100);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String formatDuration(long milliseconds) {
        Duration duration = Duration.ofMillis(milliseconds);

        return "%dd %02dh %02dm %02ds".formatted(
                duration.toDays(),
                duration.toHoursPart(),
                duration.toMinutesPart(),
                duration.toSecondsPart()
        );
    }

    public record BackendResourceResponse(
            CpuInfo cpu,
            MemoryInfo heapMemory,
            MemoryInfo nonHeapMemory,
            SystemMemoryInfo systemMemory,
            ThreadInfo threads,
            String uptime
    ) {
    }

    public record CpuInfo(
            int availableProcessors,
            Double processUsagePercent,
            Double systemUsagePercent
    ) {
    }

    public record MemoryInfo(
            double usedMb,
            double committedMb,
            Double maxMb,
            Double usagePercent
    ) {
    }

    public record SystemMemoryInfo(
            double totalMb,
            double freeMb,
            double usedMb
    ) {
    }

    public record ThreadInfo(
            int current,
            int peak,
            int daemon
    ) {
    }
}