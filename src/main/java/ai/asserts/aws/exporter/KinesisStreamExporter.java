/*
 *  Copyright © 2022.
 *  Asserts, Inc. - All Rights Reserved
 */
package ai.asserts.aws.exporter;

import ai.asserts.aws.AWSClientProvider;
import ai.asserts.aws.RateLimiter;
import ai.asserts.aws.TagUtil;
import ai.asserts.aws.TenantUtil;
import ai.asserts.aws.account.AccountProvider;
import ai.asserts.aws.resource.Resource;
import ai.asserts.aws.resource.ResourceTagHelper;
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
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.Future;
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
    private final ResourceTagHelper resourceTagHelper;
    private final TagUtil tagUtil;
    private final TenantUtil tenantUtil;
    private volatile List<MetricFamilySamples> metricFamilySamples = new ArrayList<>();

    public KinesisStreamExporter(
            AccountProvider accountProvider, AWSClientProvider awsClientProvider, CollectorRegistry collectorRegistry,
            RateLimiter rateLimiter, MetricSampleBuilder sampleBuilder, ResourceTagHelper resourceTagHelper,
            TagUtil tagUtil, TenantUtil tenantUtil) {
        this.accountProvider = accountProvider;
        this.awsClientProvider = awsClientProvider;
        this.collectorRegistry = collectorRegistry;
        this.rateLimiter = rateLimiter;
        this.sampleBuilder = sampleBuilder;
        this.resourceTagHelper = resourceTagHelper;
        this.tagUtil = tagUtil;
        this.tenantUtil = tenantUtil;
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
        List<Future<?>> futures = new ArrayList<>();
        accountProvider.getAccounts().forEach(account -> account.getRegions().forEach(region ->
                futures.add(tenantUtil.executeTenantTask(account.getTenant(), () -> {
                    try {
                        KinesisClient client = awsClientProvider.getKinesisClient(region, account);
                        String api = "KinesisClient/listStreams";
                        ListStreamsResponse resp = rateLimiter.doWithRateLimit(
                                api, ImmutableSortedMap.of(
                                        SCRAPE_ACCOUNT_ID_LABEL, account.getAccountId(),
                                        SCRAPE_REGION_LABEL, region,
                                        SCRAPE_OPERATION_LABEL, api
                                ), client::listStreams);
                        if (resp.hasStreamNames()) {
                            Map<String, Resource> byName = resourceTagHelper.getResourcesWithTag(account, region,
                                    "kinesis:stream", resp.streamNames());
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
                                        if (byName.containsKey(stream)) {
                                            labels.putAll(tagUtil.tagLabels(byName.get(stream).getTags()));
                                        }
                                        return sampleBuilder.buildSingleSample("aws_resource", labels, 1.0D);
                                    })
                                    .filter(Optional::isPresent)
                                    .map(Optional::get)
                                    .collect(Collectors.toList()));
                        }
                    } catch (Exception e) {
                        log.error("Error:" + account, e);
                    }
                }))));
        tenantUtil.awaitAll(futures);
        sampleBuilder.buildFamily(samples).ifPresent(newFamily::add);
        metricFamilySamples = newFamily;
    }
}
