/*
 * Copyright © 2021
 * Asserts, Inc. - All Rights Reserved
 */
package ai.asserts.aws.cloudwatch.config;

import ai.asserts.aws.cloudwatch.model.MetricStat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ScrapeConfigProviderTest {

    @Test
    void validWithDefaults() {
        MetricConfig metricConfig = MetricConfig.builder()
                .name("Invocations")
                .stats(ImmutableSet.of(MetricStat.Sum))
                .build();
        NamespaceConfig namespaceConfig = NamespaceConfig.builder()
                .name("AWS/Lambda")
                .metrics(ImmutableList.of(metricConfig))
                .build();
        ScrapeConfig scrapeConfig = ScrapeConfig.builder()
                .regions(ImmutableSet.of("region1"))
                .namespaces(ImmutableList.of(namespaceConfig))
                .build();
        ScrapeConfigProvider testClass = new ScrapeConfigProvider() {
            @Override
            public ScrapeConfig getScrapeConfig() {
                return super.getScrapeConfig();
            }
        };
        testClass.validateConfig(scrapeConfig);
        assertEquals(60, namespaceConfig.getScrapeInterval());
        assertEquals(300, namespaceConfig.getPeriod());
        assertEquals(60, metricConfig.getScrapeInterval());
        assertEquals(300, metricConfig.getPeriod());
    }

    @Test
    void validWith_TopLevel_Defaults() {
        MetricConfig metricConfig = MetricConfig.builder()
                .name("Invocations")
                .stats(ImmutableSet.of(MetricStat.Sum))
                .build();
        NamespaceConfig namespaceConfig = NamespaceConfig.builder()
                .name("AWS/Lambda")
                .metrics(ImmutableList.of(metricConfig))
                .build();
        ScrapeConfig scrapeConfig = ScrapeConfig.builder()
                .regions(ImmutableSet.of("region1"))
                .scrapeInterval(600)
                .period(300)
                .namespaces(ImmutableList.of(namespaceConfig))
                .build();
        ScrapeConfigProvider testClass = new ScrapeConfigProvider() {
            @Override
            public ScrapeConfig getScrapeConfig() {
                return super.getScrapeConfig();
            }
        };
        testClass.validateConfig(scrapeConfig);
        assertEquals(600, namespaceConfig.getScrapeInterval());
        assertEquals(300, namespaceConfig.getPeriod());
        assertEquals(600, metricConfig.getScrapeInterval());
        assertEquals(300, metricConfig.getPeriod());
    }

    @Test
    void validWith_NSLevel_Defaults() {
        MetricConfig metricConfig = MetricConfig.builder()
                .name("Invocations")
                .stats(ImmutableSet.of(MetricStat.Sum))
                .build();
        NamespaceConfig namespaceConfig = NamespaceConfig.builder()
                .name("AWS/Lambda")
                .scrapeInterval(600)
                .period(300)
                .metrics(ImmutableList.of(metricConfig))
                .dimensions(ImmutableSet.of("dimension1"))
                .build();
        ScrapeConfig scrapeConfig = ScrapeConfig.builder()
                .regions(ImmutableSet.of("region1"))
                .scrapeInterval(60)
                .period(300)
                .namespaces(ImmutableList.of(namespaceConfig))
                .build();
        ScrapeConfigProvider testClass = new ScrapeConfigProvider() {
            @Override
            public ScrapeConfig getScrapeConfig() {
                return super.getScrapeConfig();
            }
        };
        testClass.validateConfig(scrapeConfig);
        assertEquals(600, namespaceConfig.getScrapeInterval());
        assertEquals(300, namespaceConfig.getPeriod());
        assertEquals(600, metricConfig.getScrapeInterval());
        assertEquals(300, metricConfig.getPeriod());
        assertEquals(ImmutableSet.of("dimension1"), metricConfig.getDimensions());
    }

    @Test
    void validWith_No_Defaults() {
        MetricConfig metricConfig = MetricConfig.builder()
                .name("Invocations")
                .stats(ImmutableSet.of(MetricStat.Sum))
                .scrapeInterval(900)
                .period(60)
                .build();
        NamespaceConfig namespaceConfig = NamespaceConfig.builder()
                .name("AWS/Lambda")
                .scrapeInterval(600)
                .period(300)
                .metrics(ImmutableList.of(metricConfig))
                .build();
        ScrapeConfig scrapeConfig = ScrapeConfig.builder()
                .regions(ImmutableSet.of("region1"))
                .scrapeInterval(60)
                .period(300)
                .namespaces(ImmutableList.of(namespaceConfig))
                .build();
        ScrapeConfigProvider testClass = new ScrapeConfigProvider() {
            @Override
            public ScrapeConfig getScrapeConfig() {
                return super.getScrapeConfig();
            }
        };
        testClass.validateConfig(scrapeConfig);
        assertEquals(600, namespaceConfig.getScrapeInterval());
        assertEquals(300, namespaceConfig.getPeriod());
        assertEquals(900, metricConfig.getScrapeInterval());
        assertEquals(60, metricConfig.getPeriod());
    }
}
