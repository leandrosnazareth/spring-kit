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
            "Cria aplicações web, incluindo RESTful, usando Spring MVC", 
            "org.springframework.boot", "spring-boot-starter-web"));
        
        dependencies.add(new Dependency("webflux", "Spring Reactive Web", 
            "Cria aplicações web reativas com Spring WebFlux e Netty", 
            "org.springframework.boot", "spring-boot-starter-webflux"));
        
        // dependencies.add(new Dependency("websocket", "WebSocket", 
        //     "Cria aplicações WebSocket com SockJS e STOMP", 
        //     "org.springframework.boot", "spring-boot-starter-websocket"));
        
        dependencies.add(new Dependency("rest-docs", "Spring REST Docs", 
            "Documenta serviços RESTful combinando documentação escrita manualmente e gerada automaticamente", 
            "org.springframework.restdocs", "spring-restdocs-mockmvc"));
        
        dependencies.add(new Dependency("hateoas", "Spring HATEOAS", 
            "Facilita a criação de APIs RESTful que seguem o princípio HATEOAS", 
            "org.springframework.boot", "spring-boot-starter-hateoas"));
        
        // Template Engines
        dependencies.add(new Dependency("thymeleaf", "Thymeleaf", 
            "Motor de templates Java do lado servidor para aplicações web", 
            "org.springframework.boot", "spring-boot-starter-thymeleaf"));
        
        // dependencies.add(new Dependency("freemarker", "Apache Freemarker", 
        //     "Motor de templates baseado em Java", 
        //     "org.springframework.boot", "spring-boot-starter-freemarker"));
        
        // dependencies.add(new Dependency("mustache", "Mustache", 
        //     "Logic-less templates for web and standalone environments", 
        //     "org.springframework.boot", "spring-boot-starter-mustache"));
        
        // Data - SQL
        dependencies.add(new Dependency("jpa", "Spring Data JPA", 
            "Persiste dados em bancos SQL usando JPA com Spring Data e Hibernate", 
            "org.springframework.boot", "spring-boot-starter-data-jpa"));
        
        // dependencies.add(new Dependency("jdbc", "JDBC API", 
        //     "Database connectivity using JDBC with the HikariCP connection pool", 
        //     "org.springframework.boot", "spring-boot-starter-jdbc"));
        
        dependencies.add(new Dependency("flyway", "Flyway Migration", 
            "Controle de versão para o seu banco de dados", 
            "org.flywaydb", "flyway-core"));
        
        dependencies.add(new Dependency("liquibase", "Liquibase Migration", 
            "Gerenciamento de alterações de esquema do banco de dados", 
            "org.liquibase", "liquibase-core"));
        
        // dependencies.add(new Dependency("mybatis", "MyBatis Framework", 
        //     "Framework de persistência com mapeamento SQL", 
        //     "org.mybatis.spring.boot", "mybatis-spring-boot-starter"));
        
        // Data - NoSQL
        // dependencies.add(new Dependency("mongodb", "Spring Data MongoDB", 
        //     "Armazena dados em documentos flexíveis semelhantes a JSON", 
        //     "org.springframework.boot", "spring-boot-starter-data-mongodb"));
        
        // dependencies.add(new Dependency("mongodb-reactive", "Spring Data MongoDB Reactive", 
        //     "Suporte reativo ao MongoDB", 
        //     "org.springframework.boot", "spring-boot-starter-data-mongodb-reactive"));
        
        // dependencies.add(new Dependency("redis", "Spring Data Redis", 
        //     "Armazenamento chave-valor avançado com suporte a estruturas de dados", 
        //     "org.springframework.boot", "spring-boot-starter-data-redis"));
        
        // dependencies.add(new Dependency("redis-reactive", "Spring Data Redis Reactive", 
        //     "Acessa armazenamentos Redis em modo reativo", 
        //     "org.springframework.boot", "spring-boot-starter-data-redis-reactive"));
        
        dependencies.add(new Dependency("elasticsearch", "Spring Data Elasticsearch", 
            "Motor de busca e análise distribuído e baseado em REST", 
            "org.springframework.boot", "spring-boot-starter-data-elasticsearch"));
        
        // dependencies.add(new Dependency("cassandra", "Spring Data Cassandra", 
        //     "Banco NoSQL distribuído projetado para lidar com grandes volumes de dados", 
        //     "org.springframework.boot", "spring-boot-starter-data-cassandra"));
        
        // dependencies.add(new Dependency("couchbase", "Spring Data Couchbase", 
        //     "Banco NoSQL orientado a documentos otimizado para aplicações interativas", 
        //     "org.springframework.boot", "spring-boot-starter-data-couchbase"));
        
        // Database Drivers
        dependencies.add(new Dependency("h2", "H2 Database", 
            "Banco em memória (para desenvolvimento e testes)", 
            "com.h2database", "h2"));
        
        dependencies.add(new Dependency("mysql", "MySQL Driver", 
            "Driver JDBC para MySQL", 
            "com.mysql", "mysql-connector-java"));
        
        dependencies.add(new Dependency("postgresql", "PostgreSQL Driver", 
            "Driver JDBC e R2DBC para PostgreSQL", 
            "org.postgresql", "postgresql"));
        
        dependencies.add(new Dependency("mariadb", "MariaDB Driver", 
            "Driver JDBC para MariaDB", 
            "org.mariadb.jdbc", "mariadb-java-client"));
        
        dependencies.add(new Dependency("oracle", "Oracle Driver", 
            "Driver JDBC para Oracle", 
            "com.oracle.database.jdbc", "ojdbc8"));
        
        dependencies.add(new Dependency("sqlserver", "MS SQL Server Driver", 
            "Driver JDBC para Microsoft SQL Server", 
            "com.microsoft.sqlserver", "mssql-jdbc"));
        
        // Messaging
        dependencies.add(new Dependency("amqp", "Spring for RabbitMQ", 
            "Mensageria com RabbitMQ via AMQP", 
            "org.springframework.boot", "spring-boot-starter-amqp"));
        
        dependencies.add(new Dependency("kafka", "Spring for Apache Kafka", 
            "Publicar, assinar, armazenar e processar fluxos de registros", 
            "org.springframework.kafka", "spring-kafka"));
        
        dependencies.add(new Dependency("kafka-streams", "Apache Kafka Streams", 
            "Construir aplicações de processamento de stream com Apache Kafka", 
            "org.apache.kafka", "kafka-streams"));
        
        dependencies.add(new Dependency("artemis", "Spring for Apache ActiveMQ Artemis", 
            "Mensageria com Apache ActiveMQ Artemis", 
            "org.springframework.boot", "spring-boot-starter-artemis"));
        
        // Security
        dependencies.add(new Dependency("security", "Spring Security", 
            "Framework de autenticação e controle de acesso altamente personalizável", 
            "org.springframework.boot", "spring-boot-starter-security"));
        
        dependencies.add(new Dependency("oauth2-client", "OAuth2 Client", 
            "Integração do Spring Boot com cliente OAuth 2.0 e OpenID Connect", 
            "org.springframework.boot", "spring-boot-starter-oauth2-client"));
        
        dependencies.add(new Dependency("oauth2-resource-server", "OAuth2 Resource Server", 
            "Integração do Spring Boot para Resource Server OAuth 2.0", 
            "org.springframework.boot", "spring-boot-starter-oauth2-resource-server"));
        
        // Cloud
        dependencies.add(new Dependency("cloud-config-client", "Config Client", 
            "Cliente para conectar a um Spring Cloud Config Server", 
            "org.springframework.cloud", "spring-cloud-starter-config"));
        
        // dependencies.add(new Dependency("cloud-eureka", "Eureka Discovery Client", 
        //     "Registro e descoberta de serviços com Spring Cloud Netflix Eureka", 
        //     "org.springframework.cloud", "spring-cloud-starter-netflix-eureka-client"));
        
        // dependencies.add(new Dependency("cloud-feign", "OpenFeign", 
        //     "Cliente REST declarativo", 
        //     "org.springframework.cloud", "spring-cloud-starter-openfeign"));
        
        // dependencies.add(new Dependency("cloud-gateway", "Gateway", 
        //     "Roteamento inteligente e programável", 
        //     "org.springframework.cloud", "spring-cloud-starter-gateway"));
        
        // dependencies.add(new Dependency("cloud-resilience4j", "Resilience4J", 
        //     "Circuit breaker com Resilience4j", 
        //     "org.springframework.cloud", "spring-cloud-starter-circuitbreaker-resilience4j"));
        
        // Observability
        dependencies.add(new Dependency("actuator", "Spring Boot Actuator", 
            "Recursos prontos para produção para monitorar e gerenciar sua aplicação", 
            "org.springframework.boot", "spring-boot-starter-actuator"));
        
        dependencies.add(new Dependency("prometheus", "Prometheus", 
            "Expor métricas para Prometheus", 
            "io.micrometer", "micrometer-registry-prometheus"));
        
        // Ops
        dependencies.add(new Dependency("devtools", "Spring Boot DevTools", 
            "Reinícios rápidos da aplicação, LiveReload e configurações para desenvolvimento aprimorado", 
            "org.springframework.boot", "spring-boot-devtools"));
        
        dependencies.add(new Dependency("config-processor", "Configuration Processor", 
            "Gera metadados para suas chaves de configuração personalizadas", 
            "org.springframework.boot", "spring-boot-configuration-processor"));
        
        // I/O
        dependencies.add(new Dependency("validation", "Validation", 
            "Validação de beans com Hibernate Validator", 
            "org.springframework.boot", "spring-boot-starter-validation"));
        
        dependencies.add(new Dependency("mail", "Java Mail Sender", 
            "Enviar e-mails usando Java Mail e Spring Framework", 
            "org.springframework.boot", "spring-boot-starter-mail"));
        
        dependencies.add(new Dependency("quartz", "Quartz Scheduler", 
            "Agendar jobs usando Quartz", 
            "org.springframework.boot", "spring-boot-starter-quartz"));
        
        dependencies.add(new Dependency("batch", "Spring Batch", 
            "Construir aplicações batch", 
            "org.springframework.boot", "spring-boot-starter-batch"));
        
        dependencies.add(new Dependency("integration", "Spring Integration", 
            "Padrões de Integração Empresarial usando Spring Integration", 
            "org.springframework.boot", "spring-boot-starter-integration"));
        
        // Tools
        dependencies.add(new Dependency("lombok", "Lombok", 
            "Biblioteca de anotações Java que ajuda a reduzir código boilerplate", 
            "org.projectlombok", "lombok"));
        
        dependencies.add(new Dependency("mapstruct", "MapStruct", 
            "Gerador de código para mapeamentos entre beans", 
            "org.mapstruct", "mapstruct"));
        
        // Testing
        dependencies.add(new Dependency("test", "Spring Boot Test", 
            "Starter para testar aplicações Spring Boot com JUnit Jupiter, Hamcrest e Mockito", 
            "org.springframework.boot", "spring-boot-starter-test"));
        
        dependencies.add(new Dependency("testcontainers", "Testcontainers", 
            "Fornece instâncias descartáveis e leves de bancos de dados comuns", 
            "org.testcontainers", "testcontainers"));
        
        dependencies.add(new Dependency("rest-assured", "REST Assured", 
            "Testar e validar serviços REST", 
            "io.rest-assured", "rest-assured"));
        
        return dependencies;
    }
}
