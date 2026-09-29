# Spring Boot Database Configuration

This document contains the database connection details needed to connect a Java Spring Boot application to the Beshton Job Matching Platform PostgreSQL database.

> **Security note:** Never commit actual passwords, API keys, or AWS credentials to version control. Use environment variables or a secrets manager (AWS Secrets Manager, HashiCorp Vault) in all environments. The values marked `<secret>` must be sourced from the project `.env` file or a secrets manager.

---

## Connection Details

| Property | Value |
|---|---|
| **Host (local)** | `localhost` |
| **Host (EC2)** | `ec2-3-21-164-53.us-east-2.compute.amazonaws.com` |
| **Port** | `6543` |
| **Database name** | `talent_matching` |
| **Username** | `henry` |
| **Password** | `<secret — see .env>` |
| **JDBC URL (local)** | `jdbc:postgresql://localhost:6543/talent_matching` |
| **JDBC URL (EC2)** | `jdbc:postgresql://ec2-3-21-164-53.us-east-2.compute.amazonaws.com:6543/talent_matching` |

---

## `application.properties`

```properties
# ── DataSource ──────────────────────────────────────────────
spring.datasource.url=jdbc:postgresql://localhost:6543/talent_matching
spring.datasource.username=henry
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver

# ── Connection Pool (HikariCP) ───────────────────────────────
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000

# ── JPA / Hibernate ─────────────────────────────────────────
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=true
```

> Set `ddl-auto=validate` so Hibernate confirms the schema matches your entities without modifying the existing tables. Use `none` in production.

---

## `application.yml` (alternative)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:6543/talent_matching
    username: henry
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        format_sql: true
```

---

## Environment-Specific Profiles

Create separate profile files to switch between local and EC2:

**`application-local.properties`**
```properties
spring.datasource.url=jdbc:postgresql://localhost:6543/talent_matching
```

**`application-ec2.properties`**
```properties
spring.datasource.url=jdbc:postgresql://ec2-3-21-164-53.us-east-2.compute.amazonaws.com:6543/talent_matching
```

Activate a profile at runtime:
```bash
java -jar app.jar --spring.profiles.active=ec2
```

---

## Maven Dependencies (`pom.xml`)

```xml
<!-- PostgreSQL JDBC Driver -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.3</version>
</dependency>

<!-- Spring Data JPA -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- pgvector support (for embedding similarity queries) -->
<dependency>
    <groupId>com.pgvector</groupId>
    <artifactId>pgvector</artifactId>
    <version>0.1.4</version>
</dependency>
```

---

## Gradle Dependencies (`build.gradle`)

```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    runtimeOnly 'org.postgresql:postgresql:42.7.3'
    implementation 'com.pgvector:pgvector:0.1.4'
}
```

---

## pgvector Setup

The database uses the `pgvector` PostgreSQL extension for AI-powered job matching. All embedding columns are `vector(1536)` (OpenAI `text-embedding-ada-002` dimensions).

### Registering the pgvector type with Hibernate

```java
import com.pgvector.PGvector;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.sql.Connection;

@Component
public class PgVectorInitializer implements CommandLineRunner {

    private final DataSource dataSource;

    public PgVectorInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            PGvector.addVectorType(conn);
        }
    }
}
```

### Native cosine-similarity query example

```java
@Repository
public interface JobListingRepository extends JpaRepository<JobListing, UUID> {

    @Query(value = """
        SELECT id, title, company_name, job_url, location,
               1 - (embedding <=> CAST(:embedding AS vector)) AS similarity_score
        FROM job_listings
        WHERE embedding IS NOT NULL
        ORDER BY embedding <=> CAST(:embedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findTopMatchesByEmbedding(
        @Param("embedding") String embedding,
        @Param("limit") int limit
    );
}
```

Pass the embedding as a Postgres vector string: `"[0.123, 0.456, ...]"` (1536 floats, comma-separated, wrapped in brackets).

---

## Database Schema Summary

The following tables exist in the `talent_matching` database. All primary keys are `UUID`.

| Table | Description |
|---|---|
| `job_listings` | Scraped job postings. Has a `vector(1536)` `embedding` column. |
| `candidate_info` | Candidate profiles with skills, degree, and resume (Base64). Has a `vector(1536)` `embedding` column. |
| `user_accounts` | Login accounts. FK `candidate_profile_id → candidate_info.id` (nullable, unique). |
| `roles` | Role definitions (Admin, Staff, User). |
| `user_role_mapping` | Many-to-many join between `user_accounts` and `roles`. |

Full field-level schema for each table is documented in [`API_DOCUMENTATION.md`](./API_DOCUMENTATION.md#data-models).

---

## Other External Services

These are also needed to fully replicate the platform in Spring Boot.

### OpenAI (embeddings)

```properties
openai.api-key=${OPENAI_API_KEY}
openai.embedding-model=text-embedding-ada-002
openai.embedding-dimensions=1536
```

### AWS SES (email)

```properties
aws.region=us-west-2
aws.ses.sender-email=${SES_SENDER_EMAIL}
# Credentials via environment: AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY
# Or use an EC2 instance role (preferred in production)
```

**Maven dependency:**
```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>ses</artifactId>
    <version>2.25.0</version>
</dependency>
```
