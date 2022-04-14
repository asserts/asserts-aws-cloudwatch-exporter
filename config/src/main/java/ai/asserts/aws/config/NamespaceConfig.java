
package ai.asserts.aws.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static java.lang.String.format;

@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class NamespaceConfig {
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ScrapeConfig scrapeConfig;
    private String name;
    private Integer period;
    private Integer scrapeInterval;
    private Map<String, String> dimensionFilters;
    private Map<String, Pattern> dimensionFilterPattern;

    private Map<String, Set<String>> tagFilters;
    private List<MetricConfig> metrics;
    private List<LogScrapeConfig> logs;

    public void validate(int index) {
        List<String> errors = new ArrayList<>();
        if (!StringUtils.hasLength(name)) {
            errors.add(format("namespace[%d].name not specified", index));
        }
        if (scrapeInterval != null && (scrapeInterval < 60 || scrapeInterval % 60 != 0)) {
            errors.add(format("namespace[%d].scrapeInterval has to be a multiple of 60", index));
        } else if (period != null && (period < 60 || period % 60 != 0)) {
            errors.add(format("namespace[%d].period has to be a multiple of 60", index));
        }
        if (errors.size() > 0) {
            throw new RuntimeException(String.join("\n", errors));
        }

        if (!CollectionUtils.isEmpty(metrics)) {
            for (int j = 0; j < metrics.size(); j++) {
                MetricConfig metricConfig = metrics.get(j);
                metricConfig.setNamespace(this);
                metricConfig.validate(j);
            }
        }

        if (!CollectionUtils.isEmpty(logs)) {
            logs.forEach(LogScrapeConfig::initialize);
        }

        if (!CollectionUtils.isEmpty(dimensionFilters)) {
            dimensionFilterPattern = new TreeMap<>();
            dimensionFilters.forEach((dimension, patternString) ->
                    dimensionFilterPattern.put(dimension, Pattern.compile(patternString)));
        }
    }

    public Integer getScrapeInterval() {
        if (scrapeInterval != null) {
            return scrapeInterval;
        } else {
            return scrapeConfig.getScrapeInterval();
        }
    }

    public boolean hasTagFilters() {
        return tagFilters != null && tagFilters.size() > 0;
    }
}
