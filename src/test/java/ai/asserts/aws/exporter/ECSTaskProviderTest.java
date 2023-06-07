/*
 *  Copyright © 2020.
 *  Asserts, Inc. - All Rights Reserved
 */
package ai.asserts.aws.exporter;

import ai.asserts.aws.AWSClientProvider;
import ai.asserts.aws.RateLimiter;
import ai.asserts.aws.ScrapeConfigProvider;
import ai.asserts.aws.TaskExecutorUtil;
import ai.asserts.aws.TestTaskThreadPool;
import ai.asserts.aws.account.AWSAccount;
import ai.asserts.aws.account.AccountProvider;
import ai.asserts.aws.config.ScrapeConfig;
import ai.asserts.aws.exporter.ECSServiceDiscoveryExporter.StaticConfig;
import ai.asserts.aws.resource.Resource;
import ai.asserts.aws.resource.ResourceMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import io.prometheus.client.CollectorRegistry;
import org.easymock.EasyMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DescribeTasksRequest;
import software.amazon.awssdk.services.ecs.model.DescribeTasksResponse;
import software.amazon.awssdk.services.ecs.model.DesiredStatus;
import software.amazon.awssdk.services.ecs.model.ListTasksRequest;
import software.amazon.awssdk.services.ecs.model.ListTasksResponse;
import software.amazon.awssdk.services.ecs.model.Task;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;

import static ai.asserts.aws.exporter.ECSTaskProvider.TASK_META_METRIC;
import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({"unchecked", "deprecation"})
public class ECSTaskProviderTest extends EasyMockSupport {
    private AWSClientProvider awsClientProvider;
    private ScrapeConfigProvider scrapeConfigProvider;
    private ScrapeConfig scrapeConfig;
    private AccountProvider accountProvider;
    private BasicMetricCollector basicMetricCollector;
    private ResourceMapper resourceMapper;
    private ECSClusterProvider ecsClusterProvider;
    private ECSTaskUtil ecsTaskUtil;
    private MetricSampleBuilder sampleBuilder;
    private Sample mockSample;
    private Collector.MetricFamilySamples mockFamilySamples;

    private CollectorRegistry collectorRegistry;
    private EcsClient ecsClient;

    private StaticConfig mockStaticConfig;

    private TaskExecutorUtil taskExecutorUtil;
    private ECSTaskProvider testClass;

    @BeforeEach
    public void setup() {
        awsClientProvider = mock(AWSClientProvider.class);
        scrapeConfigProvider = mock(ScrapeConfigProvider.class);
        scrapeConfig = mock(ScrapeConfig.class);
        accountProvider = mock(AccountProvider.class);
        basicMetricCollector = mock(BasicMetricCollector.class);
        resourceMapper = mock(ResourceMapper.class);
        ecsClusterProvider = mock(ECSClusterProvider.class);
        ecsTaskUtil = mock(ECSTaskUtil.class);
        sampleBuilder = mock(MetricSampleBuilder.class);
        collectorRegistry = mock(CollectorRegistry.class);
        ecsClient = mock(EcsClient.class);
        mockStaticConfig = mock(StaticConfig.class);
        mockSample = mock(Sample.class);
        mockFamilySamples = mock(Collector.MetricFamilySamples.class);
        taskExecutorUtil = new TaskExecutorUtil(new TestTaskThreadPool(),
                new RateLimiter(basicMetricCollector, (accountId) -> "acme"));
        testClass = new ECSTaskProvider(awsClientProvider, scrapeConfigProvider, accountProvider,
                new RateLimiter(basicMetricCollector, (account) -> "acme"), resourceMapper, ecsClusterProvider,
                ecsTaskUtil, sampleBuilder,
                collectorRegistry, taskExecutorUtil);
    }


    @Test
    public void afterPropertiesSet() throws Exception {
        collectorRegistry.register(testClass);
        replayAll();
        testClass.afterPropertiesSet();
        verifyAll();
    }

    @Test
    public void collect() throws Exception {
        Resource cluster1 = Resource.builder()
                .name("cluster1")
                .region("region")
                .account("account1")
                .arn("cluster1-arn")
                .build();


        Resource task1 = Resource.builder()
                .name("task1")
                .build();

        Map<Resource, Map<Resource, List<StaticConfig>>> tasksByCluster = testClass.getTasksByCluster();
        tasksByCluster.put(cluster1, ImmutableMap.of(task1, ImmutableList.of(mockStaticConfig, mockStaticConfig)));

        Labels labels1 = Labels.builder()
                .vpcId("vpc-1")
                .build();
        expect(mockStaticConfig.getLabels()).andReturn(labels1);
        expect(sampleBuilder.buildSingleSample(TASK_META_METRIC, labels1, 1.0D))
                .andReturn(Optional.of(mockSample));

        Labels labels2 = Labels.builder()
                .vpcId("vpc-1")
                .build();
        expect(mockStaticConfig.getLabels()).andReturn(labels2);
        expect(sampleBuilder.buildSingleSample(TASK_META_METRIC, labels2, 1.0D))
                .andReturn(Optional.of(mockSample));

        expect(sampleBuilder.buildFamily(ImmutableList.of(mockSample, mockSample))).andReturn(
                Optional.of(mockFamilySamples));

        replayAll();
        assertEquals(ImmutableList.of(mockFamilySamples), testClass.collect());
        verifyAll();
    }

    @Test
    public void getService() {
        assertEquals(Optional.empty(), testClass.getService(Task.builder()
                .group("task-family-name")
                .build()));
        assertEquals(Optional.of("service-name"), testClass.getService(Task.builder()
                .group("service:service-name")
                .build()));
    }

    @Test
    public void getScrapeTargets() {
        AWSAccount awsAccount = AWSAccount.builder()
                .accountId("account1")
                .regions(ImmutableSet.of("region"))
                .name("test-account")
                .build();

        Resource cluster1 = Resource.builder()
                .name("cluster1")
                .region("region")
                .account("account1")
                .arn("cluster1-arn")
                .build();

        Resource task1 = Resource.builder()
                .name("task1")
                .build();

        testClass = new ECSTaskProvider(awsClientProvider, scrapeConfigProvider, accountProvider,
                new RateLimiter(basicMetricCollector, (account) -> "acme"), resourceMapper, ecsClusterProvider,
                ecsTaskUtil, sampleBuilder,
                collectorRegistry, taskExecutorUtil) {
            @Override
            void discoverNewTasks(Map<Resource, List<Resource>> clusterWiseNewTasks, EcsClient ecsClient,
                                  Resource cluster) {
                assertEquals(cluster1, cluster);
                clusterWiseNewTasks.put(cluster1, ImmutableList.of(task1));
            }

            @Override
            void buildNewTargets(AWSAccount _account, ScrapeConfig _scrapeConfig,
                                 Map<Resource, List<Resource>> clusterWiseNewTasks,
                                 EcsClient ecsClient) {
                assertEquals(awsAccount, _account);
                assertEquals(scrapeConfig, _scrapeConfig);
                assertEquals(ImmutableMap.of(cluster1, ImmutableList.of(task1)), clusterWiseNewTasks);
            }
        };

        testClass.getTasksByCluster().put(cluster1, ImmutableMap.of(task1, ImmutableList.of(mockStaticConfig)));

        expect(scrapeConfigProvider.getScrapeConfig()).andReturn(scrapeConfig);

        expect(accountProvider.getAccounts()).andReturn(ImmutableSet.of(awsAccount)).times(2);
        expect(awsClientProvider.getECSClient("region", awsAccount)).andReturn(ecsClient);
        expect(ecsClusterProvider.getClusters(awsAccount, "region")).andReturn(ImmutableSet.of(cluster1));
        replayAll();
        assertEquals(ImmutableList.of(mockStaticConfig), testClass.getScrapeTargets());
        verifyAll();
    }

    @Test
    public void discoverNewTargets() {
        Resource cluster1 = Resource.builder()
                .name("cluster1")
                .region("region")
                .account("account1")
                .arn("cluster1-arn")
                .build();

        Resource task1Resource = Resource.builder().name("task1").build();
        Resource task2Resource = Resource.builder().name("task2").build();
        Resource task3Resource = Resource.builder().name("task3").build();

        HashMap<Resource, List<StaticConfig>> byTask = new HashMap<>();
        byTask.put(task1Resource, ImmutableList.of(mockStaticConfig));
        byTask.put(task2Resource, ImmutableList.of(mockStaticConfig));
        testClass.getTasksByCluster().put(cluster1, byTask);


        expect(ecsClient.listTasks(ListTasksRequest.builder()
                .cluster("cluster1")
                .desiredStatus(DesiredStatus.RUNNING)
                .build()))
                .andReturn(
                        ListTasksResponse.builder()
                                .nextToken("token1")
                                .taskArns(ImmutableList.of("task2-arn"))
                                .build());
        basicMetricCollector.recordLatency(eq("aws_exporter_milliseconds"), anyObject(SortedMap.class), anyLong());

        expect(ecsClient.listTasks(ListTasksRequest.builder()
                .cluster("cluster1")
                .nextToken("token1")
                .desiredStatus(DesiredStatus.RUNNING)
                .build()))
                .andReturn(
                        ListTasksResponse.builder()
                                .nextToken("token1")
                                .taskArns(ImmutableList.of("task3-arn"))
                                .build());
        basicMetricCollector.recordLatency(eq("aws_exporter_milliseconds"), anyObject(SortedMap.class), anyLong());

        expect(resourceMapper.map("task2-arn")).andReturn(Optional.of(task2Resource));
        expect(resourceMapper.map("task3-arn")).andReturn(Optional.of(task3Resource));

        replayAll();

        HashMap<Resource, List<Resource>> clusterWiseNewTasks = new HashMap<>();
        testClass.discoverNewTasks(clusterWiseNewTasks, ecsClient, cluster1);
        assertFalse(clusterWiseNewTasks.isEmpty());
        assertTrue(clusterWiseNewTasks.containsKey(cluster1));
        assertEquals(ImmutableList.of(task3Resource), clusterWiseNewTasks.get(cluster1));

        assertFalse(byTask.containsKey(task1Resource));
        assertTrue(byTask.containsKey(task2Resource));
        verifyAll();
    }

    @Test
    public void buildNewTargets() {
        Resource cluster1 = Resource.builder()
                .name("cluster1")
                .region("region")
                .account("account1")
                .arn("cluster1-arn")
                .build();
        Resource service1 = Resource.builder().arn("service1-arn").build();
        Resource service2 = Resource.builder().arn("service2-arn").build();

        Resource cluster2 = Resource.builder()
                .name("cluster2")
                .region("region")
                .account("account1")
                .arn("cluster2-arn")
                .build();
        Resource service3 = Resource.builder().arn("service3-arn").build();
        Resource service4 = Resource.builder().arn("service4-arn").build();

        Map<Resource, List<Resource>> clusterWiseARNs = new HashMap<>();

        clusterWiseARNs.put(cluster1, ImmutableList.of(service1, service2));
        clusterWiseARNs.put(cluster2, ImmutableList.of(service3, service4));

        Task task1 = Task.builder()
                .group("service:service1")
                .taskArn("service1-task1-arn")
                .build();
        Task task2 = Task.builder()
                .group("service:service2")
                .taskArn("service2-task2-arn")
                .build();
        expect(ecsClient.describeTasks(DescribeTasksRequest.builder()
                .cluster("cluster1")
                .tasks("service1-arn", "service2-arn")
                .build())).andReturn(DescribeTasksResponse.builder()
                .tasks(task1, task2)
                .build());
        expect(ecsTaskUtil.hasAllInfo(task1)).andReturn(true);
        expect(ecsTaskUtil.hasAllInfo(task2)).andReturn(true);
        basicMetricCollector.recordLatency(eq("aws_exporter_milliseconds"), anyObject(SortedMap.class), anyLong());

        Resource task1Resource = Resource.builder().name("task1").build();
        Resource task2Resource = Resource.builder().name("task2").build();
        expect(resourceMapper.map("service1-task1-arn")).andReturn(Optional.of(task1Resource));
        expect(resourceMapper.map("service2-task2-arn")).andReturn(Optional.of(task2Resource));

        expect(ecsTaskUtil.buildScrapeTargets(scrapeConfig, ecsClient, cluster1, Optional.of("service1"), task1))
                .andReturn(ImmutableList.of(mockStaticConfig));
        expect(ecsTaskUtil.buildScrapeTargets(scrapeConfig, ecsClient, cluster1, Optional.of("service2"), task2))
                .andReturn(ImmutableList.of(mockStaticConfig));

        Task task3 = Task.builder()
                .group("service:service3")
                .taskArn("service3-task3-arn")
                .build();
        Task task4 = Task.builder()
                .group("service:service4")
                .taskArn("service4-task4-arn")
                .build();
        expect(ecsClient.describeTasks(DescribeTasksRequest.builder()
                .cluster("cluster2")
                .tasks("service3-arn", "service4-arn")
                .build())).andReturn(DescribeTasksResponse.builder()
                .tasks(task3, task4)
                .build());

        expect(ecsTaskUtil.hasAllInfo(task3)).andReturn(true);
        expect(ecsTaskUtil.hasAllInfo(task4)).andReturn(true);

        Resource task3Resource = Resource.builder().name("task3").build();
        Resource task4Resource = Resource.builder().name("task4").build();
        expect(resourceMapper.map("service3-task3-arn")).andReturn(Optional.of(task3Resource));
        expect(resourceMapper.map("service4-task4-arn")).andReturn(Optional.of(task4Resource));

        expect(ecsTaskUtil.buildScrapeTargets(scrapeConfig, ecsClient, cluster2, Optional.of("service3"), task3))
                .andReturn(ImmutableList.of(mockStaticConfig));
        expect(ecsTaskUtil.buildScrapeTargets(scrapeConfig, ecsClient, cluster2, Optional.of("service4"), task4))
                .andReturn(ImmutableList.of(mockStaticConfig, mockStaticConfig));

        basicMetricCollector.recordLatency(eq("aws_exporter_milliseconds"), anyObject(SortedMap.class), anyLong());

        replayAll();
        assertTrue(testClass.getTasksByCluster().isEmpty());
        testClass.buildNewTargets(AWSAccount.builder().build(), scrapeConfig, clusterWiseARNs, ecsClient);
        assertFalse(testClass.getTasksByCluster().isEmpty());

        assertTrue(testClass.getTasksByCluster().containsKey(cluster1));
        assertEquals(ImmutableMap.of(
                task1Resource, ImmutableList.of(mockStaticConfig),
                task2Resource, ImmutableList.of(mockStaticConfig)
        ), testClass.getTasksByCluster().get(cluster1));
        assertTrue(testClass.getTasksByCluster().containsKey(cluster2));
        assertEquals(ImmutableMap.of(
                task3Resource, ImmutableList.of(mockStaticConfig),
                task4Resource, ImmutableList.of(mockStaticConfig, mockStaticConfig)
        ), testClass.getTasksByCluster().get(cluster2));
        verifyAll();
    }
}
