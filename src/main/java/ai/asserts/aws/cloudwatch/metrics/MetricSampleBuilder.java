/*
 *  Copyright © 2020.
 *  Asserts, Inc. - All Rights Reserved
 */
package ai.asserts.aws.cloudwatch.metrics;

import ai.asserts.aws.MetricNameUtil;
import ai.asserts.aws.cloudwatch.prometheus.LabelBuilder;
import ai.asserts.aws.cloudwatch.query.MetricQuery;
import io.prometheus.client.Collector;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cloudwatch.model.MetricDataResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@AllArgsConstructor
public class MetricSampleBuilder {
    private final MetricNameUtil metricNameUtil;
    private final LabelBuilder labelBuilder;

    public List<Collector.MetricFamilySamples.Sample> buildSamples(String region, MetricQuery metricQuery,
                                                                   MetricDataResult metricDataResult,
                                                                   Instant startTime, Instant endTime, int period) {
        List<Collector.MetricFamilySamples.Sample> samples = new ArrayList<>();
        String metricName = metricNameUtil.exportedMetricName(metricQuery.getMetric(), metricQuery.getMetricStat());
        if (metricDataResult.timestamps().size() > 0) {
            for (int i = 0; i < metricDataResult.timestamps().size(); i++) {
                Map<String, String> labels = labelBuilder.buildLabels(region, metricQuery);
                Collector.MetricFamilySamples.Sample sample = new Collector.MetricFamilySamples.Sample(
                        metricName,
                        new ArrayList<>(labels.keySet()),
                        new ArrayList<>(labels.values()),
                        metricDataResult.values().get(i),
                        metricDataResult.timestamps().get(i).toEpochMilli());
                samples.add(sample);
            }
        }
        return samples;
    }
}