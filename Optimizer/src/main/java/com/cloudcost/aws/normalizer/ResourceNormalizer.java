package com.cloudcost.aws.normalizer;

import com.cloudcost.model.Resource;
import com.cloudcost.model.ResourceType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ResourceNormalizer {

    @Value("${aws.default.region:us-east-1}")
    private String defaultRegion;

    public ResourceData normalizeToResourceData(String resourceId, String name, String description,
                                                Map<String, String> rawTags, Map<String, String> rawMetrics) {
        Map<String, String> tags = rawTags != null ? rawTags : Collections.emptyMap();
        Map<String, Double> metrics = convertMetrics(rawMetrics);

        return new ResourceData(
            resourceId,
            name,
            description,
            tags,
            metrics,
            Map.of(
                "normalizedAt", Instant.now().toString(),
                "region", defaultRegion
            )
        );
    }

    public Resource normalizeToResource(ResourceData data, String region, String accountId, ResourceType type) {
        return Resource.builder()
            .resourceId(data.resourceId())
            .resourceType(type)
            .region(region != null ? region : defaultRegion)
            .accountId(accountId)
            .name(data.name())
            .description(data.description())
            .tags(data.tags())
            .metrics(data.metrics())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .version(1L)
            .build();
    }

    private Map<String, Double> convertMetrics(Map<String, String> rawMetrics) {
        if (rawMetrics == null || rawMetrics.isEmpty()) {
            return Collections.emptyMap();
        }

        return rawMetrics.entrySet().stream()
            .filter(entry -> entry.getValue() != null)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                    try {
                        return Double.parseDouble(entry.getValue());
                    } catch (NumberFormatException e) {
                        return 0.0;
                    }
                }
            ));
    }
}