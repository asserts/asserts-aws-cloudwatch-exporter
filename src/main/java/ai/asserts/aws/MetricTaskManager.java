
package ai.asserts.aws;

import ai.asserts.aws.cloudwatch.config.MetricConfig;
import ai.asserts.aws.cloudwatch.config.ScrapeConfig;
import ai.asserts.aws.cloudwatch.config.ScrapeConfigProvider;
import ai.asserts.aws.exporter.LambdaLogMetricScrapeTask;
import ai.asserts.aws.exporter.MetricScrapeTask;
import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.annotation.Timed;
import io.prometheus.client.CollectorRegistry;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;

@Component
@Slf4j
@AllArgsConstructor
public class MetricTaskManager implements InitializingBean {
    private final CollectorRegistry collectorRegistry;
    private final AutowireCapableBeanFactory beanFactory;
    private final ScrapeConfigProvider scrapeConfigProvider;
    private final TaskThreadPool taskThreadPool;

    /**
     * Maintains the last scrape time for all the metricso of a given scrape interval. The scrapes are
     * not expected to happen concurrently so no need to worry about thread safety
     */
    @Getter
    private final Map<Integer, Map<String, MetricScrapeTask>> metricScrapeTasks = new TreeMap<>();
    @Getter
    private final Map<Integer, Map<String, Set<LambdaLogMetricScrapeTask>>> logScrapeTasks = new TreeMap<>();

    public void afterPropertiesSet() {
        ScrapeConfig scrapeConfig = scrapeConfigProvider.getScrapeConfig();
        scrapeConfig.getNamespaces().forEach(nc -> nc.getMetrics().stream()
                .map(MetricConfig::getScrapeInterval)
                .forEach(interval -> scrapeConfig.getRegions().forEach(region -> {
                            Map<String, MetricScrapeTask> byRegion = metricScrapeTasks.computeIfAbsent(interval,
                                    k -> new TreeMap<>());
                            if (!byRegion.containsKey(region)) {
                                byRegion.put(region,
                                        metricScrapeTask(region, interval, scrapeConfig.getDelay()));
                            }
                        }
                )));

        scrapeConfig.getLambdaConfig().ifPresent(nc -> {
            if (!CollectionUtils.isEmpty(nc.getLogs())) {
                scrapeConfig.getRegions().forEach(region -> logScrapeTasks
                        .computeIfAbsent(60, k -> new TreeMap<>())
                        .computeIfAbsent(region, k -> new HashSet<>())
                        .add(lambdaLogScrapeTask(nc, region))
                );
            }
        });
    }

    @SuppressWarnings("unused")
    @Scheduled(fixedRateString = "${aws.metric.scrape.manager.task.fixedDelay:60000}",
            initialDelayString = "${aws.metric.scrape.manager.task.initialDelay:5000}")
    @Timed(description = "Time spent scraping cloudwatch metrics from all regions", histogram = true)
    public void triggerScrapes() {
        ExecutorService executorService = taskThreadPool.getExecutorService();
        metricScrapeTasks.values().stream()
                .flatMap(map -> map.values().stream())
                .forEach(task -> {
                    executorService.submit(task::update);
                    sleep(2001L);
                });

        logScrapeTasks.values().stream()
                .flatMap(map -> map.values().stream())
                .flatMap(Collection::stream)
                .forEach(task -> {
                    executorService.submit(task::update);
                    sleep(2000L);
                });
    }

    private void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            log.error("Interrupted", e);
        }
    }

    @VisibleForTesting
    MetricScrapeTask newScrapeTask(String region, Integer interval, Integer delay) {
        return new MetricScrapeTask(region, interval, delay);
    }

    @VisibleForTesting
    LambdaLogMetricScrapeTask newLogScrapeTask(String region) {
        return new LambdaLogMetricScrapeTask(region);
    }

    private LambdaLogMetricScrapeTask lambdaLogScrapeTask(ai.asserts.aws.cloudwatch.config.NamespaceConfig nc,
                                                          String region) {
        log.info("Setup lambda log scrape task for region {} with scrape configs {}", region, nc.getLogs());
        LambdaLogMetricScrapeTask logScraperTask = newLogScrapeTask(region);
        beanFactory.autowireBean(logScraperTask);
        logScraperTask.register(collectorRegistry);
        return logScraperTask;
    }

    private MetricScrapeTask metricScrapeTask(String region, Integer interval, Integer delay) {
        MetricScrapeTask metricScrapeTask = newScrapeTask(region, interval, delay);
        beanFactory.autowireBean(metricScrapeTask);
        metricScrapeTask.register(collectorRegistry);
        log.info("Setup metric scrape task for region {} and interval {}", region, interval);
        return metricScrapeTask;
    }
}
