package com.cloudcost.aws.normalizer;

import java.util.Map;

public record ResourceData(
    String resourceId,
    String name,
    String description,
    Map<String, String> tags,
    Map<String, Double> metrics,
    Map<String, String> additionalInfo
) {}