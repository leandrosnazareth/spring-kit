package com.leandrosnazareth.spring_kit.service;

import com.leandrosnazareth.spring_kit.model.Dependency;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DependencyService {

    public List<Dependency> getAllDependencies() {
        List<Dependency> dependencies = new ArrayList<>();
        
        // Web
        dependencies.add(new Dependency("web", "Spring Web", 
            "Build web, including RESTful, applications using Spring MVC", 
            "org.springframework.boot", "spring-boot-starter-web"));
        
        dependencies.add(new Dependency("webflux", "Spring Reactive Web", 
            "Build reactive web applications with Spring WebFlux and Netty", 
            "org.springframework.boot", "spring-boot-starter-webflux"));
        
        dependencies.add(new Dependency("websocket", "WebSocket", 
            "Build WebSocket applications with SockJS and STOMP", 
            "org.springframework.boot", "spring-boot-starter-websocket"));
        
        dependencies.add(new Dependency("rest-docs", "Spring REST Docs", 
            "Document RESTful services by combining hand-written and auto-generated documentation", 
            "org.springframework.restdocs", "spring-restdocs-mockmvc"));
        
        dependencies.add(new Dependency("hateoas", "Spring HATEOAS", 
            "Eases the creation of RESTful APIs that follow the HATEOAS principle", 
            "org.springframework.boot", "spring-boot-starter-hateoas"));
        
        // Template Engines
        dependencies.add(new Dependency("thymeleaf", "Thymeleaf", 
            "Server-side Java template engine for web applications", 
            "org.springframework.boot", "spring-boot-starter-thymeleaf"));
        
        dependencies.add(new Dependency("freemarker", "Apache Freemarker", 
            "Java-based template engine", 
            "org.springframework.boot", "spring-boot-starter-freemarker"));
        
        dependencies.add(new Dependency("mustache", "Mustache", 
            "Logic-less templates for web and standalone environments", 
            "org.springframework.boot", "spring-boot-starter-mustache"));
        
        // Data - SQL
        dependencies.add(new Dependency("jpa", "Spring Data JPA", 
            "Persist data in SQL stores with Java Persistence API using Spring Data and Hibernate", 
            "org.springframework.boot", "spring-boot-starter-data-jpa"));
        
        dependencies.add(new Dependency("jdbc", "JDBC API", 
            "Database connectivity using JDBC with the HikariCP connection pool", 
            "org.springframework.boot", "spring-boot-starter-jdbc"));
        
        dependencies.add(new Dependency("flyway", "Flyway Migration", 
            "Version control for your database", 
            "org.flywaydb", "flyway-core"));
        
        dependencies.add(new Dependency("liquibase", "Liquibase Migration", 
            "Database schema change management", 
            "org.liquibase", "liquibase-core"));
        
        dependencies.add(new Dependency("mybatis", "MyBatis Framework", 
            "Persistence framework with SQL mapping", 
            "org.mybatis.spring.boot", "mybatis-spring-boot-starter"));
        
        // Data - NoSQL
        dependencies.add(new Dependency("mongodb", "Spring Data MongoDB", 
            "Store data in flexible, JSON-like documents", 
            "org.springframework.boot", "spring-boot-starter-data-mongodb"));
        
        dependencies.add(new Dependency("mongodb-reactive", "Spring Data MongoDB Reactive", 
            "Reactive MongoDB support", 
            "org.springframework.boot", "spring-boot-starter-data-mongodb-reactive"));
        
        dependencies.add(new Dependency("redis", "Spring Data Redis", 
            "Advanced key-value store with support for data structures", 
            "org.springframework.boot", "spring-boot-starter-data-redis"));
        
        dependencies.add(new Dependency("redis-reactive", "Spring Data Redis Reactive", 
            "Access Redis key-value data stores in a reactive fashion", 
            "org.springframework.boot", "spring-boot-starter-data-redis-reactive"));
        
        dependencies.add(new Dependency("elasticsearch", "Spring Data Elasticsearch", 
            "Distributed, RESTful search and analytics engine", 
            "org.springframework.boot", "spring-boot-starter-data-elasticsearch"));
        
        dependencies.add(new Dependency("cassandra", "Spring Data Cassandra", 
            "Distributed NoSQL database designed to handle large amounts of data", 
            "org.springframework.boot", "spring-boot-starter-data-cassandra"));
        
        dependencies.add(new Dependency("couchbase", "Spring Data Couchbase", 
            "NoSQL document-oriented database optimized for interactive applications", 
            "org.springframework.boot", "spring-boot-starter-data-couchbase"));
        
        // Database Drivers
        dependencies.add(new Dependency("h2", "H2 Database", 
            "In-memory database (for development and testing)", 
            "com.h2database", "h2"));
        
        dependencies.add(new Dependency("mysql", "MySQL Driver", 
            "MySQL JDBC driver", 
            "com.mysql", "mysql-connector-java"));
        
        dependencies.add(new Dependency("postgresql", "PostgreSQL Driver", 
            "A JDBC and R2DBC driver for PostgreSQL", 
            "org.postgresql", "postgresql"));
        
        dependencies.add(new Dependency("mariadb", "MariaDB Driver", 
            "MariaDB JDBC driver", 
            "org.mariadb.jdbc", "mariadb-java-client"));
        
        dependencies.add(new Dependency("oracle", "Oracle Driver", 
            "Oracle JDBC driver", 
            "com.oracle.database.jdbc", "ojdbc8"));
        
        dependencies.add(new Dependency("sqlserver", "MS SQL Server Driver", 
            "Microsoft SQL Server JDBC driver", 
            "com.microsoft.sqlserver", "mssql-jdbc"));
        
        // Messaging
        dependencies.add(new Dependency("amqp", "Spring for RabbitMQ", 
            "Messaging with RabbitMQ via AMQP", 
            "org.springframework.boot", "spring-boot-starter-amqp"));
        
        dependencies.add(new Dependency("kafka", "Spring for Apache Kafka", 
            "Publish, subscribe, store, and process streams of records", 
            "org.springframework.kafka", "spring-kafka"));
        
        dependencies.add(new Dependency("kafka-streams", "Apache Kafka Streams", 
            "Building stream processing applications with Apache Kafka", 
            "org.apache.kafka", "kafka-streams"));
        
        dependencies.add(new Dependency("artemis", "Spring for Apache ActiveMQ Artemis", 
            "Messaging with Apache ActiveMQ Artemis", 
            "org.springframework.boot", "spring-boot-starter-artemis"));
        
        // Security
        dependencies.add(new Dependency("security", "Spring Security", 
            "Highly customizable authentication and access-control framework", 
            "org.springframework.boot", "spring-boot-starter-security"));
        
        dependencies.add(new Dependency("oauth2-client", "OAuth2 Client", 
            "Spring Boot integration for OAuth 2.0 and OpenID Connect client", 
            "org.springframework.boot", "spring-boot-starter-oauth2-client"));
        
        dependencies.add(new Dependency("oauth2-resource-server", "OAuth2 Resource Server", 
            "Spring Boot integration for OAuth 2.0 Resource Server", 
            "org.springframework.boot", "spring-boot-starter-oauth2-resource-server"));
        
        // Cloud
        dependencies.add(new Dependency("cloud-config-client", "Config Client", 
            "Client to connect to a Spring Cloud Config Server", 
            "org.springframework.cloud", "spring-cloud-starter-config"));
        
        dependencies.add(new Dependency("cloud-eureka", "Eureka Discovery Client", 
            "Service registration and discovery with Spring Cloud Netflix Eureka", 
            "org.springframework.cloud", "spring-cloud-starter-netflix-eureka-client"));
        
        dependencies.add(new Dependency("cloud-feign", "OpenFeign", 
            "Declarative REST client", 
            "org.springframework.cloud", "spring-cloud-starter-openfeign"));
        
        dependencies.add(new Dependency("cloud-gateway", "Gateway", 
            "Intelligent and programmable routing", 
            "org.springframework.cloud", "spring-cloud-starter-gateway"));
        
        dependencies.add(new Dependency("cloud-resilience4j", "Resilience4J", 
            "Circuit breaker with Resilience4j", 
            "org.springframework.cloud", "spring-cloud-starter-circuitbreaker-resilience4j"));
        
        // Observability
        dependencies.add(new Dependency("actuator", "Spring Boot Actuator", 
            "Production-ready features to monitor and manage your application", 
            "org.springframework.boot", "spring-boot-starter-actuator"));
        
        dependencies.add(new Dependency("prometheus", "Prometheus", 
            "Expose Prometheus metrics", 
            "io.micrometer", "micrometer-registry-prometheus"));
        
        // Ops
        dependencies.add(new Dependency("devtools", "Spring Boot DevTools", 
            "Fast application restarts, LiveReload, and configurations for enhanced development", 
            "org.springframework.boot", "spring-boot-devtools"));
        
        dependencies.add(new Dependency("config-processor", "Configuration Processor", 
            "Generate metadata for your custom configuration keys", 
            "org.springframework.boot", "spring-boot-configuration-processor"));
        
        // I/O
        dependencies.add(new Dependency("validation", "Validation", 
            "Bean Validation with Hibernate validator", 
            "org.springframework.boot", "spring-boot-starter-validation"));
        
        dependencies.add(new Dependency("mail", "Java Mail Sender", 
            "Send email using Java Mail and Spring Framework", 
            "org.springframework.boot", "spring-boot-starter-mail"));
        
        dependencies.add(new Dependency("quartz", "Quartz Scheduler", 
            "Schedule jobs using Quartz", 
            "org.springframework.boot", "spring-boot-starter-quartz"));
        
        dependencies.add(new Dependency("batch", "Spring Batch", 
            "Build batch applications", 
            "org.springframework.boot", "spring-boot-starter-batch"));
        
        dependencies.add(new Dependency("integration", "Spring Integration", 
            "Enterprise Integration Patterns using Spring Integration", 
            "org.springframework.boot", "spring-boot-starter-integration"));
        
        // Tools
        dependencies.add(new Dependency("lombok", "Lombok", 
            "Java annotation library which helps to reduce boilerplate code", 
            "org.projectlombok", "lombok"));
        
        dependencies.add(new Dependency("mapstruct", "MapStruct", 
            "Code generator for bean mappings", 
            "org.mapstruct", "mapstruct"));
        
        // Testing
        dependencies.add(new Dependency("test", "Spring Boot Test", 
            "Starter for testing Spring Boot applications with JUnit Jupiter, Hamcrest and Mockito", 
            "org.springframework.boot", "spring-boot-starter-test"));
        
        dependencies.add(new Dependency("testcontainers", "Testcontainers", 
            "Provide lightweight, throwaway instances of common databases", 
            "org.testcontainers", "testcontainers"));
        
        dependencies.add(new Dependency("rest-assured", "REST Assured", 
            "Testing and validating REST services", 
            "io.rest-assured", "rest-assured"));
        
        return dependencies;
    }
}
