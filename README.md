# SpringBoost

Clone do Spring Initializr com suporte para versões LTS do Java 17 e versões compatíveis do Spring Boot.

> **Status:** Beta — esta aplicação ainda está em evolução e pode sofrer mudanças frequentes.

## Características

- ✅ Suporte para Java LTS: 17
- ✅ Versões compatíveis do Spring Boot para cada versão do Java
- ✅ Interface moderna com Thymeleaf
- ✅ Geração de projetos Maven e Gradle
- ✅ Dependências mais utilizadas do Spring
- ✅ Download direto do projeto em formato ZIP

## Tecnologias

- Spring Boot 3.2.0
- Thymeleaf
- Lombok
- Maven

## Como executar

```bash
mvn spring-boot:run
```

Acesse: http://localhost:8080

## Como executar com Docker

Gerar a imagem localmente:

```bash
docker build -t spring-boost .
```

Subir o container com o docker compose:

```bash
docker compose up --build
```

O serviço ficará disponível em http://localhost:8080

## Versões compatíveis

### Java 17 (LTS)
- Spring Boot: 2.7.18, 3.0.13, 3.1.12, 3.2.5, 3.3.0

## Dependências disponíveis

- Spring Web
- Thymeleaf
- Spring Data JPA
- Spring Data MongoDB
- H2 Database
- MySQL Driver
- PostgreSQL Driver
- Spring Security
- Spring Boot DevTools
- Lombok
- Spring Boot Test
