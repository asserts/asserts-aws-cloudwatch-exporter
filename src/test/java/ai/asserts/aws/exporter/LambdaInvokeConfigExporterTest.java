/*
 *  Copyright © 2020.
 *  Asserts, Inc. - All Rights Reserved
 */
package ai.asserts.aws.exporter;

import ai.asserts.aws.AWSClientProvider;
import ai.asserts.aws.MetricNameUtil;
import ai.asserts.aws.RateLimiter;
import ai.asserts.aws.cloudwatch.config.NamespaceConfig;
import ai.asserts.aws.cloudwatch.config.ScrapeConfig;
import ai.asserts.aws.cloudwatch.config.ScrapeConfigProvider;
import ai.asserts.aws.cloudwatch.model.CWNamespace;
import ai.asserts.aws.lambda.LambdaFunction;
import ai.asserts.aws.lambda.LambdaFunctionScraper;
import ai.asserts.aws.resource.Resource;
import ai.asserts.aws.resource.ResourceMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.prometheus.client.Collector;
import org.easymock.EasyMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.DestinationConfig;
import software.amazon.awssdk.services.lambda.model.FunctionEventInvokeConfig;
import software.amazon.awssdk.services.lambda.model.ListFunctionEventInvokeConfigsRequest;
import software.amazon.awssdk.services.lambda.model.ListFunctionEventInvokeConfigsResponse;
import software.amazon.awssdk.services.lambda.model.OnFailure;
import software.amazon.awssdk.services.lambda.model.OnSuccess;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.expect;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LambdaInvokeConfigExporterTest extends EasyMockSupport {
    private LambdaFunctionScraper fnScraper;
    private AWSClientProvider awsClientProvider;
    private LambdaClient lambdaClient;
    private MetricNameUtil metricNameUtil;
    private ResourceMapper resourceMapper;
    private Resource resource;
    private MetricSampleBuilder metricSampleBuilder;
    private Collector.MetricFamilySamples.Sample sample;
    private BasicMetricCollector metricCollector;
    private LambdaInvokeConfigExporter testClass;

    @BeforeEach
    public void setup() {
        fnScraper = mock(LambdaFunctionScraper.class);
        awsClientProvider = mock(AWSClientProvider.class);
        lambdaClient = mock(LambdaClient.class);
        metricNameUtil = mock(MetricNameUtil.class);
        ScrapeConfigProvider scrapeConfigProvider = mock(ScrapeConfigProvider.class);
        ScrapeConfig scrapeConfig = mock(ScrapeConfig.class);
        NamespaceConfig namespaceConfig = mock(NamespaceConfig.class);
        resourceMapper = mock(ResourceMapper.class);
        resource = mock(Resource.class);
        metricSampleBuilder = mock(MetricSampleBuilder.class);
        sample = mock(Collector.MetricFamilySamples.Sample.class);
        metricCollector = mock(BasicMetricCollector.class);

        testClass = new LambdaInvokeConfigExporter(fnScraper, awsClientProvider, metricNameUtil,
                scrapeConfigProvider, resourceMapper, metricSampleBuilder, new RateLimiter(metricCollector));

        expect(scrapeConfig.getLambdaConfig()).andReturn(Optional.of(namespaceConfig));
        expect(scrapeConfigProvider.getScrapeConfig()).andReturn(scrapeConfig);
        expect(metricNameUtil.getMetricPrefix(CWNamespace.lambda.getNamespace())).andReturn("prefix");
    }

    @Test
    void collect() {
        expect(awsClientProvider.getLambdaClient("region1")).andReturn(lambdaClient);

        ListFunctionEventInvokeConfigsRequest request = ListFunctionEventInvokeConfigsRequest.builder()
                .functionName("fn1")
                .build();

        ListFunctionEventInvokeConfigsResponse response = ListFunctionEventInvokeConfigsResponse.builder()
                .functionEventInvokeConfigs(FunctionEventInvokeConfig.builder()
                        .functionArn("fn1:arn")
                        .destinationConfig(DestinationConfig.builder()
                                .onSuccess(OnSuccess.builder()
                                        .destination("dst1:arn")
                                        .build())
                                .build())
                        .build(), FunctionEventInvokeConfig.builder()
                        .functionArn("fn1:arn")
                        .destinationConfig(DestinationConfig.builder()
                                .onFailure(OnFailure.builder()
                                        .destination("dst2:arn")
                                        .build())
                                .build())
                        .build())
                .build();

        expect(lambdaClient.listFunctionEventInvokeConfigs(request)).andReturn(response);
        metricCollector.recordLatency(anyString(), anyObject(), anyLong());

        expect(fnScraper.getFunctions()).andReturn(ImmutableMap.of(
                "region1", ImmutableMap.of("fn1:arn", LambdaFunction.builder()
                        .name("fn1")
                        .arn("fn1:arn")
                        .region("region1")
                        .account("account1")
                        .resource(resource)
                        .build())
        ));

        Map<String, String> baseLabels = ImmutableMap.of(
                "d_function_name", "fn1", "region", "region1", "account", "account1");
        Map<String, String> success = new TreeMap<>(baseLabels);
        success.put("on", "success");

        Map<String, String> failure = new TreeMap<>(baseLabels);
        failure.put("on", "failure");

        resource.addTagLabels(baseLabels, metricNameUtil);
        expect(resourceMapper.map("dst1:arn")).andReturn(Optional.of(resource));
        resource.addLabels(success, "destination");

        resource.addTagLabels(baseLabels, metricNameUtil);
        expect(resourceMapper.map("dst2:arn")).andReturn(Optional.of(resource));
        resource.addLabels(failure, "destination");

        expect(metricSampleBuilder.buildSingleSample("prefix_invoke_config", success, 1.0D))
                .andReturn(sample);

        expect(metricSampleBuilder.buildSingleSample("prefix_invoke_config", failure, 1.0D))
                .andReturn(sample);

        lambdaClient.close();


        replayAll();
        testClass.update();
        assertEquals(ImmutableList.of(new Collector.MetricFamilySamples(
                "prefix_invoke_config", Collector.Type.GAUGE, "", ImmutableList.of(sample, sample)
        )), testClass.collect());
        verifyAll();
    }
}
