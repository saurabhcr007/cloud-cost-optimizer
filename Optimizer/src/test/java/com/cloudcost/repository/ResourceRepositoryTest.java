package com.cloudcost.repository;

import com.cloudcost.model.Resource;
import com.cloudcost.model.ResourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResourceRepositoryTest {

    @Mock
    private ResourceRepository repository;

    @Test
    void testFindByResourceIdAndType() {
        Resource testResource = Resource.builder()
            .id(1L)
            .resourceId("i-123")
            .resourceType(ResourceType.EC2_INSTANCE)
            .region("us-east-1")
            .accountId("123456789012")
            .name("m5.large")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .version(1L)
            .build();

        when(repository.findByResourceIdAndResourceType("i-123", ResourceType.EC2_INSTANCE))
            .thenReturn(Optional.of(testResource));

        Optional<Resource> result = repository.findByResourceIdAndResourceType("i-123", ResourceType.EC2_INSTANCE);

        assertThat(result).isPresent();
        assertThat(result.get().getResourceId()).isEqualTo("i-123");
        assertThat(result.get().getResourceType()).isEqualTo(ResourceType.EC2_INSTANCE);
        verify(repository).findByResourceIdAndResourceType("i-123", ResourceType.EC2_INSTANCE);
    }

    @Test
    void testFindByResourceIdAndTypeNotFound() {
        when(repository.findByResourceIdAndResourceType("i-999", ResourceType.EC2_INSTANCE))
            .thenReturn(Optional.empty());

        Optional<Resource> result = repository.findByResourceIdAndResourceType("i-999", ResourceType.EC2_INSTANCE);

        assertThat(result).isEmpty();
    }

    @Test
    void testFindByAccountId() {
        List<Resource> resources = List.of(
            Resource.builder().resourceId("i-1").resourceType(ResourceType.EC2_INSTANCE).accountId("123").build(),
            Resource.builder().resourceId("i-2").resourceType(ResourceType.RDS_INSTANCE).accountId("123").build()
        );

        when(repository.findByAccountId("123456789012")).thenReturn(resources);

        List<Resource> result = repository.findByAccountId("123456789012");

        assertThat(result).hasSize(2);
        verify(repository).findByAccountId("123456789012");
    }

    @Test
    void testFindByTypeAndRegion() {
        List<Resource> resources = List.of(
            Resource.builder().resourceId("i-1").resourceType(ResourceType.EC2_INSTANCE).region("us-east-1").build()
        );

        when(repository.findByResourceTypeAndRegion(ResourceType.EC2_INSTANCE, "us-east-1")).thenReturn(resources);

        List<Resource> result = repository.findByResourceTypeAndRegion(ResourceType.EC2_INSTANCE, "us-east-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getResourceType()).isEqualTo(ResourceType.EC2_INSTANCE);
        assertThat(result.get(0).getRegion()).isEqualTo("us-east-1");
    }

    @Test
    void testExistsByResourceIdAndType() {
        when(repository.existsByResourceIdAndResourceType("i-123", ResourceType.EC2_INSTANCE)).thenReturn(true);

        boolean exists = repository.existsByResourceIdAndResourceType("i-123", ResourceType.EC2_INSTANCE);

        assertThat(exists).isTrue();
    }

    @Test
    void testSave() {
        Resource resource = Resource.builder()
            .resourceId("i-new")
            .resourceType(ResourceType.EC2_INSTANCE)
            .region("us-east-1")
            .accountId("123456789012")
            .name("m5.large")
            .build();

        when(repository.save(any(Resource.class))).thenReturn(resource);

        Resource saved = repository.save(resource);

        assertThat(saved.getResourceId()).isEqualTo("i-new");
        verify(repository).save(resource);
    }
}