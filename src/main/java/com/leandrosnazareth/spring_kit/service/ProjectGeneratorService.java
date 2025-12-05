package com.leandrosnazareth.spring_kit.service;

import com.leandrosnazareth.spring_kit.model.Dependency;
import com.leandrosnazareth.spring_kit.model.ProjectRequest;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ProjectGeneratorService {

    private final DependencyService dependencyService;

    public ProjectGeneratorService(DependencyService dependencyService) {
        this.dependencyService = dependencyService;
    }

    public byte[] generateProject(ProjectRequest request) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);

        String baseDir = request.getArtifactId() + "/";
        String srcMainJava = baseDir + "src/main/java/" + request.getPackageName().replace(".", "/") + "/";
        String srcMainResources = baseDir + "src/main/resources/";
        String srcTestJava = baseDir + "src/test/java/" + request.getPackageName().replace(".", "/") + "/";

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
        addFileToZip(zos, srcMainResources + "application.properties", "# Application Configuration\n");

        // Generate test class
        addFileToZip(zos, srcTestJava + capitalize(request.getName()) + "ApplicationTests.java", 
            generateTestClass(request));

        // Generate README
        addFileToZip(zos, baseDir + "README.md", generateReadme(request));

        // Generate .gitignore
        addFileToZip(zos, baseDir + ".gitignore", generateGitignore(request));

        zos.close();
        return baos.toByteArray();
    }

    private void addFileToZip(ZipOutputStream zos, String fileName, String content) throws IOException {
        ZipEntry entry = new ZipEntry(fileName);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
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

    private String generateTestClass(ProjectRequest request) {
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
