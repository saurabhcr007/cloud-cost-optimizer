package com.cloudcost.repository;

import com.cloudcost.model.Resource;
import com.cloudcost.model.ResourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, Long> {

    Optional<Resource> findByResourceIdAndResourceType(String resourceId, ResourceType resourceType);

    List<Resource> findByAccountId(String accountId);

    List<Resource> findByResourceTypeAndRegion(ResourceType resourceType, String region);

    @Query("SELECT r FROM Resource r WHERE " +
           "(:accountId IS NULL OR r.accountId = :accountId) AND " +
           "(:resourceType IS NULL OR r.resourceType = :resourceType) AND " +
           "(:region IS NULL OR r.region = :region) AND " +
           "(:namePattern IS NULL OR r.name LIKE %:namePattern%) AND " +
           "(:createdAfter IS NULL OR r.createdAt >= :createdAfter) AND " +
           "(:createdBefore IS NULL OR r.createdAt <= :createdBefore)")
    Page<Resource> findByFilters(
        @Param("accountId") String accountId,
        @Param("resourceType") ResourceType resourceType,
        @Param("region") String region,
        @Param("namePattern") String namePattern,
        @Param("createdAfter") java.time.Instant createdAfter,
        @Param("createdBefore") java.time.Instant createdBefore,
        Pageable pageable
    );

    boolean existsByResourceIdAndResourceType(String resourceId, ResourceType resourceType);
}