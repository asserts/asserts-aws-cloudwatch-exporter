/*
 *  Copyright © 2022.
 *  Asserts, Inc. - All Rights Reserved
 */
package ai.asserts.aws.exporter;

import ai.asserts.aws.AWSClientProvider;
import ai.asserts.aws.AccountProvider;
import ai.asserts.aws.RateLimiter;
import com.google.common.collect.ImmutableSortedMap;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.ListStreamsResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static ai.asserts.aws.MetricNameUtil.SCRAPE_ACCOUNT_ID_LABEL;
import static ai.asserts.aws.MetricNameUtil.SCRAPE_OPERATION_LABEL;
import static ai.asserts.aws.MetricNameUtil.SCRAPE_REGION_LABEL;

@Component
@Slf4j
public class KinesisStreamExporter extends Collector implements InitializingBean {
    public final CollectorRegistry collectorRegistry;
    private final AccountProvider accountProvider;
    private final AWSClientProvider awsClientProvider;
    private final RateLimiter rateLimiter;
    private final MetricSampleBuilder sampleBuilder;
    private volatile List<MetricFamilySamples> metricFamilySamples = new ArrayList<>();

    public KinesisStreamExporter(
            AccountProvider accountProvider, AWSClientProvider awsClientProvider, CollectorRegistry collectorRegistry,
            RateLimiter rateLimiter, MetricSampleBuilder sampleBuilder) {
        this.accountProvider = accountProvider;
        this.awsClientProvider = awsClientProvider;
        this.collectorRegistry = collectorRegistry;
        this.rateLimiter = rateLimiter;
        this.sampleBuilder = sampleBuilder;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        register(collectorRegistry);
    }

    @Override
    public List<MetricFamilySamples> collect() {
        return metricFamilySamples;
    }

    public void update() {
        log.info("Exporting Kinesis Streams");
        List<MetricFamilySamples> newFamily = new ArrayList<>();
        List<MetricFamilySamples.Sample> samples = new ArrayList<>();
        accountProvider.getAccounts().forEach(account -> account.getRegions().forEach(region -> {
            try (KinesisClient client = awsClientProvider.getKinesisClient(region, account)) {
                String api = "KinesisClient/listStreams";
                ListStreamsResponse resp = rateLimiter.doWithRateLimit(
                        api, ImmutableSortedMap.of(
                                SCRAPE_ACCOUNT_ID_LABEL, account.getAccountId(),
                                SCRAPE_REGION_LABEL, region,
                                SCRAPE_OPERATION_LABEL, api
                        ), () -> client.listStreams());
                if (resp.hasStreamNames()) {
                    samples.addAll(resp.streamNames().stream()
                            .map(stream -> {
                                Map<String, String> labels = new TreeMap<>();
                                labels.put(SCRAPE_ACCOUNT_ID_LABEL, account.getAccountId());
                                labels.put(SCRAPE_REGION_LABEL, region);
                                labels.put("aws_resource_type", "AWS::Kinesis::Stream");
                                labels.put("namespace", "AWS/Kinesis");
                                labels.put("job", stream);
                                labels.put("name", stream);
                                labels.put("id", stream);
                                return sampleBuilder.buildSingleSample("aws_resource", labels, 1.0D);
                            })
                            .collect(Collectors.toList()));
                }
            }
        }));
        newFamily.add(sampleBuilder.buildFamily(samples));
        metricFamilySamples = newFamily;
    }
}
