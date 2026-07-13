# Resource Discovery Subsystem Design

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Automatically discover and catalog AWS resources for cost analysis by scanning EC2, RDS, EBS, Elastic IP, S3, and Snapshot resources

**Architecture:**
The Resource Discovery subsystem forms the foundation for all cost optimization analysis. It follows a clean architecture pattern:

- **Outside World:** AWS APIs, IAM, databases, monitoring systems
- **Boundary**: AWS scanner interface layer with rate limiting and retry logic
- **Application Core**: Resource normalization and ingestion services
- **Data Access**: PostgreSQL repository for persistent storage

Data flows through a standardized pipeline:
1. AWS Scanner retrieves raw resource data via AWS SDK v2
2. Resource Normalizer standardizes format and extracts metadata
3. Resource Ingestion Service orchestrates the process with error handling
4. Repository persists normalized data for downstream analysis

**Tech Stack:**
- Java 21, Spring Boot 3.x
- AWS SDK v2 for Java
- PostgreSQL database
- Redis caching for resource lookup
- JUnit 5, Mockito for testing

## Global Constraints

- Spring Boot 3.x framework
- Java 21 language version
- PostgreSQL as primary database
- AWS SDK v2 compliance
- JWT-based authentication for AWS API calls
- Rate limiting to prevent API throttling
- Connection pooling for database
- 90%+ test coverage requirement

## Current State - What Exists

- Empty project directory with basic structure
- No source code files
- No database schema
- No dependencies defined

## Design Sections

### 1. Entities and Domain Models

#### Resource Entity
```java
@Entity
@Table(name = "aws_resources")
public class Resource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String resourceId;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ResourceType resourceType;
    
    @Column(nullable = false)
    private String region;
    
    @Column(nullable = false)
    private String accountId;
    
    @Column(nullable = false)
    private String name;
    
    @Column
    private String description;
    
    @ElementCollection
    @CollectionTable(name = "resource_tags", 
        joinColumns = @JoinColumn(name = "resource_id"))
    @Column(name = "tag_key")
    private Map<String, String> tags;
    
    @ElementCollection
    @CollectionTable(name = "resource_metrics", 
        joinColumns = @JoinColumn(name = "resource_id"))
    @Column(name = "metric_value")
    private Map<String, Double> metrics;
    
    @Column
    private Instant createdAt;
    
    @Column
    private Instant updatedAt;
    
    @Version
    private Long version;
}
```

#### ResourceType Enum
```java
public enum ResourceType {
    EC2_INSTANCE("ec2"),
    RDS_INSTANCE("rds"),
    EBS_VOLUME("ebs"),
    ELASTIC_IP("eip"),
    S3_BUCKET("s3"),
    SNAPSHOT("snapshot"),
    UNKNOWN("unknown");
    
    private final String value;
    
    ResourceType(String value) {
        this.value = value;
    }
    
    public static ResourceType fromValue(String value) {
        for (ResourceType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
```

### 2. Data Access Layer

#### ResourceRepository Interface
```java
public interface ResourceRepository {
    Resource save(Resource resource);
    Optional<Resource> findById(Long id);
    Optional<Resource> findByResourceIdAndType(String resourceId, ResourceType type);
    List<Resource> findByAccountId(String accountId);
    List<Resource> findByTypeAndRegion(ResourceType type, String region);
    Page<Resource> findByFilters(ResourceFilter filter, Pageable pageable);
    void deleteById(Long id);
    boolean existsByResourceIdAndType(String resourceId, ResourceType type);
}
```

#### ResourceFilter Record
```java
public record ResourceFilter(
    String accountId,
    ResourceType type,
    String region,
    String namePattern,
    Map<String, String> tags,
    Instant createdAfter,
    Instant createdBefore
) {}
```

#### ResourceRepository Implementation
```java
@Repository
@Transactional(readOnly = true)
public class ResourceRepositoryImpl implements ResourceRepository {
    @PersistenceContext
    private EntityManager entityManager;
    
    @Cacheable(value = "resources", key = "{#resourceId + '_' + #type}")
    @Override
    public Optional<Resource> findByResourceIdAndType(String resourceId, ResourceType type) {
        String jpql = "SELECT r FROM Resource r WHERE r.resourceId = :resourceId AND r.resourceType = :type";
        return entityManager.createQuery(jpql, Resource.class)
            .setParameter("resourceId", resourceId)
            .setParameter("type", type)
            .getResultStream()
            .findFirst();
    }
    
    @Transactional
    @Override
    public Resource save(Resource resource) {
        if (existsByResourceIdAndType(resource.getResourceId(), resource.getResourceType())) {
            throw new DuplicateResourceException(
                "Resource already exists: " + resource.getResourceId());
        }
        return entityManager.merge(resource);
    }
}
```

### 3. Application Services

#### ResourceIngestionService
```java
@Service
@RequiredArgsConstructor
@Transactional
public class ResourceIngestionService {
    private final AwsResourceScanner scanner;
    private final ResourceNormalizer normalizer;
    private final ResourceRepository repository;
    
    private static final int BATCH_SIZE = 100;
    private static final Logger log = LoggerFactory.getLogger(ResourceIngestionService.class);
    
    public IngestionResult scanAndIngest(String accountId, List<String> regions) {
        log.info("Starting resource ingestion for account: {}, regions: {}", accountId, regions);
        
        Map<ResourceType, List<ResourceData>> scanResults = new HashMap<>();
        
        for (String region : regions) {
            try {
                scanResults.merge(scanner.scanRegion(accountId, region), 
                    normalizer.normalize(region, accountId), 
                    (existing, newData) -> {
                        existing.addAll(newData);
                        return existing;
                    });
            } catch (AwsServiceException e) {
                log.error("Failed to scan region {} for account {}", region, accountId, e);
                throw new ResourceScanException("Scan failed for region: " + region, e);
            }
        }
        
        List<Resource> resources = new ArrayList<>();
        for (Map.Entry<ResourceType, List<ResourceData>> entry : scanResults.entrySet()) {
            List<Resource> normalized = entry.getValue().stream()
                .map(data -> normalizer.normalizeToResource(data, entry.getKey()))
                .collect(Collectors.toList());
            
            resources.addAll(batchSave(normalized, accountId));
        }
        
        log.info("Successfully ingested {} resources for account {}", resources.size(), accountId);
        return new IngestionResult(resources.size(), new Date());
    }
    
    private List<Resource> batchSave(List<Resource> resources, String accountId) {
        List<Resource> saved = new ArrayList<>();n        for (int i = 0; i < resources.size(); i++) {
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
```

#### IngestionResult Record
```java
public record IngestionResult(
    int totalResources,
    Date timestamp,
    List<String> errors
) {}
```

### 4. Infrastructure Layer

#### AwsResourceScanner
```java
@Service
@RequiredArgsConstructor
public class AwsResourceScanner {
    private final AmazonEC2Client ec2Client;
    private final AmazonRDSClient rdsClient;
    private final AmazonEBSClient ebsClient;
    private final AmazonEC2Client eipClient; // Reuse for EIP
    private final AmazonS3Client s3Client;
    
    @Retryable(value = { AwsServiceException.class }, maxAttempts = 3, 
               backoff = @Backoff(delay = 1000, multiplier = 2))
    public Map<ResourceType, List<ResourceData>> scanRegion(String accountId, String region) {
        Map<ResourceType, List<ResourceData>> results = new HashMap<>();
        
        try {
            results.put(ResourceType.EC2_INSTANCE, scanEc2Instances(region));
            results.put(ResourceType.RDS_INSTANCE, scanRdsInstances(region));
            results.put(ResourceType.EBS_VOLUME, scanEbsVolumes(region));
            results.put(ResourceType.ELASTIC_IP, scanElasticIps(region));
            results.put(ResourceType.S3_BUCKET, scanS3Buckets(region));
            results.put(ResourceType.SNAPSHOT, scanSnapshots(region));
        } catch (AmazonServiceException e) {
            throw new ResourceScanException("Failed to scan region: " + region, e);
        }
        
        return results;
    }
    
    private List<ResourceData> scanEc2Instances(String region) {
        DescribeInstancesRequest request = new DescribeInstancesRequest()
            .withFilters(new Filter("instance-state-name", "running"));
        
        return ec2Client.describeInstances(request)
            .getReservations()
            .stream()
            .flatMap(r -> r.getInstances().stream())
            .map(this::mapToResourceData)
            .collect(Collectors.toList());
    }
    
    private ResourceData mapToResourceData(Instance instance) {
        return ResourceData.builder()
            .resourceId(instance.getInstanceId())
            .name(instance.getInstanceTypeAsString())
            .description(instance.getDescription())
            .metrics(Map.of(
                "cpuUtilization", instance.getCpuUnits(),
                "memoryUsage", instance.getMemoryMb(),
                "storageIo", instance.getEbsOptimized()
            ))
            .tags(mapTags(instance.getTags()))
            .build();
    }
}
```

#### ResourceData Record
```java
@Builder
public record ResourceData(
    String resourceId,
    String name,
    String description,
    Map<String, String> tags,
    Map<String, Double> metrics,
    Map<String, String> additionalInfo
) {}
```

### 5. Infrastructure Configuration

#### Database Schema (PostgreSQL)
```sql
CREATE TABLE aws_resources (
    id BIGSERIAL PRIMARY KEY,
    resource_id VARCHAR(255) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    region VARCHAR(50) NOT NULL,
    account_id VARCHAR(255) NOT NULL,
    name VARCHAR(500) NOT NULL,
    description TEXT,
    tags JSONB,
    metrics JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    UNIQUE(resource_id, resource_type),
    INDEX idx_account_region_type (account_id, region, resource_type),
    INDEX idx_resource_type (resource_type),
    INDEX idx_created_at (created_at)
);
```

#### Application Properties
```properties
# AWS Configuration
aws.region.us-east-1= us-east-1
aws.scanner.batch.size=100
aws.scanner.max.attempts=3

# Database Configuration
db.schema=cloud_cost
db.pool.size=20
db.connection.timeout=30000

# Caching Configuration
redis.ttl.resources=3600
redis.ttl.metrics=300

# Security Configuration
jwt.token.validity=86400
jwt.refresh.token.validity=604800
```

### 6. Exception Handling

#### ResourceExceptions
```java
public class ResourceException extends RuntimeException {
    private final ErrorCode errorCode;
    private final String resourceId;
    
    public ResourceException(ErrorCode errorCode, String message, String resourceId) {
        super(message);
        this.errorCode = errorCode;
        this.resourceId = resourceId;
    }
}

public class DuplicateResourceException extends ResourceException {
    public DuplicateResourceException(String message) {
        super(ErrorCode.DUPLICATE_RESOURCE, message, null);
    }
}

public class ResourceScanException extends ResourceException {
    public ResourceScanException(String message, Throwable cause) {
        super(ErrorCode.SCAN_FAILED, message, null);
    }
}

public enum ErrorCode {
    DUPLICATE_RESOURCE,
    SCAN_FAILED,
    RESOURCE_NOT_FOUND,
    INVALID_RESOURCE_DATA
}
```

### 7. Testing Strategy

#### Unit Tests
```java
@ExtendWith(MockitoExtension.class)
class ResourceRepositoryTest {
    @Mock
    private EntityManager entityManager;
    
    @Mock
    private Query mockQuery;
    
    @InjectMocks
    private ResourceRepositoryImpl repository;
    
    @Test
    void testFindByResourceIdAndType() {
        Resource testResource = createTestResource();
        when(entityManager.createQuery(anyString(), eq(Resource.class)))
            .thenReturn(mockQuery);
        when(mockQuery.setParameter(anyString(), any()))
            .thenReturn(mockQuery);
        when(mockQuery.getResultStream()).thenReturn(Stream.of(testResource));
        
        Optional<Resource> result = repository.findByResourceIdAndType("i-123", ResourceType.EC2_INSTANCE);
        
        assertThat(result).isPresent();
        assertThat(result.get().getResourceId()).isEqualTo("i-123");
    }
}
```

#### Integration Tests
```java
@SpringBootTest
@ActiveProfiles("test")
@Container(
    value = "postgres:15-alpine",
    properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/test_db",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    }
)
class ResourceIT {
    @Autowired
    private ResourceRepository repository;
    
    @Autowired
    private ResourceIngestionService ingestionService;
    
    @Test
    @DirtiesContext
    void testEndToEndResourceFlow() {
        String accountId = "123456789012";
        List<String> regions = List.of("us-east-1");
        
        IngestionResult result = ingestionService.scanAndIngest(accountId, regions);
        
        assertThat(result.totalResources()).isPositive();
        assertThat(repository.count()).isEqualTo(result.totalResources());
    }
}
```

### 8. Security Considerations

#### Authentication

- Use AWS IAM roles for service-to-service authentication
- Implement JWT validation for external API access
- Encrypt sensitive resource data at rest

#### Authorization

- Implement resource-level access control
- Validate account ownership for all queries
- Log all resource access for audit purposes

### 9. Performance Considerations

#### Optimizations

1. **Database:**
   - Use connection pooling for database connections
   - Implement proper indexing on frequently queried columns
   - Use batch operations for bulk inserts
   - Configure appropriate cache TTL values

2. **AWS Scanner:**
   - Implement retry logic with exponential backoff
   - Use concurrent scanning for multiple regions
   - Implement rate limiting to avoid API throttling

3. **Monitoring:**
   - Track scan progress and performance metrics
   - Monitor database query performance
   - Alert on scan failures or performance degradation

### 10. Edge Cases

#### Handling Edge Cases

1. **Resource Concurrency:**
   - Use optimistic locking with versioning
   - Handle concurrent scan attempts gracefully
   - Implement deduplication logic

2. **API Throttling:**
   - Implement circuit breaker pattern
   - Implement exponential backoff for retries
   - Cache resources to reduce API calls

3. **Resource Updates:**
   - Handle resource lifecycle events (create, update, delete)
   - Implement resource synchronization
   - Handle partial scan failures

4. **Database Issues:**
   - Implement retry logic for database operations
   - Handle connection pooling timeouts
   - Implement database migration strategy

### Implementation Status

This design document is complete and ready for implementation. It defines:

- Clear domain models and entities
- Comprehensive data access layer
- Orchestration services with error handling
- Infrastructure components for AWS integration
- Database schema and configuration
- Exception handling strategy
- Testing approach and implementation
- Security and performance considerations
- Edge case handling strategies

Next step: Create implementation plan with task breakdown and execution approach.
