package com.cloudcost.service;

import com.cloudcost.aws.normalizer.ResourceData;
import com.cloudcost.aws.normalizer.ResourceNormalizer;
import com.cloudcost.aws.scanner.AwsResourceScanner;
import com.cloudcost.model.Resource;
import com.cloudcost.model.ResourceType;
import com.cloudcost.repository.ResourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResourceIngestionServiceTest {

    @Mock
    private AwsResourceScanner scanner;

    @Mock
    private ResourceNormalizer normalizer;

    @Mock
    private ResourceRepository repository;

    @InjectMocks
    private ResourceIngestionService ingestionService;

    @Test
    void testScanAndIngest() {
        String accountId = "123456789012";
        List<String> regions = List.of("us-east-1");

        Map<ResourceType, List<ResourceData>> scanResults = Map.of(
            ResourceType.EC2_INSTANCE, List.of(
                new ResourceData("i-123", "m5.large", null, Map.of(), Map.of(), Map.of())
            )
        );

        when(scanner.scanRegion(accountId, "us-east-1")).thenReturn(scanResults);

        Resource testResource = Resource.builder()
            .resourceId("i-123")
            .resourceType(ResourceType.EC2_INSTANCE)
            .region("us-east-1")
            .accountId(accountId)
            .name("m5.large")
            .build();

        when(normalizer.normalizeToResource(
            eq(new ResourceData("i-123", "m5.large", null, Map.of(), Map.of(), Map.of())),
            eq("us-east-1"), eq(accountId), eq(ResourceType.EC2_INSTANCE)
        )).thenReturn(testResource);

        when(repository.save(any(Resource.class))).thenReturn(testResource);

        IngestionResult result = ingestionService.scanAndIngest(accountId, regions);

        assertThat(result.totalResources()).isEqualTo(1);
        assertThat(result.timestamp()).isEqualTo(LocalDate.now());
        verify(scanner).scanRegion(accountId, "us-east-1");
        verify(repository).save(any(Resource.class));
    }

    @Test
    void testScanAndIngestMultipleRegions() {
        String accountId = "123456789012";
        List<String> regions = List.of("us-east-1", "us-west-2");

        Map<ResourceType, List<ResourceData>> scanResults1 = Map.of(
            ResourceType.EC2_INSTANCE, List.of(new ResourceData("i-1", "m5.large", null, Map.of(), Map.of(), Map.of()))
        );
        Map<ResourceType, List<ResourceData>> scanResults2 = Map.of(
            ResourceType.RDS_INSTANCE, List.of(new ResourceData("db-1", "db.t3.medium", null, Map.of(), Map.of(), Map.of()))
        );

        when(scanner.scanRegion(accountId, "us-east-1")).thenReturn(scanResults1);
        when(scanner.scanRegion(accountId, "us-west-2")).thenReturn(scanResults2);

        Resource resource1 = Resource.builder().resourceId("i-1").resourceType(ResourceType.EC2_INSTANCE).build();
        Resource resource2 = Resource.builder().resourceId("db-1").resourceType(ResourceType.RDS_INSTANCE).build();

        when(normalizer.normalizeToResource(
            eq(new ResourceData("i-1", "m5.large", null, Map.of(), Map.of(), Map.of())),
            eq("us-east-1"), eq(accountId), eq(ResourceType.EC2_INSTANCE)
        )).thenReturn(resource1);
        when(normalizer.normalizeToResource(
            eq(new ResourceData("db-1", "db.t3.medium", null, Map.of(), Map.of(), Map.of())),
            eq("us-west-2"), eq(accountId), eq(ResourceType.RDS_INSTANCE)
        )).thenReturn(resource2);

        when(repository.save(any(Resource.class))).thenReturn(resource1, resource2);

        IngestionResult result = ingestionService.scanAndIngest(accountId, regions);

        assertThat(result.totalResources()).isEqualTo(2);
        verify(scanner).scanRegion(accountId, "us-east-1");
        verify(scanner).scanRegion(accountId, "us-west-2");
    }

    @Test
    void testScanAndIngestWithDuplicates() {
        String accountId = "123456789012";
        List<String> regions = List.of("us-east-1");

        Map<ResourceType, List<ResourceData>> scanResults = Map.of(
            ResourceType.EC2_INSTANCE, List.of(new ResourceData("i-123", "m5.large", null, Map.of(), Map.of(), Map.of()))
        );

        when(scanner.scanRegion(accountId, "us-east-1")).thenReturn(scanResults);

        Resource testResource = Resource.builder().resourceId("i-123").resourceType(ResourceType.EC2_INSTANCE).build();

        when(normalizer.normalizeToResource(
            eq(new ResourceData("i-123", "m5.large", null, Map.of(), Map.of(), Map.of())),
            eq("us-east-1"), eq(accountId), eq(ResourceType.EC2_INSTANCE)
        )).thenReturn(testResource);

        when(repository.save(any(Resource.class)))
            .thenReturn(testResource)
            .thenThrow(new com.cloudcost.exception.DuplicateResourceException("Duplicate"));

        IngestionResult result = ingestionService.scanAndIngest(accountId, regions);

        assertThat(result.totalResources()).isEqualTo(1);
        verify(repository, times(1)).save(any(Resource.class));
    }
}