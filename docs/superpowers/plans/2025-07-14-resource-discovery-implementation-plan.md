# Resource Discovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the AWS resource discovery subsystem for EC2, RDS, EBS, Elastic IP, S3, and Snapshot resources

**Architecture:** Clean architecture with Spring Boot 3.x, implementing the data flow: Scanner → Normalizer → Ingestion Service → Repository → Database

**Tech Stack:** Java 21, Spring Boot 3.x, PostgreSQL, AWS SDK v2, JUnit 5, Mockito, Docker

## Global Constraints

- Spring Boot 3.x framework
- Java 21 language version
- PostgreSQL as primary database
- AWS SDK v2 compliance
- Connection pooling for database
- 90%+ test coverage requirement
- Optimistic locking for concurrent access

## Task Breakdown

### Task 1: Create Domain Models

**Description:** Implement Resource and ResourceType models with JPA entities

**Files:**
- Create: `src/main/java/com/cloudcost/model/Resource.java`
- Create: `src/main/java/com/cloudcost/model/ResourceType.java`

**Interfaces:**
- Consumes: None
- Produces: Resource model types for repository layer

**Implementation Requirements:**
```java
@Entity
@Table(name = "aws_resources", indexes = {
    @Index(name = "idx_account_region_type", columnList = "account_id,region,resource_type"),
    @Index(name = "idx_resource_id_type", columnList = "resource_id,resource_type")
})
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
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "resource_tags", 
        joinColumns = @JoinColumn(name = "resource_id"))
    @MapKeyColumn(name = "tag_key")
    @Column(name = "tag_value")
    private Map<String, String> tags;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "resource_metrics", 
        joinColumns = @JoinColumn(name = "resource_id"))
    @MapKeyColumn(name = "metric_key")
    @Column(name = "metric_value")
    private Map<String, Double> metrics;
    
    @Column(nullable = false)
    private Instant createdAt;
    
    @Column(nullable = false)
    private Instant updatedAt;
    
    @Version
    private Long version;
}

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

**Task Structure:**

- [ ] **Step 1: Write the failing test**

```java
@ExtendWith(MockitoExtension.class)
class ResourceTest {
    @Test
    void testResourceConstructorAndGetters() {
        Map<String, String> tags = Map.of("Environment", "Production");
        Resource resource = new Resource(
            "i-123456", ResourceType.EC2_INSTANCE, 
            "us-east-1", "123456789012", "m5.large", 
            "EC2 instance", tags, Map.of("cpu", 80.0),
            Instant.now(), Instant.now(), 1L
        );
        
        assertThat(resource.getResourceId()).isEqualTo("i-123456");
        assertThat(resource.getResourceType()).isEqualTo(ResourceType.EC2_INSTANCE);
        assertThat(resource.getName()).isEqualTo("m5.large");
        assertThat(resource.getTags()).isEqualTo(tags);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ResourceTest -DfailIfNoTests=false`

- [ ] **Step 3: Write minimal implementation**

Create complete Resource entity and ResourceType enum as specified above

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ResourceTest -DfailIfNoTests=false`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cloudcost/model/Resource.java
   git add src/main/java/com/cloudcost/model/ResourceType.java
   git add src/test/java/com/cloudcost/model/ResourceTest.java
   git commit -m "feat: add domain models Resource and ResourceType"
```

### Task 2: Implement Data Access Layer

**Description:** Create ResourceRepository interfaces and implementations

**Files:**
- Create: `src/main/java/com/cloudcost/repository/ResourceRepository.java`
- Create: `src/main/java/com/cloudcost/repository/ResourceRepositoryImpl.java`

**Interfaces:**
- Consumes: Resource model from Task 1
- Produces: CRUD operations and filtering methods

**Implementation Requirements:**
```java
@Repository
@Transactional(readOnly = true)
@CacheConfig(cacheNames = "resources")
public class ResourceRepositoryImpl implements ResourceRepository {
    @PersistenceContext
    private EntityManager entityManager;
    
    @Override
    @Cacheable(key = "{#resourceId + '_' + #type}")
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
    
    @Override
    public boolean existsByResourceIdAndType(String resourceId, ResourceType type) {
        String jpql = "SELECT COUNT(r) > 0 FROM Resource r WHERE r.resourceId = :resourceId AND r.resourceType = :type";
        Long count = entityManager.createQuery(jpql, Long.class)
            .setParameter("resourceId", resourceId)
            .setParameter("type", type)
            .getSingleResult();
        return count;
    }
    
    public void flush() {
        entityManager.flush();
    }
}
```

**Task Structure:**

- [ ] **Step 1: Write the failing test**

```java
@ExtendWith(MockitoExtension.class)
class ResourceRepositoryTest {
    @Mock
    private EntityManager mockEntityManager;
    
    @Mock
    private Query mockQuery;
    
    @InjectMocks
    private ResourceRepositoryImpl repository;
    
    @Test
    void testFindByResourceIdAndType() {
        Resource testResource = createTestResource();
        when(mockEntityManager.createQuery(anyString(), eq(Resource.class)))
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

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ResourceRepositoryTest -DfailIfNoTests=false`

- [ ] **Step 3: Write minimal implementation**

Implement ResourceRepository and ResourceRepositoryImpl as specified above

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ResourceRepositoryTest -DfailIfNoTests=false`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cloudcost/repository/ResourceRepository.java
   git add src/main/java/com/cloudcost/repository/ResourceRepositoryImpl.java
   git add src/test/java/com/cloudcost/repository/ResourceRepositoryTest.java
   git commit -m "feat: add repository layer ResourceRepositoryImpl"
```

### Task 3: Implement Resource Normalization Service

**Description:** Create ResourceNormalizer service for data conversion

**Files:**
- Create: `src/main/java/com/cloudcost/aws/normalizer/ResourceNormalizer.java`

**Interfaces:**
- Consumes: Raw resource data from AWS clients
- Produces: ResourceData objects and Resource entities

**Implementation Requirements:**
```java
@Component
@RequiredArgsConstructor
public class ResourceNormalizer {
    @Value("${aws.default.region:us-east-1}")
    private String defaultRegion;
    
    public ResourceData normalizeToResourceData(String resourceId, String name, String description,
                                               Map<String, String> rawTags, Map<String, String> rawMetrics) {
        Map<String, String> tags = rawTags != null ? rawTags : Map.of();
        Map<String, Double> metrics = convertMetrics(rawMetrics);
        
        return ResourceData.builder()
            .resourceId(resourceId)
            .name(name)
            .description(description)
            .tags(tags)
            .metrics(metrics)
            .additionalInfo(Map.of(
                "normalizedAt", Instant.now().toString(),
                "region", defaultRegion
            ))
            .build();
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
        if (rawMetrics == null) {
            return Map.of();
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
```

**Task Structure:**

- [ ] **Step 1: Write the failing test**

```java
@ExtendWith(MockitoExtension.class)
class ResourceNormalizerTest {
    private ResourceNormalizer normalizer;
    
    @BeforeEach
    void setUp() {
        normalizer = new ResourceNormalizer();
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
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ResourceNormalizerTest -DfailIfNoTests=false`

- [ ] **Step 3: Write minimal implementation**

Implement ResourceNormalizer as specified above

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ResourceNormalizerTest -DfailIfNoTests=false`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cloudcost/aws/normalizer/ResourceNormalizer.java
   git add src/test/java/com/cloudcost/aws/normalizer/ResourceNormalizerTest.java
   git commit -m "feat: add resource normalization service"
```

### Task 4: Implement AWS Scanner

**Description:** Create AWSResourceScanner for API integration

**Files:**
- Create: `src/main/java/com/cloudcost/aws/scanner/AwsResourceScanner.java`

**Interfaces:**
- Consumes: AWS SDK clients
- Produces: Scan results from all resource types

**Implementation Requirements:**
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
}
```

**Task Structure:**

- [ ] **Step 1: Write the failing test**

```java
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class AwsResourceScannerTest {
    @Mock
    private AmazonEC2Client mockEC2Client;
    
    @InjectMocks
    private AwsResourceScanner scanner;
    
    @Test
    void testScanEc2Instances() {
        Instance mockInstance = new Instance()
            .withInstanceId("i-123")
            .withInstanceType(InstanceType.M5_LARGE)
            .withDescription("Test instance");
        
        Reservation mockReservation = new Reservation()
            .withInstances(mockInstance);
        
        DescribeInstancesResult mockResult = new DescribeInstancesResult()
            .withReservations(mockReservation);
        
        when(mockEC2Client.describeInstances(any(DescribeInstancesRequest.class)))
            .thenReturn(mockResult);
        
        Map<ResourceType, List<ResourceData>> results = scanner.scanRegion("123456789012", "us-east-1");
        
        assertThat(results).containsKey(ResourceType.EC2_INSTANCE);
        assertThat(results.get(ResourceType.EC2_INSTANCE)).hasSize(1);
        assertThat(results.get(ResourceType.EC2_INSTANCE).get(0).resourceId()).isEqualTo("i-123");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=AwsResourceScannerTest -DfailIfNoTests=false`

- [ ] **Step 3: Write minimal implementation**

Implement AwsResourceScanner as specified above

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=AwsResourceScannerTest -DfailIfNoTests=false`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cloudcost/aws/scanner/AwsResourceScanner.java
   git add src/test/java/com/cloudcost/aws/scanner/AwsResourceScannerTest.java
   git commit -m "feat: add AWS resource scanner"
```

### Task 5: Implement Resource Ingestion Service

**Description:** Create ResourceIngestionService for workflow coordination

**Files:**
- Create: `src/main/java/com/cloudcost/service/ResourceIngestionService.java`
- Create: `src/main/java/com/cloudcost/exception/ResourceException.java`
- Create: `src/main/java/com/cloudcost/exception/DuplicateResourceException.java`
- Create: `src/main/java/com/cloudcost/exception/ResourceScanException.java`

**Interfaces:**
- Consumes: AwsResourceScanner results
- Produces: Ingestion results and statistics

**Implementation Requirements:**
```java
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ResourceIngestionService {
    private final AwsResourceScanner scanner;
    private final ResourceNormalizer normalizer;
    private final ResourceRepository repository;
    
    private static final int BATCH_SIZE = 100;
    
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
            } catch (Exception e) {
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

public class ResourceScanException extends ResourceException {
    public ResourceScanException(String message, Throwable cause) {
        super(ErrorCode.SCAN_FAILED, message, null);
    }
}
```

**Task Structure:**

- [ ] **Step 1: Write the failing test**

```java
@SpringBootTest
@ActiveProfiles("test")
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
        
        Map<ResourceType, List<ResourceData>> scanResults = new HashMap<>();
        scanResults.put(ResourceType.EC2_INSTANCE, 
            List.of(ResourceData.builder()
                .resourceId("i-123")
                .name("m5.large")
                .build()));
        
        when(scanner.scanRegion(accountId, "us-east-1")).thenReturn(scanResults);
        when(normalizer.normalize(eq("us-east-1"), eq(accountId)), 
             anyList()).thenReturn(List.of(ResourceData.builder().resourceId("i-123").build()));
        
        Resource testResource = Resource.builder()
            .resourceId("i-123")
            .resourceType(ResourceType.EC2_INSTANCE)
            .region("us-east-1")
            .accountId(accountId)
            .name("m5.large")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .version(1L)
            .build();
        
        when(repository.save(any(Resource.class))).thenReturn(testResource);
        
        IngestionResult result = ingestionService.scanAndIngest(accountId, regions);
        
        assertThat(result.totalResources()).isEqualTo(1);
        assertThat(result.timestamp()).isNotNull();
        verify(scanner).scanRegion(accountId, "us-east-1");
        verify(repository).save(any(Resource.class));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ResourceIngestionServiceTest -DfailIfNoTests=false`

- [ ] **Step 3: Write minimal implementation**

Implement ResourceIngestionService as specified above

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ResourceIngestionServiceTest -DfailIfNoTests=false`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cloudcost/service/ResourceIngestionService.java
   git add src/main/java/com/cloudcost/exception/ResourceException.java
   git add src/main/java/com/cloudcost/exception/DuplicateResourceException.java
   git add src/main/java/com/cloudcost/exception/ResourceScanException.java
   git add src/test/java/com/cloudcost/service/ResourceIngestionServiceTest.java
   git commit -m "feat: add resource ingestion service"
```

### Task 6: Infrastructure Configuration

**Description:** Setup database and application configuration

**Files:**
- Create: `src/main/resources/application-dev.properties`
- Create: `src/main/resources/application-test.properties`

**Implementation Requirements:**
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

**Task Structure:**

- [ ] **Step 1: Write failing test**

Create test that verifies application context loads and properties are set

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ResourceApplicationTest`

- [ ] **Step 3: Write minimal implementation**

Create properties files and basic application configuration

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ResourceApplicationTest`

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/application-dev.properties
   git add src/main/resources/application-test.properties
   git add src/test/java/com/cloudcost/ResourceApplicationTest.java
   git commit -m "feat: add infrastructure configuration"
```

### Task 7: Integration Testing

**Description:** Comprehensive integration tests

**Files:**
- Create: `src/test/java/com/cloudcost/integration/*IntegrationTest.java`

**Task Structure:**

- [ ] **Step 1: Write failing test**

Create integration test that starts embedded database and tests full flow

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ResourceDiscoveryIntegrationTest`

- [ ] **Step 3: Write minimal implementation**

Create complete integration test setup

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ResourceDiscoveryIntegrationTest`

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/cloudcost/integration/ResourceDiscoveryIntegrationTest.java
   git commit -m "feat: add integration tests"
```

### Task 8: Build and Deployment

**Description:** Setup build configuration and deployment

**Files:**
- Create: `pom.xml`

**Task Structure:**

- [ ] **Step 1: Write failing test**

Create test to verify maven build works

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn clean test`

- [ ] **Step 3: Write minimal implementation**

Create Maven pom.xml with all dependencies

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn clean test`

- [ ] **Step 5: Commit**

```bash
git add pom.xml
   git commit -m "feat: add build configuration"
```

## Execution Handoff

This plan is complete and ready for implementation. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task with review checkpoints
**2. Inline Execution** - Execute tasks in this session using executing-plans

**Which approach?**

I recommend Subagent-Driven for this project due to:
- Independent tasks (good for parallel execution)
- Need for fresh context per task (prevents cross-contamination)
- Quality gates for each deliverable
- Faster iteration with review checkpoints

Choose "1" to start immediately, or "2" for inline execution. The plan will proceed based on your choice.
"