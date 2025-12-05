package com.leandrosnazareth.spring_kit.service;

import com.leandrosnazareth.spring_kit.model.CrudClassDefinition;
import com.leandrosnazareth.spring_kit.model.CrudFieldDefinition;
import com.leandrosnazareth.spring_kit.model.CrudGenerationRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class CrudScaffoldingService {

    private static final Map<String, String> FIELD_IMPORTS = Map.of(
        "BigDecimal", "java.math.BigDecimal",
        "LocalDate", "java.time.LocalDate",
        "LocalDateTime", "java.time.LocalDateTime"
    );

    public byte[] generateCrudModule(CrudGenerationRequest request) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        String moduleDir = sanitizeModuleName(request.getModuleName());
        String basePackage = sanitizePackageName(request.getBasePackage());
        String packagePath = basePackage.replace('.', '/');

        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (CrudClassDefinition classDefinition : request.getClasses()) {
                ProcessedClass processedClass = prepareClass(classDefinition);

                String entityContent = buildEntity(basePackage, processedClass);
                String dtoContent = buildDto(basePackage, processedClass);
                String repositoryContent = buildRepository(basePackage, processedClass);
                String serviceContent = buildService(basePackage, processedClass);
                String controllerContent = buildController(basePackage, processedClass);

                addFile(zos, moduleDir + "/src/main/java/" + packagePath + "/entity/" +
                    processedClass.entityName + ".java", entityContent);
                addFile(zos, moduleDir + "/src/main/java/" + packagePath + "/dto/" +
                    processedClass.dtoName + ".java", dtoContent);
                addFile(zos, moduleDir + "/src/main/java/" + packagePath + "/repository/" +
                    processedClass.repositoryName + ".java", repositoryContent);
                addFile(zos, moduleDir + "/src/main/java/" + packagePath + "/service/" +
                    processedClass.serviceName + ".java", serviceContent);
                addFile(zos, moduleDir + "/src/main/java/" + packagePath + "/controller/" +
                    processedClass.controllerName + ".java", controllerContent);
            }

            addFile(zos, moduleDir + "/README.md", buildReadme(request.getClasses(), basePackage));
        }

        return baos.toByteArray();
    }

    private void addFile(ZipOutputStream zos, String path, String content) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private ProcessedClass prepareClass(CrudClassDefinition definition) {
        String rawName = Optional.ofNullable(definition.getName()).orElse("Entity");
        String entityName = toPascalCase(rawName);
        if (!StringUtils.hasText(entityName)) {
            entityName = "GeneratedEntity";
        }
        String dtoName = entityName + "Dto";
        String repositoryName = entityName + "Repository";
        String serviceName = entityName + "Service";
        String controllerName = entityName + "Controller";
        String tableName = StringUtils.hasText(definition.getTableName())
            ? definition.getTableName().trim()
            : toSnakeCase(entityName);

        List<CrudFieldDefinition> fields = new ArrayList<>(definition.getFields());
        CrudFieldDefinition idField = fields.stream()
            .filter(CrudFieldDefinition::isIdentifier)
            .findFirst()
            .orElseGet(() -> {
                CrudFieldDefinition identifier = new CrudFieldDefinition();
                identifier.setName("id");
                identifier.setType("Long");
                identifier.setIdentifier(true);
                identifier.setRequired(true);
                identifier.setUnique(true);
                fields.add(0, identifier);
                return identifier;
            });

        List<ProcessedField> processedFields = new ArrayList<>();
        for (CrudFieldDefinition field : fields) {
            processedFields.add(mapField(field));
        }

        ProcessedField identifier = processedFields.stream()
            .filter(ProcessedField::identifier)
            .findFirst()
            .orElseThrow();

        return new ProcessedClass(
            entityName,
            dtoName,
            repositoryName,
            serviceName,
            controllerName,
            identifier,
            processedFields,
            tableName
        );
    }

    private ProcessedField mapField(CrudFieldDefinition field) {
        String fieldName = toCamelCase(field.getName());
        if (!StringUtils.hasText(fieldName)) {
            fieldName = "field" + UUID.randomUUID().toString().replace("-", "");
        }
        String type = normalizeType(field.getType());
        boolean identifier = field.isIdentifier();
        return new ProcessedField(fieldName, type, identifier, field.isRequired(), field.isUnique());
    }

    private String buildEntity(String basePackage, ProcessedClass processedClass) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePackage).append(".entity;\n\n");
        sb.append("import jakarta.persistence.*;\n");
        Set<String> imports = resolveFieldImports(processedClass.fields());
        imports.forEach(i -> sb.append("import ").append(i).append(";\n"));
        if (!imports.isEmpty()) {
            sb.append("\n");
        } else {
            sb.append("\n");
        }

        sb.append("@Entity\n");
        sb.append("@Table(name = \"").append(processedClass.tableName()).append("\")\n");
        sb.append("public class ").append(processedClass.entityName()).append(" {\n\n");

        for (ProcessedField field : processedClass.fields()) {
            if (field.identifier()) {
                sb.append("    @Id\n");
                sb.append("    @GeneratedValue(strategy = GenerationType.IDENTITY)\n");
            } else {
                String columnAnnotation = buildColumnAnnotation(field);
                if (StringUtils.hasText(columnAnnotation)) {
                    sb.append("    ").append(columnAnnotation).append("\n");
                }
            }
            sb.append("    private ").append(field.type()).append(" ").append(field.name()).append(";\n\n");
        }

        for (ProcessedField field : processedClass.fields()) {
            String capitalized = StringUtils.capitalize(field.name());
            sb.append("    public ").append(field.type()).append(" get").append(capitalized).append("() {\n");
            sb.append("        return ").append(field.name()).append(";\n");
            sb.append("    }\n\n");
            sb.append("    public void set").append(capitalized).append("(").append(field.type())
                .append(" ").append(field.name()).append(") {\n");
            sb.append("        this.").append(field.name()).append(" = ").append(field.name()).append(";\n");
            sb.append("    }\n\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String buildDto(String basePackage, ProcessedClass processedClass) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePackage).append(".dto;\n\n");
        Set<String> imports = resolveFieldImports(processedClass.fields());
        imports.forEach(i -> sb.append("import ").append(i).append(";\n"));
        if (!imports.isEmpty()) {
            sb.append("\n");
        }
        sb.append("public class ").append(processedClass.dtoName()).append(" {\n\n");

        for (ProcessedField field : processedClass.fields()) {
            sb.append("    private ").append(field.type()).append(" ").append(field.name()).append(";\n");
        }
        sb.append("\n");

        for (ProcessedField field : processedClass.fields()) {
            String capitalized = StringUtils.capitalize(field.name());
            sb.append("    public ").append(field.type()).append(" get").append(capitalized).append("() {\n");
            sb.append("        return ").append(field.name()).append(";\n");
            sb.append("    }\n\n");
            sb.append("    public void set").append(capitalized).append("(").append(field.type())
                .append(" ").append(field.name()).append(") {\n");
            sb.append("        this.").append(field.name()).append(" = ").append(field.name()).append(";\n");
            sb.append("    }\n\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String buildRepository(String basePackage, ProcessedClass processedClass) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePackage).append(".repository;\n\n");
        sb.append("import ").append(basePackage).append(".entity.").append(processedClass.entityName()).append(";\n");
        sb.append("import org.springframework.data.jpa.repository.JpaRepository;\n");
        sb.append("import org.springframework.stereotype.Repository;\n\n");

        sb.append("@Repository\n");
        sb.append("public interface ").append(processedClass.repositoryName()).append(" extends JpaRepository<")
            .append(processedClass.entityName()).append(", ").append(processedClass.identifier().type()).append("> {\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String buildService(String basePackage, ProcessedClass processedClass) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePackage).append(".service;\n\n");
        sb.append("import ").append(basePackage).append(".dto.").append(processedClass.dtoName()).append(";\n");
        sb.append("import ").append(basePackage).append(".entity.").append(processedClass.entityName()).append(";\n");
        sb.append("import ").append(basePackage).append(".repository.").append(processedClass.repositoryName()).append(";\n");
        sb.append("import org.springframework.http.HttpStatus;\n");
        sb.append("import org.springframework.stereotype.Service;\n");
        sb.append("import org.springframework.web.server.ResponseStatusException;\n");
        sb.append("import java.util.List;\n");
        sb.append("import java.util.stream.Collectors;\n\n");

        sb.append("@Service\n");
        sb.append("public class ").append(processedClass.serviceName()).append(" {\n\n");
        sb.append("    private final ").append(processedClass.repositoryName()).append(" repository;\n\n");
        sb.append("    public ").append(processedClass.serviceName()).append("(")
            .append(processedClass.repositoryName()).append(" repository) {\n");
        sb.append("        this.repository = repository;\n");
        sb.append("    }\n\n");

        sb.append("    public List<").append(processedClass.dtoName()).append("> findAll() {\n");
        sb.append("        return repository.findAll()\n");
        sb.append("            .stream()\n");
        sb.append("            .map(this::toDto)\n");
        sb.append("            .collect(Collectors.toList());\n");
        sb.append("    }\n\n");

        sb.append("    public ").append(processedClass.dtoName()).append(" findById(")
            .append(processedClass.identifier().type()).append(" id) {\n");
        sb.append("        ").append(processedClass.entityName()).append(" entity = repository.findById(id)\n");
        sb.append("            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, \"")
            .append(processedClass.entityName()).append(" not found\"));\n");
        sb.append("        return toDto(entity);\n");
        sb.append("    }\n\n");

        sb.append("    public ").append(processedClass.dtoName()).append(" create(")
            .append(processedClass.dtoName()).append(" dto) {\n");
        sb.append("        ").append(processedClass.entityName()).append(" entity = toEntity(dto);\n");
        sb.append("        ").append(processedClass.entityName()).append(" saved = repository.save(entity);\n");
        sb.append("        return toDto(saved);\n");
        sb.append("    }\n\n");

        sb.append("    public ").append(processedClass.dtoName()).append(" update(")
            .append(processedClass.identifier().type()).append(" id, ")
            .append(processedClass.dtoName()).append(" dto) {\n");
        sb.append("        ").append(processedClass.entityName()).append(" entity = repository.findById(id)\n");
        sb.append("            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, \"")
            .append(processedClass.entityName()).append(" not found\"));\n");
        sb.append("        applyNonIdentifierFields(dto, entity);\n");
        sb.append("        ").append(processedClass.entityName()).append(" saved = repository.save(entity);\n");
        sb.append("        return toDto(saved);\n");
        sb.append("    }\n\n");

        sb.append("    public void delete(").append(processedClass.identifier().type()).append(" id) {\n");
        sb.append("        if (!repository.existsById(id)) {\n");
        sb.append("            throw new ResponseStatusException(HttpStatus.NOT_FOUND, \"")
            .append(processedClass.entityName()).append(" not found\");\n");
        sb.append("        }\n");
        sb.append("        repository.deleteById(id);\n");
        sb.append("    }\n\n");

        sb.append("    private ").append(processedClass.dtoName()).append(" toDto(")
            .append(processedClass.entityName()).append(" entity) {\n");
        sb.append("        ").append(processedClass.dtoName()).append(" dto = new ")
            .append(processedClass.dtoName()).append("();\n");
        for (ProcessedField field : processedClass.fields()) {
            String capitalized = StringUtils.capitalize(field.name());
            sb.append("        dto.set").append(capitalized).append("(entity.get").append(capitalized).append("());\n");
        }
        sb.append("        return dto;\n");
        sb.append("    }\n\n");

        sb.append("    private ").append(processedClass.entityName()).append(" toEntity(")
            .append(processedClass.dtoName()).append(" dto) {\n");
        sb.append("        ").append(processedClass.entityName()).append(" entity = new ")
            .append(processedClass.entityName()).append("();\n");
        for (ProcessedField field : processedClass.fields()) {
            String capitalized = StringUtils.capitalize(field.name());
            sb.append("        entity.set").append(capitalized).append("(dto.get").append(capitalized).append("());\n");
        }
        sb.append("        return entity;\n");
        sb.append("    }\n\n");

        sb.append("    private void applyNonIdentifierFields(")
            .append(processedClass.dtoName()).append(" dto, ")
            .append(processedClass.entityName()).append(" entity) {\n");
        for (ProcessedField field : processedClass.fields()) {
            if (field.identifier()) {
                continue;
            }
            String capitalized = StringUtils.capitalize(field.name());
            sb.append("        entity.set").append(capitalized).append("(dto.get")
                .append(capitalized).append("());\n");
        }
        sb.append("    }\n");

        sb.append("}\n");
        return sb.toString();
    }

    private String buildController(String basePackage, ProcessedClass processedClass) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePackage).append(".controller;\n\n");
        sb.append("import ").append(basePackage).append(".dto.").append(processedClass.dtoName()).append(";\n");
        sb.append("import ").append(basePackage).append(".service.").append(processedClass.serviceName()).append(";\n");
        sb.append("import org.springframework.http.HttpStatus;\n");
        sb.append("import org.springframework.web.bind.annotation.*;\n");
        sb.append("import java.util.List;\n\n");

        sb.append("@RestController\n");
        sb.append("@RequestMapping(\"/api/").append(buildControllerPath(processedClass.entityName())).append("\")\n");
        sb.append("public class ").append(processedClass.controllerName()).append(" {\n\n");
        sb.append("    private final ").append(processedClass.serviceName()).append(" service;\n\n");
        sb.append("    public ").append(processedClass.controllerName()).append("(")
            .append(processedClass.serviceName()).append(" service) {\n");
        sb.append("        this.service = service;\n");
        sb.append("    }\n\n");

        sb.append("    @GetMapping\n");
        sb.append("    public List<").append(processedClass.dtoName()).append("> findAll() {\n");
        sb.append("        return service.findAll();\n");
        sb.append("    }\n\n");

        sb.append("    @GetMapping(\"/{id}\")\n");
        sb.append("    public ").append(processedClass.dtoName()).append(" findById(@PathVariable ")
            .append(processedClass.identifier().type()).append(" id) {\n");
        sb.append("        return service.findById(id);\n");
        sb.append("    }\n\n");

        sb.append("    @PostMapping\n");
        sb.append("    @ResponseStatus(HttpStatus.CREATED)\n");
        sb.append("    public ").append(processedClass.dtoName()).append(" create(@RequestBody ")
            .append(processedClass.dtoName()).append(" dto) {\n");
        sb.append("        return service.create(dto);\n");
        sb.append("    }\n\n");

        sb.append("    @PutMapping(\"/{id}\")\n");
        sb.append("    public ").append(processedClass.dtoName()).append(" update(@PathVariable ")
            .append(processedClass.identifier().type()).append(" id, @RequestBody ")
            .append(processedClass.dtoName()).append(" dto) {\n");
        sb.append("        return service.update(id, dto);\n");
        sb.append("    }\n\n");

        sb.append("    @DeleteMapping(\"/{id}\")\n");
        sb.append("    @ResponseStatus(HttpStatus.NO_CONTENT)\n");
        sb.append("    public void delete(@PathVariable ").append(processedClass.identifier().type()).append(" id) {\n");
        sb.append("        service.delete(id);\n");
        sb.append("    }\n");

        sb.append("}\n");
        return sb.toString();
    }

    private String buildColumnAnnotation(ProcessedField field) {
        List<String> attributes = new ArrayList<>();
        if (field.required()) {
            attributes.add("nullable = false");
        }
        if (field.unique()) {
            attributes.add("unique = true");
        }
        if (attributes.isEmpty()) {
            return "";
        }
        return "@Column(" + String.join(", ", attributes) + ")";
    }

    private Set<String> resolveFieldImports(List<ProcessedField> fields) {
        Set<String> imports = new TreeSet<>();
        for (ProcessedField field : fields) {
            String importName = FIELD_IMPORTS.get(field.type());
            if (importName != null) {
                imports.add(importName);
            }
        }
        return imports;
    }

    private String buildReadme(List<CrudClassDefinition> classes, String basePackage) {
        StringBuilder sb = new StringBuilder();
        sb.append("# CRUD Module\n\n");
        sb.append("Base package: `").append(basePackage).append("`\n\n");
        sb.append("Classes generated:\n");
        for (CrudClassDefinition definition : classes) {
            sb.append("- ").append(definition.getName()).append("\n");
        }
        sb.append("\nGenerated layers: Entity, DTO, Repository, Service, Controller.\n");
        return sb.toString();
    }

    private String sanitizeModuleName(String name) {
        String safe = Optional.ofNullable(name).orElse("crud-module").trim();
        if (safe.isEmpty()) {
            safe = "crud-module";
        }
        return safe.replaceAll("[^a-zA-Z0-9-_]", "-").toLowerCase(Locale.ROOT);
    }

    private String sanitizePackageName(String packageName) {
        String safe = Optional.ofNullable(packageName).orElse("com.example.demo").trim();
        if (safe.isEmpty()) {
            return "com.example.demo";
        }
        return safe;
    }

    private String toPascalCase(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String[] parts = value.replaceAll("[^a-zA-Z0-9]+", " ").split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(StringUtils.capitalize(part.toLowerCase(Locale.ROOT)));
        }
        return sb.toString();
    }

    private String toCamelCase(String value) {
        String pascal = toPascalCase(value);
        if (!StringUtils.hasText(pascal)) {
            return "";
        }
        return pascal.substring(0, 1).toLowerCase(Locale.ROOT) + pascal.substring(1);
    }

    private String toSnakeCase(String value) {
        if (!StringUtils.hasText(value)) {
            return "generated_table";
        }
        return value.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private String buildControllerPath(String entityName) {
        String kebab = entityName.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
        if (!kebab.endsWith("s")) {
            kebab = kebab + "s";
        }
        return kebab;
    }

    private String normalizeType(String type) {
        String candidate = Optional.ofNullable(type).orElse("String").trim();
        if (candidate.isEmpty()) {
            return "String";
        }
        switch (candidate.toLowerCase(Locale.ROOT)) {
            case "long":
                return "Long";
            case "int":
            case "integer":
                return "Integer";
            case "double":
                return "Double";
            case "bigdecimal":
                return "BigDecimal";
            case "localdate":
                return "LocalDate";
            case "localdatetime":
                return "LocalDateTime";
            case "boolean":
            case "bool":
                return "Boolean";
            default:
                return StringUtils.capitalize(candidate);
        }
    }

    private record ProcessedField(
        String name,
        String type,
        boolean identifier,
        boolean required,
        boolean unique
    ) { }

    private record ProcessedClass(
        String entityName,
        String dtoName,
        String repositoryName,
        String serviceName,
        String controllerName,
        ProcessedField identifier,
        List<ProcessedField> fields,
        String tableName
    ) { }
}
