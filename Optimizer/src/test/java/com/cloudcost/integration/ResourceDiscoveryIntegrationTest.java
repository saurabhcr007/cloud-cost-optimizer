package com.cloudcost.integration;

import com.cloudcost.aws.normalizer.ResourceData;
import com.cloudcost.aws.normalizer.ResourceNormalizer;
import com.cloudcost.aws.scanner.AwsResourceScanner;
import com.cloudcost.config.IntegrationTestConfig;
import com.cloudcost.exception.DuplicateResourceException;
import com.cloudcost.model.Resource;
import com.cloudcost.model.ResourceType;
import com.cloudcost.repository.ResourceRepository;
import com.cloudcost.service.ResourceIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(IntegrationTestConfig.class)
class ResourceDiscoveryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ResourceRepository repository;

    @MockBean
    private AwsResourceScanner scanner;

    @MockBean
    private ResourceNormalizer normalizer;

    @Autowired
    private ResourceIngestionService ingestionService;

    @Test
    void testFullResourceDiscoveryFlow() {
        String accountId = "123456789012";
        List<String> regions = List.of("us-east-1");

        Map<ResourceType, List<ResourceData>> scanResults = Map.of(
            ResourceType.EC2_INSTANCE, List.of(
                new ResourceData("i-123456", "m5.large", "Test EC2 Instance", 
                    Map.of("Environment", "Production"),
                    Map.of("cpu", 80.0, "memory", 8192.0),
                    Map.of())
            ),
            ResourceType.RDS_INSTANCE, List.of(
                new ResourceData("db-789012", "db.t3.medium", "Test RDS Instance",
                    Map.of("Environment", "Production"),
                    Map.of("allocatedStorage", 100.0),
                    Map.of())
            )
        );

        when(scanner.scanRegion(accountId, "us-east-1")).thenReturn(scanResults);

        Resource ec2Resource = Resource.builder()
            .resourceId("i-123456")
            .resourceType(ResourceType.EC2_INSTANCE)
            .region("us-east-1")
            .accountId(accountId)
            .name("m5.large")
            .description("Test EC2 Instance")
            .tags(Map.of("Environment", "Production"))
            .metrics(Map.of("cpu", 80.0, "memory", 8192.0))
            .build();

        Resource rdsResource = Resource.builder()
            .resourceId("db-789012")
            .resourceType(ResourceType.RDS_INSTANCE)
            .region("us-east-1")
            .accountId(accountId)
            .name("db.t3.medium")
            .description("Test RDS Instance")
            .tags(Map.of("Environment", "Production"))
            .metrics(Map.of("allocatedStorage", 100.0))
            .build();

        when(normalizer.normalizeToResource(
            eq(new ResourceData("i-123456", "m5.large", "Test EC2 Instance", Map.of("Environment", "Production"), Map.of("cpu", 80.0, "memory", 8192.0), Map.of())),
            eq("us-east-1"), eq(accountId), eq(ResourceType.EC2_INSTANCE)
        )).thenReturn(ec2Resource);

        when(normalizer.normalizeToResource(
            eq(new ResourceData("db-789012", "db.t3.medium", "Test RDS Instance", Map.of("Environment", "Production"), Map.of("allocatedStorage", 100.0), Map.of())),
            eq("us-east-1"), eq(accountId), eq(ResourceType.RDS_INSTANCE)
        )).thenReturn(rdsResource);

        when(repository.save(any(Resource.class))).thenAnswer(inv -> inv.getArgument(0));

        ResourceIngestionService.IngestionResult result = ingestionService.scanAndIngest(accountId, regions);

        assertThat(result.totalResources()).isEqualTo(2);
        assertThat(result.timestamp()).isEqualTo(LocalDate.now());

        List<Resource> savedResources = repository.findAll();
        assertThat(savedResources).hasSize(2);

        Resource savedEc2 = savedResources.stream()
            .filter(r -> r.getResourceType() == ResourceType.EC2_INSTANCE)
            .findFirst().orElseThrow();
        assertThat(savedEc2.getResourceId()).isEqualTo("i-123456");
        assertThat(savedEc2.getName()).isEqualTo("m5.large");
        assertThat(savedEc2.getTags()).containsEntry("Environment", "Production");

        verify(scanner).scanRegion(accountId, "us-east-1");
        verify(repository, times(2)).save(any(Resource.class));
    }

    @Test
    void testDuplicateResourceHandling() {
        String accountId = "123456789012";
        List<String> regions = List.of("us-east-1");

        Map<ResourceType, List<ResourceData>> scanResults = Map.of(
            ResourceType.EC2_INSTANCE, List.of(
                new ResourceData("i-123", "m5.large", null, Map.of(), Map.of(), Map.of())
            )
        );

        when(scanner.scanRegion(accountId, "us-east-1")).thenReturn(scanResults);

        Resource existingResource = Resource.builder()
            .resourceId("i-123")
            .resourceType(ResourceType.EC2_INSTANCE)
            .region("us-east-1")
            .accountId(accountId)
            .name("m5.large")
            .build();

        entityManager.persistAndFlush(existingResource);

        when(normalizer.normalizeToResource(
            eq(new ResourceData("i-123", "m5.large", null, Map.of(), Map.of(), Map.of())),
            eq("us-east-1"), eq(accountId), eq(ResourceType.EC2_INSTANCE)
        )).thenReturn(existingResource);

        when(repository.save(any(Resource.class)))
            .thenThrow(new DuplicateResourceException("Duplicate resource: i-123"));

        ResourceIngestionService.IngestionResult result = ingestionService.scanAndIngest(accountId, regions);

        assertThat(result.totalResources()).isEqualTo(0);
        verify(repository, never()).save(any(Resource.class));
    }
}