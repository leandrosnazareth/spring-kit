package com.leandrosnazareth.spring_kit.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Service;

import com.leandrosnazareth.spring_kit.model.CrudGenerationRequest;
import com.leandrosnazareth.spring_kit.model.Dependency;
import com.leandrosnazareth.spring_kit.model.ProjectRequest;

import tools.jackson.databind.ObjectMapper;

@Service
public class ProjectGeneratorService {

    private final DependencyService dependencyService;
    private final CrudScaffoldingService crudScaffoldingService;
    private final ObjectMapper objectMapper;
    private static final Path DASHBOARD_TEMPLATE_RESOURCES =
        Paths.get("/home/nazareth/Documentos/Projetos/template-thymeleaf/src/main/resources");
    private static final Set<String> DASHBOARD_TEMPLATE_WHITELIST = Set.of(
        "layout/main.html",
        "fragments/header.html",
        "fragments/footer.html",
        "fragments/menu.html",
        "index.html"
    );

    public ProjectGeneratorService(DependencyService dependencyService,
                                   CrudScaffoldingService crudScaffoldingService,
                                   ObjectMapper objectMapper) {
        this.dependencyService = dependencyService;
        this.crudScaffoldingService = crudScaffoldingService;
        this.objectMapper = objectMapper;
    }

    public byte[] generateProject(ProjectRequest request) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);

        String baseDir = request.getArtifactId() + "/";
        String srcMainJava = baseDir + "src/main/java/" + request.getPackageName().replace(".", "/") + "/";
        String srcMainResources = baseDir + "src/main/resources/";
        String srcTestJava = baseDir + "src/test/java/" + request.getPackageName().replace(".", "/") + "/";
        CrudGenerationRequest crudRequest = parseCrudDefinition(request);

        // Generate pom.xml or build.gradle
        if ("maven".equals(request.getProjectType())) {
            addFileToZip(zos, baseDir + "pom.xml", generatePomXml(request));
        } else {
            addFileToZip(zos, baseDir + "build.gradle", generateBuildGradle(request));
        }

        // Generate main application class
        addFileToZip(zos, srcMainJava + capitalize(request.getName()) + "Application.java", 
            generateMainClass(request));

        // Generate application.properties
        addFileToZip(zos, srcMainResources + "application.properties", generateApplicationProperties(request));

        boolean includeTests = hasDependency(request, "test");
        if (includeTests) {
            addFileToZip(zos, srcTestJava + capitalize(request.getName()) + "ApplicationTests.java",
                generateIntegrationTestClass(request));
            addFileToZip(zos, srcTestJava + "SampleUnitTest.java", generateSampleUnitTest(request));
        }

        // Generate README
        addFileToZip(zos, baseDir + "README.md", generateReadme(request));

        // Generate Dockerfile and docker-compose
        addFileToZip(zos, baseDir + "Dockerfile", generateDockerfile(request));
        addFileToZip(zos, baseDir + "docker-compose.yml", generateDockerCompose(request));

        // Generate .gitignore
        addFileToZip(zos, baseDir + ".gitignore", generateGitignore(request));

        List<CrudScaffoldingService.CrudUiMetadata> crudUiMetadata = appendCrudModuleIfPresent(
            request, srcMainJava, srcTestJava, srcMainResources + "templates/", zos, crudRequest);

        if (hasDependency(request, "thymeleaf")) {
            copyDashboardTemplateResources(zos, baseDir, crudUiMetadata);
            addFileToZip(zos, srcMainJava + "controller/DashboardController.java",
                generateDashboardController(request.getPackageName()));
        }

        zos.close();
        return baos.toByteArray();
    }

    private void addFileToZip(ZipOutputStream zos, String fileName, String content) throws IOException {
        addFileToZip(zos, fileName, content.getBytes(StandardCharsets.UTF_8));
    }

    private void addFileToZip(ZipOutputStream zos, String fileName, byte[] content) throws IOException {
        ZipEntry entry = new ZipEntry(fileName);
        zos.putNextEntry(entry);
        zos.write(content);
        zos.closeEntry();
    }

    private List<CrudScaffoldingService.CrudUiMetadata> appendCrudModuleIfPresent(ProjectRequest request,
                                                                                 String srcMainJava,
                                                                                 String srcTestJava,
                                                                                 String templatesBasePath,
                                                                                 ZipOutputStream zos,
                                                                                 CrudGenerationRequest crudRequest)
        throws IOException {
        if (crudRequest == null || crudRequest.getClasses() == null || crudRequest.getClasses().isEmpty()) {
            return Collections.emptyList();
        }
        crudRequest.setBasePackage(request.getPackageName());
        crudRequest.setModuleName(request.getArtifactId() + "-crud");
        crudRequest.setThymeleafViews(hasDependency(request, "thymeleaf"));
        crudRequest.setUseLombok(hasDependency(request, "lombok"));
        crudRequest.setUseJakartaPersistence(isJakartaPersistence(request));
        crudRequest.setGenerateTests(hasDependency(request, "test"));
        return crudScaffoldingService.appendCrudToProject(crudRequest, srcMainJava, srcTestJava, templatesBasePath,
            request.getPackageName(), zos);
    }

    private CrudGenerationRequest parseCrudDefinition(ProjectRequest request) throws IOException {
        if (request.getCrudDefinition() == null || request.getCrudDefinition().isBlank()) {
            return null;
        }
        return objectMapper.readValue(request.getCrudDefinition(), CrudGenerationRequest.class);
    }

    private void copyDashboardTemplateResources(ZipOutputStream zos, String baseDir,
                                                List<CrudScaffoldingService.CrudUiMetadata> menuItems) throws IOException {
        Path templatesDir = DASHBOARD_TEMPLATE_RESOURCES.resolve("templates");
        Path staticDir = DASHBOARD_TEMPLATE_RESOURCES.resolve("static");
        copyTemplateDirectory(zos, templatesDir, baseDir + "src/main/resources/templates/", true, menuItems,
            DASHBOARD_TEMPLATE_WHITELIST);
        copyTemplateDirectory(zos, staticDir, baseDir + "src/main/resources/static/", false, menuItems, null);
    }

    private void copyTemplateDirectory(ZipOutputStream zos, Path sourceDir, String targetBase,
                                       boolean customizeMenu, List<CrudScaffoldingService.CrudUiMetadata> menuItems,
                                       Set<String> whitelist) throws IOException {
        if (!Files.exists(sourceDir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                Path relative = sourceDir.relativize(path);
                String normalized = relative.toString().replace("\\", "/");
                if (whitelist != null && !whitelist.contains(normalized)) {
                    return;
                }
                String entryName = targetBase + normalized;
                try {
                    byte[] content = Files.readAllBytes(path);
                    if (customizeMenu && "fragments/menu.html".equals(normalized)) {
                        content = generateMenuFragment(menuItems).getBytes(StandardCharsets.UTF_8);
                    }
                    addFileToZip(zos, entryName, content);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to copy template resource " + path, e);
                }
            });
        }
    }

    private String generateMenuFragment(List<CrudScaffoldingService.CrudUiMetadata> menuItems) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">

<head>
    <meta charset="UTF-8">
</head>

<body>
    <aside th:fragment="menu" class="sidebar" th:with="currentContent=${content}">
        <ul class="sidebar-nav" id="sidebar-nav">

            <li class="nav-item">
                <a class="nav-link" th:href="@{/}"
                    th:classappend="${currentContent} == 'index' ? ' active' : ' collapsed'">
                    <i class="bi bi-grid"></i>
                    <span>Dashboard</span>
                </a>
            </li>
""");
        if (menuItems != null && !menuItems.isEmpty()) {
            sb.append("            <li class=\"nav-heading\">Cadastros</li>\n");
            for (CrudScaffoldingService.CrudUiMetadata item : menuItems) {
                sb.append("            <li class=\"nav-item\">\n");
                sb.append("                <a class=\"nav-link\" th:href=\"@{'/").append(item.controllerPath()).append("}'\"\n");
                sb.append("                    th:classappend=\"${currentContent == '").append(item.listContentFragment())
                    .append("' or currentContent == '").append(item.formContentFragment())
                    .append("'} ? ' active' : ' collapsed'\">\n");
                sb.append("                    <i class=\"bi bi-collection\"></i>\n");
                sb.append("                    <span>").append(item.displayName()).append("</span>\n");
                sb.append("                </a>\n");
                sb.append("            </li>\n");
            }
        }
        sb.append("""
        </ul>
    </aside>
</body>

</html>
""");
        return sb.toString();
    }

    private String generateDashboardController(String basePackage) {
        String controllerPackage = basePackage + ".controller";
        return """
package %s;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("content", "index");
        return "layout/main";
    }
}
""".formatted(controllerPackage);
    }

    private boolean hasDependency(ProjectRequest request, String dependencyId) {
        if (request.getDependencies() == null) {
            return false;
        }
        return request.getDependencies().stream()
            .anyMatch(dep -> dependencyId.equalsIgnoreCase(dep));
    }

    private String generateApplicationProperties(ProjectRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Application Configuration\n");
        sb.append("server.port=8080\n");
        sb.append("spring.application.name=").append(request.getName()).append("\n\n");

        sb.append("# Datasource Configuration\n");
        DatabaseType dbType = resolveDatabaseType(request);
        String dbName = request.getArtifactId();
        switch (dbType) {
            case H2 -> {
                sb.append("spring.datasource.url=jdbc:h2:mem:").append(dbName)
                    .append(";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE\n");
                sb.append("spring.datasource.username=sa\n");
                sb.append("spring.datasource.password=\n");
                sb.append("spring.datasource.driver-class-name=org.h2.Driver\n");
                sb.append("spring.h2.console.enabled=true\n");
                sb.append("spring.jpa.database-platform=org.hibernate.dialect.H2Dialect\n");
            }
            case MYSQL -> {
                sb.append("spring.datasource.url=jdbc:mysql://localhost:3306/").append(dbName)
                    .append("?useSSL=false&serverTimezone=UTC&createDatabaseIfNotExist=true\n");
                sb.append("spring.datasource.username=root\n");
                sb.append("spring.datasource.password=secret\n");
                sb.append("spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver\n");
            }
            case POSTGRESQL -> {
                sb.append("spring.datasource.url=jdbc:postgresql://localhost:5432/").append(dbName).append("\n");
                sb.append("spring.datasource.username=postgres\n");
                sb.append("spring.datasource.password=secret\n");
                sb.append("spring.datasource.driver-class-name=org.postgresql.Driver\n");
            }
            case NONE -> sb.append("# Configure your datasource here if needed\n");
        }
        sb.append("\n");

        if (hasDependency(request, "jpa")) {
            sb.append("# JPA / Hibernate\n");
            sb.append("spring.jpa.hibernate.ddl-auto=update\n");
            sb.append("spring.jpa.show-sql=true\n");
            sb.append("spring.jpa.open-in-view=false\n\n");
        }

        if (hasDependency(request, "thymeleaf")) {
            sb.append("# Thymeleaf\n");
            sb.append("spring.thymeleaf.prefix=classpath:/templates/\n");
            sb.append("spring.thymeleaf.suffix=.html\n");
            sb.append("spring.thymeleaf.cache=false\n");
            sb.append("spring.thymeleaf.mode=HTML\n\n");
        }

        sb.append("# Logging\n");
        sb.append("logging.level.org.springframework=INFO\n");
        sb.append("logging.level.com.leandrosnazareth=DEBUG\n");
        return sb.toString();
    }

    private DatabaseType resolveDatabaseType(ProjectRequest request) {
        if (hasDependency(request, "h2")) {
            return DatabaseType.H2;
        }
        if (hasDependency(request, "mysql")) {
            return DatabaseType.MYSQL;
        }
        if (hasDependency(request, "postgresql")) {
            return DatabaseType.POSTGRESQL;
        }
        return DatabaseType.NONE;
    }

    private boolean isJakartaPersistence(ProjectRequest request) {
        String version = request.getSpringBootVersion();
        if (version == null) {
            return true;
        }
        return !version.startsWith("2.");
    }

    private enum DatabaseType {
        H2, MYSQL, POSTGRESQL, NONE
    }

    private String generateDockerfile(ProjectRequest request) {
        String javaVersion = request.getJavaVersion();
        String baseImage = switch (javaVersion) {
            case "8" -> "eclipse-temurin:8-jre";
            case "11" -> "eclipse-temurin:11-jre";
            case "17" -> "eclipse-temurin:17-jre";
            case "21" -> "eclipse-temurin:21-jre";
            default -> "eclipse-temurin:17-jre";
        };

        String jarName = request.getArtifactId() + "-0.0.1-SNAPSHOT.jar";

        StringBuilder sb = new StringBuilder();
        sb.append("FROM ").append(baseImage).append("\n");
        sb.append("WORKDIR /app\n");
        sb.append("COPY target/").append(jarName).append(" app.jar\n");
        sb.append("EXPOSE 8080\n");
        sb.append("ENTRYPOINT [\"java\",\"-jar\",\"/app/app.jar\"]\n");
        return sb.toString();
    }

    private String generateDockerCompose(ProjectRequest request) {
        DatabaseType dbType = resolveDatabaseType(request);
        String projectName = request.getArtifactId();
        String jarName = request.getArtifactId() + "-0.0.1-SNAPSHOT.jar";
        StringBuilder sb = new StringBuilder();
        sb.append("version: '3.8'\n");
        sb.append("services:\n");
        sb.append("  app:\n");
        sb.append("    build: .\n");
        sb.append("    container_name: ").append(projectName).append("-app\n");
        sb.append("    environment:\n");
        sb.append("      - SPRING_PROFILES_ACTIVE=default\n");
        if (dbType == DatabaseType.MYSQL) {
            sb.append("      - SPRING_DATASOURCE_URL=jdbc:mysql://db:3306/").append(projectName)
                .append("?useSSL=false&serverTimezone=UTC&createDatabaseIfNotExist=true\n");
            sb.append("      - SPRING_DATASOURCE_USERNAME=root\n");
            sb.append("      - SPRING_DATASOURCE_PASSWORD=secret\n");
        } else if (dbType == DatabaseType.POSTGRESQL) {
            sb.append("      - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/").append(projectName).append("\n");
            sb.append("      - SPRING_DATASOURCE_USERNAME=postgres\n");
            sb.append("      - SPRING_DATASOURCE_PASSWORD=secret\n");
        }
        sb.append("    ports:\n");
        sb.append("      - \"8080:8080\"\n");
        if (dbType != DatabaseType.H2 && dbType != DatabaseType.NONE) {
            sb.append("    depends_on:\n");
            sb.append("      - db\n");
        }
        sb.append("    volumes:\n");
        sb.append("      - ./target/").append(jarName).append(":/app/").append(jarName).append("\n");

        if (dbType == DatabaseType.MYSQL) {
            sb.append("\n  db:\n");
            sb.append("    image: mysql:8\n");
            sb.append("    container_name: ").append(projectName).append("-db\n");
            sb.append("    environment:\n");
            sb.append("      - MYSQL_ROOT_PASSWORD=secret\n");
            sb.append("      - MYSQL_DATABASE=").append(projectName).append("\n");
            sb.append("    ports:\n");
            sb.append("      - \"3306:3306\"\n");
            sb.append("    volumes:\n");
            sb.append("      - mysql_data:/var/lib/mysql\n");
        } else if (dbType == DatabaseType.POSTGRESQL) {
            sb.append("\n  db:\n");
            sb.append("    image: postgres:15\n");
            sb.append("    container_name: ").append(projectName).append("-db\n");
            sb.append("    environment:\n");
            sb.append("      - POSTGRES_PASSWORD=secret\n");
            sb.append("      - POSTGRES_DB=").append(projectName).append("\n");
            sb.append("    ports:\n");
            sb.append("      - \"5432:5432\"\n");
            sb.append("    volumes:\n");
            sb.append("      - postgres_data:/var/lib/postgresql/data\n");
        }

        if (dbType == DatabaseType.MYSQL) {
            sb.append("\nvolumes:\n");
            sb.append("  mysql_data:\n");
        } else if (dbType == DatabaseType.POSTGRESQL) {
            sb.append("\nvolumes:\n");
            sb.append("  postgres_data:\n");
        }

        return sb.toString();
    }

    private String generatePomXml(ProjectRequest request) {
        StringBuilder sb = new StringBuilder();
        List<Dependency> allDeps = dependencyService.getAllDependencies();
        
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n");
        sb.append("         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        sb.append("         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0\n");
        sb.append("         http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n");
        sb.append("    <modelVersion>4.0.0</modelVersion>\n\n");
        
        sb.append("    <parent>\n");
        sb.append("        <groupId>org.springframework.boot</groupId>\n");
        sb.append("        <artifactId>spring-boot-starter-parent</artifactId>\n");
        sb.append("        <version>").append(request.getSpringBootVersion()).append("</version>\n");
        sb.append("        <relativePath/>\n");
        sb.append("    </parent>\n\n");
        
        sb.append("    <groupId>").append(request.getGroupId()).append("</groupId>\n");
        sb.append("    <artifactId>").append(request.getArtifactId()).append("</artifactId>\n");
        sb.append("    <version>0.0.1-SNAPSHOT</version>\n");
        sb.append("    <packaging>").append(request.getPackaging()).append("</packaging>\n");
        sb.append("    <name>").append(request.getName()).append("</name>\n");
        sb.append("    <description>").append(request.getDescription()).append("</description>\n\n");
        
        sb.append("    <properties>\n");
        sb.append("        <java.version>").append(request.getJavaVersion()).append("</java.version>\n");
        sb.append("    </properties>\n\n");
        
        sb.append("    <dependencies>\n");
        for (String depId : request.getDependencies()) {
            Dependency dep = allDeps.stream()
                .filter(d -> d.getId().equals(depId))
                .findFirst()
                .orElse(null);
            
            if (dep != null) {
                sb.append("        <dependency>\n");
                sb.append("            <groupId>").append(dep.getGroupId()).append("</groupId>\n");
                sb.append("            <artifactId>").append(dep.getArtifactId()).append("</artifactId>\n");
                if ("devtools".equals(depId) || "lombok".equals(depId)) {
                    sb.append("            <optional>true</optional>\n");
                }
                if ("test".equals(depId)) {
                    sb.append("            <scope>test</scope>\n");
                }
                if ("h2".equals(depId) || "devtools".equals(depId)) {
                    sb.append("            <scope>runtime</scope>\n");
                }
                sb.append("        </dependency>\n");
            }
        }
        sb.append("    </dependencies>\n\n");
        
        sb.append("    <build>\n");
        sb.append("        <plugins>\n");
        sb.append("            <plugin>\n");
        sb.append("                <groupId>org.springframework.boot</groupId>\n");
        sb.append("                <artifactId>spring-boot-maven-plugin</artifactId>\n");
        sb.append("            </plugin>\n");
        sb.append("        </plugins>\n");
        sb.append("    </build>\n");
        sb.append("</project>\n");
        
        return sb.toString();
    }

    private String generateBuildGradle(ProjectRequest request) {
        StringBuilder sb = new StringBuilder();
        List<Dependency> allDeps = dependencyService.getAllDependencies();
        
        sb.append("plugins {\n");
        sb.append("    id 'java'\n");
        sb.append("    id 'org.springframework.boot' version '").append(request.getSpringBootVersion()).append("'\n");
        sb.append("    id 'io.spring.dependency-management' version '1.1.0'\n");
        sb.append("}\n\n");
        
        sb.append("group = '").append(request.getGroupId()).append("'\n");
        sb.append("version = '0.0.1-SNAPSHOT'\n");
        sb.append("sourceCompatibility = '").append(request.getJavaVersion()).append("'\n\n");
        
        sb.append("repositories {\n");
        sb.append("    mavenCentral()\n");
        sb.append("}\n\n");
        
        sb.append("dependencies {\n");
        for (String depId : request.getDependencies()) {
            Dependency dep = allDeps.stream()
                .filter(d -> d.getId().equals(depId))
                .findFirst()
                .orElse(null);
            
            if (dep != null) {
                String config = "implementation";
                if ("test".equals(depId)) {
                    config = "testImplementation";
                } else if ("h2".equals(depId) || "devtools".equals(depId)) {
                    config = "runtimeOnly";
                } else if ("lombok".equals(depId)) {
                    config = "compileOnly";
                }
                sb.append("    ").append(config).append(" '")
                    .append(dep.getGroupId()).append(":")
                    .append(dep.getArtifactId()).append("'\n");
            }
        }
        sb.append("}\n\n");
        
        sb.append("tasks.named('test') {\n");
        sb.append("    useJUnitPlatform()\n");
        sb.append("}\n");
        
        return sb.toString();
    }

    private String generateMainClass(ProjectRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(request.getPackageName()).append(";\n\n");
        sb.append("import org.springframework.boot.SpringApplication;\n");
        sb.append("import org.springframework.boot.autoconfigure.SpringBootApplication;\n\n");
        sb.append("@SpringBootApplication\n");
        sb.append("public class ").append(capitalize(request.getName())).append("Application {\n\n");
        sb.append("    public static void main(String[] args) {\n");
        sb.append("        SpringApplication.run(").append(capitalize(request.getName()))
            .append("Application.class, args);\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String generateIntegrationTestClass(ProjectRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(request.getPackageName()).append(";\n\n");
        sb.append("import org.junit.jupiter.api.Test;\n");
        sb.append("import org.springframework.boot.test.context.SpringBootTest;\n\n");
        sb.append("@SpringBootTest\n");
        sb.append("class ").append(capitalize(request.getName())).append("ApplicationTests {\n\n");
        sb.append("    @Test\n");
        sb.append("    void contextLoads() {\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String generateSampleUnitTest(ProjectRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(request.getPackageName()).append(";\n\n");
        sb.append("import org.junit.jupiter.api.Test;\n");
        sb.append("import static org.assertj.core.api.Assertions.assertThat;\n\n");
        sb.append("class SampleUnitTest {\n\n");
        sb.append("    @Test\n");
        sb.append("    void shouldAddNumbers() {\n");
        sb.append("        int result = 1 + 1;\n");
        sb.append("        assertThat(result).isEqualTo(2);\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String generateReadme(ProjectRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(request.getName()).append("\n\n");
        sb.append(request.getDescription()).append("\n\n");
        sb.append("## Build Information\n\n");
        sb.append("- **Java Version**: ").append(request.getJavaVersion()).append("\n");
        sb.append("- **Spring Boot Version**: ").append(request.getSpringBootVersion()).append("\n");
        sb.append("- **Build Tool**: ").append(request.getProjectType()).append("\n");
        sb.append("- **Packaging**: ").append(request.getPackaging()).append("\n\n");
        sb.append("## Getting Started\n\n");
        
        if ("maven".equals(request.getProjectType())) {
            sb.append("### Maven\n");
            sb.append("```bash\n");
            sb.append("./mvnw spring-boot:run\n");
            sb.append("```\n");
        } else {
            sb.append("### Gradle\n");
            sb.append("```bash\n");
            sb.append("./gradlew bootRun\n");
            sb.append("```\n");
        }
        
        return sb.toString();
    }

    private String generateGitignore(ProjectRequest request) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("HELP.md\n");
        sb.append("target/\n");
        sb.append("!.mvn/wrapper/maven-wrapper.jar\n");
        sb.append("!**/src/main/**/target/\n");
        sb.append("!**/src/test/**/target/\n\n");
        
        sb.append("### STS ###\n");
        sb.append(".apt_generated\n");
        sb.append(".classpath\n");
        sb.append(".factorypath\n");
        sb.append(".project\n");
        sb.append(".settings\n");
        sb.append(".springBeans\n");
        sb.append(".sts4-cache\n\n");
        
        sb.append("### IntelliJ IDEA ###\n");
        sb.append(".idea\n");
        sb.append("*.iws\n");
        sb.append("*.iml\n");
        sb.append("*.ipr\n\n");
        
        sb.append("### NetBeans ###\n");
        sb.append("/nbproject/private/\n");
        sb.append("/nbbuild/\n");
        sb.append("/dist/\n");
        sb.append("/nbdist/\n");
        sb.append("/.nb-gradle/\n");
        sb.append("build/\n");
        sb.append("!**/src/main/**/build/\n");
        sb.append("!**/src/test/**/build/\n\n");
        
        sb.append("### VS Code ###\n");
        sb.append(".vscode/\n\n");
        
        // Gradle specific
        if ("gradle".equals(request.getProjectType())) {
            sb.append("### Gradle ###\n");
            sb.append(".gradle\n");
            sb.append("gradle-app.setting\n");
            sb.append("!gradle-wrapper.jar\n");
            sb.append(".gradletasknamecache\n\n");
        }
        
        // Maven specific
        if ("maven".equals(request.getProjectType())) {
            sb.append("### Maven ###\n");
            sb.append(".mvn/wrapper/maven-wrapper.jar\n");
            sb.append(".flattened-pom.xml\n\n");
        }
        
        sb.append("### Logs ###\n");
        sb.append("*.log\n");
        sb.append("logs/\n\n");
        
        sb.append("### OS ###\n");
        sb.append(".DS_Store\n");
        sb.append("Thumbs.db\n");
        
        return sb.toString();
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
