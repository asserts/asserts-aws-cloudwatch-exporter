/*
 *  Copyright © 2020.
 *  Asserts, Inc. - All Rights Reserved
 */
package ai.asserts.aws.lambda;

import ai.asserts.aws.RateLimiter;
import ai.asserts.aws.cloudwatch.TimeWindowBuilder;
import ai.asserts.aws.cloudwatch.config.LogScrapeConfig;
import ai.asserts.aws.exporter.BasicMetricCollector;
import com.google.common.collect.ImmutableSortedMap;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilterLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilterLogEventsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilteredLogEvent;

import java.time.Instant;
import java.util.Optional;

import static ai.asserts.aws.MetricNameUtil.SCRAPE_ERROR_COUNT_METRIC;
import static ai.asserts.aws.MetricNameUtil.SCRAPE_FUNCTION_NAME_LABEL;
import static ai.asserts.aws.MetricNameUtil.SCRAPE_LATENCY_METRIC;
import static ai.asserts.aws.MetricNameUtil.SCRAPE_OPERATION_LABEL;
import static ai.asserts.aws.MetricNameUtil.SCRAPE_REGION_LABEL;
import static java.lang.String.format;

@Component
@Slf4j
@AllArgsConstructor
public class LogEventScraper {
    private final BasicMetricCollector metricCollector;
    private final TimeWindowBuilder timeWindowBuilder;
    private final RateLimiter rateLimiter;

    public Optional<FilteredLogEvent> findLogEvent(CloudWatchLogsClient cloudWatchLogsClient,
                                                   LambdaFunction functionConfig,
                                                   LogScrapeConfig logScrapeConfig) {
        Instant[] timePeriod = timeWindowBuilder.getTimePeriod(functionConfig.getRegion(), 60);
        Instant endTime = timePeriod[1].minusSeconds(60);
        Instant startTime = timePeriod[0].minusSeconds(60);
        String logGroupName = format("/aws/lambda/%s", functionConfig.getName());
        log.info("About to scrape logs from {}", logGroupName);
        try {
            FilterLogEventsRequest logEventsRequest = FilterLogEventsRequest.builder()
                    .limit(1)
                    .startTime(startTime.toEpochMilli())
                    .endTime(endTime.toEpochMilli())
                    .filterPattern(logScrapeConfig.getLogFilterPattern())
                    .logGroupName(logGroupName)
                    .build();

            long timeTaken = System.currentTimeMillis();
            FilterLogEventsResponse response = rateLimiter.doWithRateLimit(
                    "CloudWatchLogsClient/filterLogEvents", () ->
                            cloudWatchLogsClient.filterLogEvents(logEventsRequest));
            timeTaken = System.currentTimeMillis() - timeTaken;
            metricCollector.recordLatency(SCRAPE_LATENCY_METRIC,
                    ImmutableSortedMap.of(
                            SCRAPE_REGION_LABEL, functionConfig.getRegion(),
                            SCRAPE_OPERATION_LABEL, "scrape_lambda_logs",
                            SCRAPE_FUNCTION_NAME_LABEL, functionConfig.getName()
                    ), timeTaken);

            if (response.hasEvents()) {
                log.info("log scrape config {} matched {} events", logScrapeConfig, response.events().size());
                return response.events().stream().findFirst();
            }
        } catch (Exception e) {
            log.error("Failed to scrape logs from log group " + logGroupName, e);
            metricCollector.recordCounterValue(SCRAPE_ERROR_COUNT_METRIC, ImmutableSortedMap.of(
                    SCRAPE_REGION_LABEL, functionConfig.getRegion(), SCRAPE_OPERATION_LABEL, "findLogEvent"
            ), 1);
        }
        return Optional.empty();
    }
}
