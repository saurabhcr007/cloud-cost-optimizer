package com.cloudcost.aws.normalizer;

import com.cloudcost.model.Resource;
import com.cloudcost.model.ResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ResourceNormalizerTest {

    private ResourceNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new ResourceNormalizer();
        // Set default region via reflection since @Value won't work in test
        try {
            var field = ResourceNormalizer.class.getDeclaredField("defaultRegion");
            field.setAccessible(true);
            field.set(normalizer, "us-east-1");
        } catch (Exception e) {
            // ignore
        }
    }

    @Test
    void testNormalizeToResourceData() {
        Map<String, String> rawTags = Map.of("Environment", "Production");
        Map<String, String> rawMetrics = Map.of("cpu", "80", "memory", "4096");

        ResourceData result = normalizer.normalizeToResourceData(
            "i-123", "m5.large", "Test instance", rawTags, rawMetrics
        );

        assertThat(result.resourceId()).isEqualTo("i-123");
        assertThat(result.name()).isEqualTo("m5.large");
        assertThat(result.description()).isEqualTo("Test instance");
        assertThat(result.tags()).isEqualTo(rawTags);
        assertThat(result.metrics()).containsKey("cpu");
        assertThat(result.metrics().get("cpu")).isEqualTo(80.0);
        assertThat(result.metrics().get("memory")).isEqualTo(4096.0);
        assertThat(result.additionalInfo()).containsKey("normalizedAt");
        assertThat(result.additionalInfo()).containsKey("region");
    }

    @Test
    void testNormalizeToResourceDataWithNullValues() {
        ResourceData result = normalizer.normalizeToResourceData(
            "i-456", "t3.micro", null, null, null
        );

        assertThat(result.resourceId()).isEqualTo("i-456");
        assertThat(result.name()).isEqualTo("t3.micro");
        assertThat(result.description()).isNull();
        assertThat(result.tags()).isEmpty();
        assertThat(result.metrics()).isEmpty();
    }

    @Test
    void testNormalizeToResource() {
        ResourceData data = new ResourceData(
            "i-123",
            "m5.large",
            "Test instance",
            Map.of("Env", "Prod"),
            Map.of("cpu", 80.0),
            Map.of()
        );

        Resource result = normalizer.normalizeToResource(data, "us-west-2", "123456789012", ResourceType.EC2_INSTANCE);

        assertThat(result.getResourceId()).isEqualTo("i-123");
        assertThat(result.getResourceType()).isEqualTo(ResourceType.EC2_INSTANCE);
        assertThat(result.getRegion()).isEqualTo("us-west-2");
        assertThat(result.getAccountId()).isEqualTo("123456789012");
        assertThat(result.getName()).isEqualTo("m5.large");
        assertThat(result.getTags()).isEqualTo(Map.of("Env", "Prod"));
        assertThat(result.getMetrics()).isEqualTo(Map.of("cpu", 80.0));
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotNull();
        assertThat(result.getVersion()).isEqualTo(1L);
    }

    @Test
    void testNormalizeToResourceWithNullRegion() {
        ResourceData data = new ResourceData(
            "i-789",
            "db.t3.medium",
            null,
            Map.of(),
            Map.of(),
            Map.of()
        );

        Resource result = normalizer.normalizeToResource(data, null, "999999999999", ResourceType.RDS_INSTANCE);

        assertThat(result.getRegion()).isEqualTo("us-east-1"); // default region
        assertThat(result.getResourceType()).isEqualTo(ResourceType.RDS_INSTANCE);
    }

    @Test
    void testConvertMetrics() {
        Map<String, String> rawMetrics = Map.of(
            "cpu", "80",
            "memory", "4096",
            "invalid", "not-a-number",
            "null-value", null
        );

        ResourceData result = normalizer.normalizeToResourceData("i-1", "test", "desc", Collections.emptyMap(), rawMetrics);

        assertThat(result.metrics().get("cpu")).isEqualTo(80.0);
        assertThat(result.metrics().get("memory")).isEqualTo(4096.0);
        assertThat(result.metrics().get("invalid")).isEqualTo(0.0);
        assertThat(result.metrics()).doesNotContainKey("null-value");
    }
}