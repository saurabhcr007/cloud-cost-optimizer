# Cloud Cost Optimizer

A production-grade AWS Cloud Cost Optimization platform built with Java 21, Spring Boot 3.x, and PostgreSQL.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Cloud Cost Optimizer                         │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌──────────────┐  ┌────────────────────────┐  │
│  │ AWS Scanner │──▶│ Normalizer  │──▶│ Ingestion Service    │  │
│  └─────────────┘  └──────────────┘  └───────────┬────────────┘  │
│                                                  │               │
│  ┌─────────────┐  ┌──────────────┐             ▼               │
│  │ Scheduler   │◀─│ Repository  │◀──── Database               │  │
│  └─────────────┘  └──────────────┘                            │
│                                                  │               │
│  ┌─────────────┐  ┌──────────────┐             ▼               │
│  │ Dashboard   │◀─│ API Layer   │◀──── Analysis Engine        │  │
│  └─────────────┘  └──────────────┘                            │
└─────────────────────────────────────────────────────────────────┘
```

## Features

- **Resource Discovery**: Automatically scans EC2, RDS, EBS, Elastic IPs, S3 buckets
- **Cost Analysis**: Analyzes utilization metrics and identifies waste
- **Recommendations**: Generates right-sizing, reservation, and deletion recommendations
- **Historical Tracking**: Stores scan history for trend analysis
- **REST API**: Full CRUD and query capabilities
- **Monitoring**: Prometheus metrics, Grafana dashboards

## Tech Stack

- **Language**: Java 21
- **Framework**: Spring Boot 3.2
- **Database**: PostgreSQL 15
- **Cache**: Redis 7
- **AWS SDK**: v2 (EC2, RDS, EBS, S3)
- **Testing**: JUnit 5, Mockito, Testcontainers
- **Build**: Maven
- **Container**: Docker, Docker Compose

## Project Structure

```
cloud-cost-optimizer/
├── src/
│   ├── main/
│   │   ├── java/com/cloudcost/
│   │   │   ├── aws/
│   │   │   │   ├── normalizer/    # Data transformation
│   │   │   │   └── scanner/       # AWS API integration
│   │   │   ├── config/            # Configuration classes
│   │   │   ├── exception/         # Custom exceptions
│   │   │   ├── model/             # JPA entities
│   │   │   ├── repository/        # Data access layer
│   │   │   ├── service/           # Business logic
│   │   │   └── CloudCostOptimizerApplication.java
│   │   └── resources/
│   │       ├── application-dev.properties
│   │       └── application-test.properties
│   └── test/
│       └── java/com/cloudcost/
│           ├── integration/       # Integration tests
│           ├── aws/               # AWS component tests
│           ├── repository/        # Repository tests
│           ├── service/           # Service tests
│           └── model/             # Model tests
├── Dockerfile
├── docker-compose.yml
├── prometheus.yml
└── pom.xml
```

## Quick Start

### Prerequisites
- Java 21+
- Maven 3.9+
- Docker & Docker Compose
- AWS Credentials (for scanning)

### Local Development

1. **Start dependencies**:
```bash
docker-compose up -d postgres redis
```

2. **Configure AWS credentials** (optional for tests):
```bash
export AWS_ACCESS_KEY_ID=your-key
export AWS_SECRET_ACCESS_KEY=your-secret
```

3. **Run the application**:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

4. **Access endpoints**:
- API: http://localhost:8080/api
- Health: http://localhost:8080/actuator/health
- Metrics: http://localhost:8080/actuator/prometheus
- Swagger: http://localhost:8080/swagger-ui.html

### Run Tests

```bash
# Unit tests
mvn test

# Integration tests (requires Docker)
mvn verify -Dspring.profiles.active=test
```

### Full Stack with Docker Compose

```bash
docker-compose up --build -d
```

Services:
- App: http://localhost:8080
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)

## API Endpoints

### Resources
- `GET /api/resources` - List all resources (paginated, filterable)
- `GET /api/resources/{id}` - Get resource by ID
- `GET /api/resources/account/{accountId}` - Get resources by account
- `GET /api/resources/type/{type}` - Get resources by type

### Scanning
- `POST /api/scan` - Trigger resource scan
- `GET /api/scan/status` - Get scan status

### Recommendations
- `GET /api/recommendations` - Get cost optimization recommendations

### Health & Metrics
- `GET /actuator/health` - Health check
- `GET /actuator/metrics` - Application metrics
- `GET /actuator/prometheus` - Prometheus metrics

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DATABASE_URL` | PostgreSQL JDBC URL | jdbc:postgresql://localhost:5432/cloud_cost |
| `DATABASE_USERNAME` | DB username | postgres |
| `DATABASE_PASSWORD` | DB password | postgres |
| `REDIS_HOST` | Redis host | localhost |
| `REDIS_PORT` | Redis port | 6379 |
| `AWS_DEFAULT_REGION` | AWS region | us-east-1 |
| `AWS_ACCESS_KEY_ID` | AWS access key | - |
| `AWS_SECRET_ACCESS_KEY` | AWS secret key | - |
| `JWT_SECRET` | JWT signing key | (generated) |

### Application Properties

Key configurations in `application-dev.properties`:

```properties
# Scanner
aws.scanner.batch.size=100
aws.scanner.max.attempts=3
aws.scanner.retry.delay=1000

# Database
spring.datasource.hikari.maximum-pool-size=20

# Cache
spring.cache.redis.time-to-live=3600000

# Scheduling
spring.scheduling.enabled=true
```

## Testing

### Test Structure

```
src/test/java/com/cloudcost/
├── model/           # Entity tests
├── repository/      # Repository layer tests
├── aws/
│   ├── normalizer/  # Normalizer tests
│   └── scanner/     # Scanner tests (mocked)
├── service/         # Service layer tests
└── integration/     # Full stack integration tests
```

### Running Specific Tests

```bash
# Model tests
mvn test -Dtest=ResourceTest

# Repository tests
mvn test -Dtest=ResourceRepositoryTest

# Normalizer tests
mvn test -Dtest=ResourceNormalizerTest

# Scanner tests
mvn test -Dtest=AwsResourceScannerTest

# Service tests
mvn test -Dtest=ResourceIngestionServiceTest

# Integration tests
mvn test -Dtest=ResourceDiscoveryIntegrationTest
```

## CI/CD Pipeline

GitHub Actions workflow (`.github/workflows/ci.yml`):
1. Code checkout
2. Build with Maven
3. Run unit tests
4. Run integration tests (Testcontainers)
5. Build Docker image
6. Push to registry
7. Deploy to staging

## Monitoring

### Key Metrics
- `cloudcost.resources.scanned` - Total resources discovered
- `cloudcost.scan.duration` - Scan execution time
- `cloudcost.recommendations.generated` - Recommendations created
- `cloudcost.errors` - Error count by type

### Grafana Dashboards
Pre-configured dashboards for:
- Resource inventory overview
- Cost trends
- Recommendation effectiveness
- System health

## Security

- JWT-based authentication
- Role-based access control (Admin, Viewer)
- AWS IAM role integration
- Encrypted credentials storage
- Audit logging for all operations

## Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

## License

MIT License - see LICENSE file for details.

## Support

For issues and feature requests, please use the GitHub issue tracker.