
package ai.asserts.aws.exporter;

import ai.asserts.aws.AWSClientProvider;
import ai.asserts.aws.RateLimiter;
import ai.asserts.aws.cloudwatch.TimeWindowBuilder;
import ai.asserts.aws.cloudwatch.config.MetricConfig;
import ai.asserts.aws.cloudwatch.query.MetricQuery;
import ai.asserts.aws.cloudwatch.query.MetricQueryProvider;
import ai.asserts.aws.cloudwatch.query.QueryBatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import org.easymock.EasyMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricDataResponse;
import software.amazon.awssdk.services.cloudwatch.model.Metric;
import software.amazon.awssdk.services.cloudwatch.model.MetricDataQuery;
import software.amazon.awssdk.services.cloudwatch.model.MetricDataResult;
import software.amazon.awssdk.services.cloudwatch.model.StatusCode;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MetricScrapeTaskTest extends EasyMockSupport {
    private String region;
    private Integer interval;
    private Integer delay;
    private MetricQueryProvider metricQueryProvider;
    private QueryBatcher queryBatcher;
    private BasicMetricCollector metricCollector;
    private AWSClientProvider awsClientProvider;
    private CloudWatchClient cloudWatchClient;
    private Instant now;
    private MetricSampleBuilder sampleBuilder;
    private Sample sample;
    private Collector.MetricFamilySamples familySamples;
    private TimeWindowBuilder timeWindowBuilder;
    private MetricScrapeTask testClass;

    @BeforeEach
    public void setup() {
        now = Instant.now();

        region = "region1";
        interval = 60;
        delay = 0;
        metricQueryProvider = mock(MetricQueryProvider.class);
        queryBatcher = mock(QueryBatcher.class);
        metricCollector = mock(BasicMetricCollector.class);
        awsClientProvider = mock(AWSClientProvider.class);
        cloudWatchClient = mock(CloudWatchClient.class);
        sampleBuilder = mock(MetricSampleBuilder.class);
        sample = new Sample("metric1", Collections.emptyList(), Collections.emptyList(),
                1.0D, now.toEpochMilli());
        familySamples = mock(Collector.MetricFamilySamples.class);
        timeWindowBuilder = mock(TimeWindowBuilder.class);

        testClass = new MetricScrapeTask(region, interval, delay);
        testClass.setMetricQueryProvider(metricQueryProvider);
        testClass.setQueryBatcher(queryBatcher);
        testClass.setMetricCollector(metricCollector);
        testClass.setAwsClientProvider(awsClientProvider);
        testClass.setSampleBuilder(sampleBuilder);
        testClass.setTimeWindowBuilder(timeWindowBuilder);
        testClass.setRateLimiter(new RateLimiter());
    }

    @Test
    public void run() {
        List<MetricQuery> queries = ImmutableList.of(
                MetricQuery.builder()
                        .metric(Metric.builder().namespace("ns1").build())
                        .metricConfig(MetricConfig.builder().scrapeInterval(interval).build())
                        .metricDataQuery(MetricDataQuery.builder()
                                .id("id1")
                                .build())
                        .build(),
                MetricQuery.builder()
                        .metric(Metric.builder().namespace("ns2").build())
                        .metricConfig(MetricConfig.builder().scrapeInterval(interval).build())
                        .metricDataQuery(MetricDataQuery.builder()
                                .id("id2")
                                .build())
                        .build(),
                MetricQuery.builder()
                        .metric(Metric.builder().namespace("ns3").build())
                        .metricConfig(MetricConfig.builder().scrapeInterval(interval).build())
                        .metricDataQuery(MetricDataQuery.builder()
                                .id("id3")
                                .build())
                        .build());

        expect(metricQueryProvider.getMetricQueries())
                .andReturn(ImmutableMap.of(region, ImmutableMap.of(interval, queries)));

        expect(awsClientProvider.getCloudWatchClient(region)).andReturn(cloudWatchClient);

        expect(timeWindowBuilder.getTimePeriod(region)).andReturn(new Instant[]{now.minusSeconds(60), now});

        expect(queryBatcher.splitIntoBatches(queries)).andReturn(ImmutableList.of(queries));

        Instant endTime = now.minusSeconds(delay);
        Instant startTime = now.minusSeconds(60 + delay);
        GetMetricDataRequest request = GetMetricDataRequest.builder()
                .metricDataQueries(queries.stream()
                        .map(MetricQuery::getMetricDataQuery)
                        .collect(Collectors.toList()))
                .endTime(endTime)
                .startTime(startTime)
                .build();

        MetricDataResult mdr1 = MetricDataResult.builder()
                .timestamps(ImmutableList.of(now))
                .values(ImmutableList.of(1.0D))
                .statusCode(StatusCode.COMPLETE)
                .id("id1")
                .build();

        expect(cloudWatchClient.getMetricData(request)).andReturn(
                GetMetricDataResponse.builder()
                        .metricDataResults(ImmutableList.of(mdr1))
                        .nextToken("token1")
                        .build()
        );
        metricCollector.recordLatency(anyObject(), anyObject(), anyLong());

        expect(sampleBuilder.buildSamples(region, queries.get(0), mdr1))
                .andReturn(ImmutableList.of(sample));

        request = GetMetricDataRequest.builder()
                .metricDataQueries(queries.stream()
                        .map(MetricQuery::getMetricDataQuery)
                        .collect(Collectors.toList()))
                .endTime(endTime)
                .startTime(startTime)
                .nextToken("token1")
                .build();

        MetricDataResult mdr2 = MetricDataResult.builder()
                .timestamps(ImmutableList.of(now))
                .values(ImmutableList.of(1.0D))
                .statusCode(StatusCode.COMPLETE)
                .id("id2")
                .build();

        expect(cloudWatchClient.getMetricData(request)).andReturn(
                GetMetricDataResponse.builder()
                        .metricDataResults(ImmutableList.of(mdr2))
                        .build()
        );

        metricCollector.recordLatency(anyObject(), anyObject(), anyLong());

        expect(sampleBuilder.buildSamples(region, queries.get(1), mdr2))
                .andReturn(ImmutableList.of(sample));

        cloudWatchClient.close();

        expect(sampleBuilder.buildFamily(ImmutableList.of(sample, sample))).andReturn(familySamples);

        replayAll();
        testClass.update();
        assertEquals(ImmutableList.of(familySamples), testClass.collect());
        verifyAll();
    }

    @Test
    public void run_NoQueriesForRegion() {
        expect(metricQueryProvider.getMetricQueries())
                .andReturn(ImmutableMap.of());

        replayAll();
        testClass.update();
        assertEquals(ImmutableList.of(), testClass.collect());
        verifyAll();
    }

    @Test
    public void run_NoQueriesForInterval() {
        expect(metricQueryProvider.getMetricQueries())
                .andReturn(ImmutableMap.of(region, ImmutableMap.of()));

        replayAll();
        testClass.update();
        assertEquals(ImmutableList.of(), testClass.collect());
        verifyAll();
    }
}
