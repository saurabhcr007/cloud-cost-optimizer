package com.cloudcost.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ResourceTest {

    @Test
    void testResourceBuilderAndGetters() {
        Map<String, String> tags = Map.of("Environment", "Production");
        Map<String, Double> metrics = Map.of("cpu", 80.0);
        Instant now = Instant.now();

        Resource resource = Resource.builder()
            .resourceId("i-123456")
            .resourceType(ResourceType.EC2_INSTANCE)
            .region("us-east-1")
            .accountId("123456789012")
            .name("m5.large")
            .description("EC2 instance")
            .tags(tags)
            .metrics(metrics)
            .createdAt(now)
            .updatedAt(now)
            .version(1L)
            .build();

        assertThat(resource.getResourceId()).isEqualTo("i-123456");
        assertThat(resource.getResourceType()).isEqualTo(ResourceType.EC2_INSTANCE);
        assertThat(resource.getName()).isEqualTo("m5.large");
        assertThat(resource.getTags()).isEqualTo(tags);
        assertThat(resource.getMetrics()).isEqualTo(metrics);
        assertThat(resource.getRegion()).isEqualTo("us-east-1");
        assertThat(resource.getAccountId()).isEqualTo("123456789012");
        assertThat(resource.getDescription()).isEqualTo("EC2 instance");
        assertThat(resource.getCreatedAt()).isEqualTo(now);
        assertThat(resource.getUpdatedAt()).isEqualTo(now);
        assertThat(resource.getVersion()).isEqualTo(1L);
    }

    @Test
    void testResourceDefaultConstructor() {
        Resource resource = new Resource();
        assertThat(resource).isNotNull();
    }

    @Test
    void testResourceSetters() {
        Resource resource = new Resource();
        resource.setResourceId("i-999");
        resource.setResourceType(ResourceType.RDS_INSTANCE);
        resource.setRegion("us-west-2");
        resource.setAccountId("999999999999");
        resource.setName("db.t3.medium");
        resource.setDescription("RDS instance");

        assertThat(resource.getResourceId()).isEqualTo("i-999");
        assertThat(resource.getResourceType()).isEqualTo(ResourceType.RDS_INSTANCE);
        assertThat(resource.getRegion()).isEqualTo("us-west-2");
        assertThat(resource.getAccountId()).isEqualTo("999999999999");
        assertThat(resource.getName()).isEqualTo("db.t3.medium");
        assertThat(resource.getDescription()).isEqualTo("RDS instance");
    }

    @Test
    void testResourceTypeFromValue() {
        assertThat(ResourceType.fromValue("ec2")).isEqualTo(ResourceType.EC2_INSTANCE);
        assertThat(ResourceType.fromValue("EC2")).isEqualTo(ResourceType.EC2_INSTANCE);
        assertThat(ResourceType.fromValue("rds")).isEqualTo(ResourceType.RDS_INSTANCE);
        assertThat(ResourceType.fromValue("ebs")).isEqualTo(ResourceType.EBS_VOLUME);
        assertThat(ResourceType.fromValue("eip")).isEqualTo(ResourceType.ELASTIC_IP);
        assertThat(ResourceType.fromValue("s3")).isEqualTo(ResourceType.S3_BUCKET);
        assertThat(ResourceType.fromValue("snapshot")).isEqualTo(ResourceType.SNAPSHOT);
        assertThat(ResourceType.fromValue("unknown")).isEqualTo(ResourceType.UNKNOWN);
        assertThat(ResourceType.fromValue("invalid")).isEqualTo(ResourceType.UNKNOWN);
        assertThat(ResourceType.fromValue(null)).isEqualTo(ResourceType.UNKNOWN);
    }

    @Test
    void testResourceTypeGetValue() {
        assertThat(ResourceType.EC2_INSTANCE.getValue()).isEqualTo("ec2");
        assertThat(ResourceType.RDS_INSTANCE.getValue()).isEqualTo("rds");
        assertThat(ResourceType.EBS_VOLUME.getValue()).isEqualTo("ebs");
        assertThat(ResourceType.ELASTIC_IP.getValue()).isEqualTo("eip");
        assertThat(ResourceType.S3_BUCKET.getValue()).isEqualTo("s3");
        assertThat(ResourceType.SNAPSHOT.getValue()).isEqualTo("snapshot");
        assertThat(ResourceType.UNKNOWN.getValue()).isEqualTo("unknown");
    }
}