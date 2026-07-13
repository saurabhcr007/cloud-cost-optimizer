package com.cloudcost.service;

import com.cloudcost.aws.normalizer.ResourceData;
import com.cloudcost.aws.normalizer.ResourceNormalizer;
import com.cloudcost.aws.scanner.AwsResourceScanner;
import com.cloudcost.exception.DuplicateResourceException;
import com.cloudcost.exception.ResourceScanException;
import com.cloudcost.model.Resource;
import com.cloudcost.model.ResourceType;
import com.cloudcost.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ResourceIngestionService {
    private final AwsResourceScanner scanner;
    private final ResourceNormalizer normalizer;
    private final ResourceRepository repository;

    private static final int BATCH_SIZE = 100;

    @Retryable(value = { Exception.class }, maxAttempts = 3, backoff = @org.springframework.retry.annotation.Backoff(delay = 1000, multiplier = 2))
    public IngestionResult scanAndIngest(String accountId, List<String> regions) {
        log.info("Starting resource ingestion for account: {}, regions: {}", accountId, regions);

        Map<ResourceType, List<ResourceData>> scanResults = new HashMap<>();

        for (String region : regions) {
            try {
                Map<ResourceType, List<ResourceData>> regionResults = scanner.scanRegion(accountId, region);
                
                regionResults.forEach((type, resources) -> 
                    scanResults.merge(type, resources, (existing, newData) -> {
                        existing.addAll(newData);
                        return existing;
                    })
                );
            } catch (Exception e) {
                log.error("Failed to scan region {} for account {}", region, accountId, e);
                throw new ResourceScanException("Scan failed for region: " + region, e);
            }
        }

        List<Resource> resources = new ArrayList<>();
        for (Map.Entry<ResourceType, List<ResourceData>> entry : scanResults.entrySet()) {
            List<Resource> normalized = entry.getValue().stream()
                .map(data -> normalizer.normalizeToResource(data, region, accountId, entry.getKey()))
                .collect(Collectors.toList());
            
            resources.addAll(batchSave(normalized, accountId));
        }

        log.info("Successfully ingested {} resources for account {}", resources.size(), accountId);
        return new IngestionResult(resources.size(), LocalDate.now());
    }

    private List<Resource> batchSave(List<Resource> resources, String accountId) {
        List<Resource> saved = new ArrayList<>();
        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);
            try {
                saved.add(repository.save(resource));
            } catch (DuplicateResourceException e) {
                log.debug("Resource already exists: {} - {}", resource.getResourceId(), resource.getResourceType());
            }
            if ((i + 1) % BATCH_SIZE == 0) {
                repository.flush();
            }
        }
        return saved;
    }
}

record IngestionResult(int totalResources, LocalDate timestamp, List<String> errors) {
    public IngestionResult(int totalResources, LocalDate timestamp) {
        this(totalResources, timestamp, new ArrayList<>());
    }
}