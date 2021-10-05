/*
 * Copyright © 2021
 * Asserts, Inc. - All Rights Reserved
 */
package ai.asserts.aws;

import ai.asserts.aws.cloudwatch.model.CWNamespace;
import ai.asserts.aws.cloudwatch.model.MetricStat;
import com.google.common.collect.ImmutableMap;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cloudwatch.model.Metric;

import java.util.Map;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;

@Component
public class MetricNameUtil {
    private final Map<String, String> NAMESPACE_TO_METRIC_PREFIX = new ImmutableMap.Builder<String, String>()
            .put(CWNamespace.lambda.getNamespace(), "aws_lambda")
            .put(CWNamespace.sqs.getNamespace(), "aws_sqs")
            .put(CWNamespace.s3.getNamespace(), "aws_s3")
            .put(CWNamespace.dynamodb.getNamespace(), "aws_dynamodb")
            .build();

    public String exportedMetricName(Metric metric, MetricStat metricStat) {
        String namespace = metric.namespace();
        String metricPrefix = getMetricPrefix(namespace);
        return format("%s_%s_%s", metricPrefix, toSnakeCase(metric.metricName()),
                metricStat.getShortForm().toLowerCase());

    }

    public String exportedMetric(Metric metric, MetricStat metricStat) {
        return String.format("%s{%s}", exportedMetricName(metric, metricStat),
                metric.dimensions().stream()
                        .map(dimension -> format("d_%s=\"%s\"", toSnakeCase(dimension.name()), dimension.value()))
                        .collect(joining(", ")));
    }

    public String getMetricPrefix(String namespace) {
        return NAMESPACE_TO_METRIC_PREFIX.get(namespace);
    }

    public String toSnakeCase(String input) {
        StringBuilder builder = new StringBuilder();
        boolean lastCaseWasSmall = false;
        int numContiguousUpperCase = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isUpperCase(c) && lastCaseWasSmall) {
                builder.append("_");
            } else if (Character.isLowerCase(c) && numContiguousUpperCase > 1) {
                char lastUpperCaseLetter = builder.toString().charAt(builder.length() - 1);
                builder.deleteCharAt(builder.length() - 1);
                builder.append("_");
                builder.append(lastUpperCaseLetter);
            }
            builder.append(c);
            lastCaseWasSmall = Character.isLowerCase(c);
            if (Character.isUpperCase(c)) {
                numContiguousUpperCase++;
            } else {
                numContiguousUpperCase = 0;
            }
        }
        return builder.toString().toLowerCase();
    }
}
