package com.leandrosnazareth.spring_kit.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.leandrosnazareth.spring_kit.model.CrudClassDefinition;
import com.leandrosnazareth.spring_kit.model.CrudFieldDefinition;
import com.leandrosnazareth.spring_kit.model.CrudGenerationRequest;
import com.leandrosnazareth.spring_kit.model.CrudMethodDefinition;
import com.leandrosnazareth.spring_kit.model.CrudMethodParameterDefinition;
import com.leandrosnazareth.spring_kit.model.CrudRelationshipType;
import com.leandrosnazareth.spring_kit.model.CrudStructureType;

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
        request.setBasePackage(basePackage);
        String packagePath = basePackage.replace('.', '/');

        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            writeCrudArtifacts(
                request,
                basePackage,
                moduleDir + "/src/main/java/" + packagePath + "/",
                moduleDir + "/src/test/java/" + packagePath + "/",
                moduleDir + "/src/main/resources/templates/",
                zos
            );
            addFile(zos, moduleDir + "/README.md", buildReadme(request, basePackage));
        }

        return baos.toByteArray();
    }

    public void appendCrudToProject(CrudGenerationRequest request, String srcMainJavaBasePath,
                                    String srcTestJavaBasePath, String templatesBasePath,
                                    String basePackage, ZipOutputStream zos) throws IOException {
        String sanitizedPackage = sanitizePackageName(basePackage);
        request.setBasePackage(sanitizedPackage);
        writeCrudArtifacts(request, sanitizedPackage, srcMainJavaBasePath, srcTestJavaBasePath,
            templatesBasePath, zos);
    }

    private void addFile(ZipOutputStream zos, String path, String content) throws IOException {
        ZipEntry entry = new ZipEntry(path);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private void writeCrudArtifacts(CrudGenerationRequest request, String basePackage,
                                    String destinationJavaBasePath, String destinationTestJavaBasePath,
                                    String destinationTemplatesPath,
                                    ZipOutputStream zos) throws IOException {
        if (request.getClasses() == null) {
            return;
        }
        boolean thymeleafViews = request.isThymeleafViews();
        boolean useLombok = request.isUseLombok();
        boolean useJakarta = request.isUseJakartaPersistence();
        String normalizedTemplatePath = destinationTemplatesPath;
        if (normalizedTemplatePath != null && !normalizedTemplatePath.endsWith("/")) {
            normalizedTemplatePath = normalizedTemplatePath + "/";
        }
        String normalizedTestPath = destinationTestJavaBasePath;
        if (normalizedTestPath != null && !normalizedTestPath.endsWith("/")) {
            normalizedTestPath = normalizedTestPath + "/";
        }
        boolean includeTests = request.isGenerateTests() && normalizedTestPath != null;
        Map<String, CrudStructureType> structureLookup = new LinkedHashMap<>();
        for (CrudClassDefinition definition : request.getClasses()) {
            String rawName = Optional.ofNullable(definition.getName()).orElse("Structure");
            String safeName = toPascalCase(rawName);
            CrudStructureType type = Optional.ofNullable(definition.getStructureType())
                .orElse(CrudStructureType.CLASS);
            structureLookup.put(safeName, type);
        }

        List<ProcessedClass> processedEntities = new ArrayList<>();
        List<SupplementalStructure> supplementalStructures = new ArrayList<>();
        for (CrudClassDefinition definition : request.getClasses()) {
            CrudStructureType type = Optional.ofNullable(definition.getStructureType())
                .orElse(CrudStructureType.CLASS);
            if (type == CrudStructureType.CLASS) {
                processedEntities.add(prepareClass(definition, structureLookup));
            } else {
                supplementalStructures.add(prepareSupplementalStructure(definition, type, structureLookup));
            }
        }

        Map<String, ProcessedClass> processedClassMap = processedEntities.stream()
            .collect(Collectors.toMap(ProcessedClass::entityName, cls -> cls, (a, b) -> a, LinkedHashMap::new));
        Map<String, String> customTypePackages = new LinkedHashMap<>();
        processedEntities.forEach(entity -> customTypePackages.put(entity.entityName(), basePackage + ".entity"));
        supplementalStructures.forEach(structure -> customTypePackages.put(structure.name(), basePackage + ".model"));

        for (ProcessedClass processedClass : processedEntities) {

            String entityContent = buildEntity(basePackage, processedClass, useLombok, useJakarta,
                processedClassMap, customTypePackages);
            String dtoContent = buildDto(basePackage, processedClass, useLombok, processedClassMap, customTypePackages);
            String repositoryContent = buildRepository(basePackage, processedClass);
            String serviceContent = buildService(basePackage, processedClass, processedClassMap);
            String controllerContent = thymeleafViews
                ? buildMvcController(basePackage, processedClass, processedClassMap)
                : buildRestController(basePackage, processedClass);

            addFile(zos, destinationJavaBasePath + "entity/" + processedClass.entityName + ".java", entityContent);
            addFile(zos, destinationJavaBasePath + "dto/" + processedClass.dtoName + ".java", dtoContent);
            addFile(zos, destinationJavaBasePath + "repository/" + processedClass.repositoryName + ".java", repositoryContent);
            addFile(zos, destinationJavaBasePath + "service/" + processedClass.serviceName + ".java", serviceContent);
            addFile(zos, destinationJavaBasePath + "controller/" + processedClass.controllerName + ".java", controllerContent);

            if (thymeleafViews && normalizedTemplatePath != null) {
                String folder = buildControllerPath(processedClass.entityName());
                String baseTemplateDir = normalizedTemplatePath + folder + "/";
                addFile(zos, baseTemplateDir + "list.html", buildListTemplate(processedClass, folder));
                addFile(zos, baseTemplateDir + "form.html", buildFormTemplate(processedClass, folder, processedClassMap));
            }

            if (includeTests) {
                addFile(zos, normalizedTestPath + "service/" + processedClass.serviceName() + "Test.java",
                    buildServiceTest(basePackage, processedClass));
                addFile(zos, normalizedTestPath + "controller/" + processedClass.controllerName() + "Test.java",
                    buildControllerTest(basePackage, processedClass, processedClassMap, thymeleafViews));
            }
        }

        for (SupplementalStructure supplementalStructure : supplementalStructures) {
            String targetPath = destinationJavaBasePath + "model/" + supplementalStructure.name() + ".java";
            String content = switch (supplementalStructure.type()) {
                case ABSTRACT_CLASS -> buildPlainClass(basePackage, supplementalStructure, useLombok, customTypePackages);
                case INTERFACE -> buildInterface(basePackage, supplementalStructure, customTypePackages);
                case ENUM -> buildEnum(basePackage, supplementalStructure, customTypePackages);
                default -> "";
            };
            if (StringUtils.hasText(content)) {
                addFile(zos, targetPath, content);
            }
        }
    }

    private ProcessedClass prepareClass(CrudClassDefinition definition,
                                        Map<String, CrudStructureType> structureLookup) {
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

        List<CrudFieldDefinition> fields = new ArrayList<>();
        if (definition.getFields() != null) {
            fields.addAll(definition.getFields());
        }
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
            processedFields.add(mapField(field, structureLookup));
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

    private SupplementalStructure prepareSupplementalStructure(CrudClassDefinition definition,
                                                               CrudStructureType type,
                                                               Map<String, CrudStructureType> structureLookup) {
        String rawName = Optional.ofNullable(definition.getName()).orElse("Structure");
        String name = toPascalCase(rawName);
        if (!StringUtils.hasText(name)) {
            name = "SupplementalStructure";
        }
        List<ProcessedField> fields = new ArrayList<>();
        if (definition.getFields() != null) {
            for (CrudFieldDefinition fieldDefinition : definition.getFields()) {
                fields.add(mapField(fieldDefinition, structureLookup));
            }
        }
        List<ProcessedMethod> methods = processMethods(definition.getMethods(), type);
        List<String> enumConstants = new ArrayList<>();
        if (definition.getEnumConstants() != null) {
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            for (String constant : definition.getEnumConstants()) {
                String normalizedName = toConstantCase(constant);
                if (StringUtils.hasText(normalizedName)) {
                    normalized.add(normalizedName);
                }
            }
            enumConstants.addAll(normalized);
        }
        return new SupplementalStructure(name, type, fields, methods, enumConstants);
    }

    private ProcessedField mapField(CrudFieldDefinition field,
                                    Map<String, CrudStructureType> structureLookup) {
        String fieldName = toCamelCase(field.getName());
        if (!StringUtils.hasText(fieldName)) {
            fieldName = "field" + UUID.randomUUID().toString().replace("-", "");
        }
        boolean objectField = field.isObjectType();
        String type;
        String targetEntity = null;
        CrudRelationshipType relationshipType = field.getRelationshipType();
        if (objectField) {
            targetEntity = toPascalCase(Optional.ofNullable(field.getTargetClassName()).orElse(""));
            if (!StringUtils.hasText(targetEntity)
                || structureLookup.getOrDefault(targetEntity, CrudStructureType.CLASS) != CrudStructureType.CLASS) {
                objectField = false;
                targetEntity = null;
            }
            type = targetEntity;
        } else {
            type = normalizeType(field.getType());
        }
        boolean identifier = field.isIdentifier();
        return new ProcessedField(fieldName, type, identifier, field.isRequired(),
            field.isUnique(), objectField, relationshipType, targetEntity);
    }

    private String buildEntity(String basePackage, ProcessedClass processedClass, boolean useLombok,
                               boolean useJakarta, Map<String, ProcessedClass> processedClasses,
                               Map<String, String> customTypePackages) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePackage).append(".entity;\n\n");
        String persistenceImport = useJakarta ? "jakarta.persistence" : "javax.persistence";
        sb.append("import ").append(persistenceImport).append(".*;\n");
        Set<String> imports = resolveFieldImports(processedClass.fields(), basePackage + ".entity", customTypePackages);
        if (useLombok) {
            sb.append("import lombok.AllArgsConstructor;\n");
            sb.append("import lombok.Data;\n");
            sb.append("import lombok.NoArgsConstructor;\n");
        }
        imports.forEach(i -> sb.append("import ").append(i).append(";\n"));
        sb.append("\n");

        if (useLombok) {
            sb.append("@Data\n");
            sb.append("@NoArgsConstructor\n");
            sb.append("@AllArgsConstructor\n");
        }
        sb.append("@Entity\n");
        sb.append("@Table(name = \"").append(processedClass.tableName()).append("\")\n");
        sb.append("public class ").append(processedClass.entityName()).append(" {\n\n");

        for (ProcessedField field : processedClass.fields()) {
            if (field.identifier()) {
                sb.append("    @Id\n");
                sb.append("    @GeneratedValue(strategy = GenerationType.IDENTITY)\n");
            } else if (!field.objectField()) {
                String columnAnnotation = buildColumnAnnotation(field);
                if (StringUtils.hasText(columnAnnotation)) {
                    sb.append("    ").append(columnAnnotation).append("\n");
                }
            }
            if (field.objectField()) {
                String annotations = buildRelationshipAnnotations(field, processedClass, processedClasses);
                if (StringUtils.hasText(annotations)) {
                    sb.append(annotations);
                }
            }
            sb.append("    private ").append(resolveEntityFieldType(field)).append(" ").append(field.name()).append(";\n\n");
        }

        if (!useLombok) {
            List<GeneratedField> generatedFields = processedClass.fields().stream()
                .map(f -> new GeneratedField(f.name(), resolveEntityFieldType(f)))
                .collect(Collectors.toList());
            appendConstructors(sb, processedClass.entityName(), generatedFields);
            appendGettersAndSetters(sb, generatedFields);
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String buildDto(String basePackage, ProcessedClass processedClass, boolean useLombok,
                            Map<String, ProcessedClass> processedClasses,
                            Map<String, String> customTypePackages) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePackage).append(".dto;\n\n");
        Set<String> imports = new TreeSet<>();
        if (useLombok) {
            sb.append("import lombok.AllArgsConstructor;\n");
            sb.append("import lombok.Data;\n");
            sb.append("import lombok.NoArgsConstructor;\n");
        }
        List<GeneratedField> dtoFields = buildDtoFields(processedClass, processedClasses);
        dtoFields.forEach(field -> {
            if (requiresListImport(field.type())) {
                imports.add("java.util.List");
            }
            addCustomImport(field.type(), basePackage + ".dto", customTypePackages, imports);
        });
        imports.forEach(i -> sb.append("import ").append(i).append(";\n"));
        if (!imports.isEmpty()) {
            sb.append("\n");
        }
        if (useLombok) {
            sb.append("@Data\n");
            sb.append("@NoArgsConstructor\n");
            sb.append("@AllArgsConstructor\n");
        }
        sb.append("public class ").append(processedClass.dtoName()).append(" {\n\n");

        for (GeneratedField field : dtoFields) {
            sb.append("    private ").append(field.type()).append(" ").append(field.name()).append(";\n");
        }
        sb.append("\n");

        if (!useLombok) {
            appendConstructors(sb, processedClass.dtoName(), dtoFields);
            appendGettersAndSetters(sb, dtoFields);
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String buildPlainClass(String basePackage, SupplementalStructure structure, boolean useLombok,
                                   Map<String, String> customTypePackages) {
        String packageName = basePackage + ".model";
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n");
        Set<String> imports = new TreeSet<>();
        imports.addAll(resolveFieldImports(structure.fields(), packageName, customTypePackages));
        imports.addAll(resolveMethodImports(structure.methods(), packageName, customTypePackages));
        if (useLombok) {
            sb.append("import lombok.AllArgsConstructor;\n");
            sb.append("import lombok.Data;\n");
            sb.append("import lombok.NoArgsConstructor;\n");
        }
        imports.forEach(i -> sb.append("import ").append(i).append(";\n"));
        if (!imports.isEmpty()) {
            sb.append("\n");
        }
        if (useLombok) {
            sb.append("@Data\n");
            sb.append("@NoArgsConstructor\n");
            sb.append("@AllArgsConstructor\n");
        }
        sb.append("public ");
        if (structure.type() == CrudStructureType.ABSTRACT_CLASS) {
            sb.append("abstract ");
        }
        sb.append("class ").append(structure.name()).append(" {\n\n");

        List<GeneratedField> fields = structure.fields().stream()
            .map(field -> new GeneratedField(field.name(), field.type()))
            .collect(Collectors.toList());
        for (GeneratedField field : fields) {
            sb.append("    private ").append(field.type()).append(" ").append(field.name()).append(";\n");
        }
        if (!fields.isEmpty()) {
            sb.append("\n");
        }
        if (!useLombok) {
            appendConstructors(sb, structure.name(), fields);
            appendGettersAndSetters(sb, fields);
        }
        appendClassMethods(sb, structure.methods(), structure.type() == CrudStructureType.ABSTRACT_CLASS);
        sb.append("}\n");
        return sb.toString();
    }

    private String buildInterface(String basePackage, SupplementalStructure structure,
                                  Map<String, String> customTypePackages) {
        String packageName = basePackage + ".model";
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n");
        Set<String> imports = new TreeSet<>();
        imports.addAll(resolveFieldImports(structure.fields(), packageName, customTypePackages));
        imports.addAll(resolveMethodImports(structure.methods(), packageName, customTypePackages));
        imports.forEach(i -> sb.append("import ").append(i).append(";\n"));
        if (!imports.isEmpty()) {
            sb.append("\n");
        }
        sb.append("public interface ").append(structure.name()).append(" {\n\n");
        for (ProcessedField field : structure.fields()) {
            String constantName = toConstantCase(field.name());
            String defaultValue = resolveDefaultValueLiteral(field.type());
            sb.append("    ").append("public static final ").append(field.type())
                .append(" ").append(constantName).append(" = ").append(defaultValue).append(";\n");
        }
        if (!structure.fields().isEmpty()) {
            sb.append("\n");
        }
        appendInterfaceMethods(sb, structure.methods());
        sb.append("}\n");
        return sb.toString();
    }

    private String buildEnum(String basePackage, SupplementalStructure structure,
                             Map<String, String> customTypePackages) {
        String packageName = basePackage + ".model";
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(packageName).append(";\n\n");
        Set<String> imports = resolveMethodImports(structure.methods(), packageName, customTypePackages);
        imports.forEach(i -> sb.append("import ").append(i).append(";\n"));
        if (!imports.isEmpty()) {
            sb.append("\n");
        }
        sb.append("public enum ").append(structure.name()).append(" {\n");
        List<String> constants = structure.enumConstants().isEmpty()
            ? List.of("DEFAULT_VALUE")
            : structure.enumConstants();
        sb.append("    ").append(String.join(",\n    ", constants)).append(";\n\n");
        appendClassMethods(sb, structure.methods(), false);
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

    private String buildService(String basePackage, ProcessedClass processedClass,
                                Map<String, ProcessedClass> processedClasses) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePackage).append(".service;\n\n");
        sb.append("import ").append(basePackage).append(".dto.").append(processedClass.dtoName()).append(";\n");
        sb.append("import ").append(basePackage).append(".entity.").append(processedClass.entityName()).append(";\n");
        sb.append("import ").append(basePackage).append(".repository.").append(processedClass.repositoryName()).append(";\n");
        sb.append("import org.springframework.http.HttpStatus;\n");
        sb.append("import org.springframework.stereotype.Service;\n");
        sb.append("import org.springframework.web.server.ResponseStatusException;\n");
        if (processedClass.fields().stream().anyMatch(f -> f.objectField() && f.isCollection())) {
            sb.append("import java.util.ArrayList;\n");
        }
        sb.append("import java.util.Collections;\n");
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
            appendDtoMapping(sb, field, processedClasses);
        }
        sb.append("        return dto;\n");
        sb.append("    }\n\n");

        sb.append("    private ").append(processedClass.entityName()).append(" toEntity(")
            .append(processedClass.dtoName()).append(" dto) {\n");
        sb.append("        ").append(processedClass.entityName()).append(" entity = new ")
            .append(processedClass.entityName()).append("();\n");
        for (ProcessedField field : processedClass.fields()) {
            appendEntityAssignment(sb, field, processedClasses);
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
            appendEntityAssignment(sb, field, processedClasses);
        }
        sb.append("    }\n");

        sb.append("}\n");
        return sb.toString();
    }

    private void appendDtoMapping(StringBuilder sb, ProcessedField field,
                                  Map<String, ProcessedClass> processedClasses) {
        String entityGetter = "get" + StringUtils.capitalize(field.name());
        if (!field.objectField()) {
            sb.append("        dto.set").append(StringUtils.capitalize(field.name()))
                .append("(entity.").append(entityGetter).append("());\n");
            return;
        }
        String dtoSetter = getDtoSetterName(field);
        String targetIdGetter = "get" + StringUtils.capitalize(resolveTargetIdFieldName(field, processedClasses));
        if (field.isCollection()) {
            sb.append("        dto.").append(dtoSetter).append("(entity.")
                .append(entityGetter).append("() == null ? null : entity.")
                .append(entityGetter).append("().stream()\n");
            sb.append("            .map(").append(field.targetEntity()).append("::")
                .append(targetIdGetter).append(")\n");
            sb.append("            .collect(Collectors.toList()));\n");
        } else {
            sb.append("        dto.").append(dtoSetter).append("(entity.")
                .append(entityGetter).append("() != null ? entity.")
                .append(entityGetter).append("().").append(targetIdGetter)
                .append("() : null);\n");
        }
    }

    private void appendEntityAssignment(StringBuilder sb, ProcessedField field,
                                        Map<String, ProcessedClass> processedClasses) {
        String entitySetter = "set" + StringUtils.capitalize(field.name());
        if (!field.objectField()) {
            String dtoGetter = "get" + StringUtils.capitalize(field.name());
            sb.append("        entity.").append(entitySetter).append("(dto.")
                .append(dtoGetter).append("());\n");
            return;
        }
        String dtoGetter = getDtoGetterName(field);
        String targetIdSetter = "set" + StringUtils.capitalize(resolveTargetIdFieldName(field, processedClasses));
        if (field.isCollection()) {
            sb.append("        if (dto.").append(dtoGetter).append("() != null) {\n");
            sb.append("            List<").append(field.targetEntity()).append("> related = new ArrayList<>();\n");
            sb.append("            for (").append(resolveTargetIdType(field, processedClasses)).append(" id : dto.")
                .append(dtoGetter).append("()) {\n");
            sb.append("                ").append(field.targetEntity()).append(" rel = new ")
                .append(field.targetEntity()).append("();\n");
            sb.append("                rel.").append(targetIdSetter).append("(id);\n");
            sb.append("                related.add(rel);\n");
            sb.append("            }\n");
            sb.append("            entity.").append(entitySetter).append("(related);\n");
            sb.append("        } else {\n");
            sb.append("            entity.").append(entitySetter).append("(new ArrayList<>());\n");
            sb.append("        }\n");
        } else {
            sb.append("        if (dto.").append(dtoGetter).append("() != null) {\n");
            sb.append("            ").append(field.targetEntity()).append(" related = new ")
                .append(field.targetEntity()).append("();\n");
            sb.append("            related.").append(targetIdSetter).append("(dto.")
                .append(dtoGetter).append("());\n");
            sb.append("            entity.").append(entitySetter).append("(related);\n");
            sb.append("        } else {\n");
            sb.append("            entity.").append(entitySetter).append("(null);\n");
            sb.append("        }\n");
        }
    }

    private String buildRestController(String basePackage, ProcessedClass processedClass) {
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

    private String buildMvcController(String basePackage, ProcessedClass processedClass,
                                      Map<String, ProcessedClass> processedClasses) {
        String controllerPath = buildControllerPath(processedClass.entityName());
        List<ProcessedField> relationshipFields = processedClass.fields().stream()
            .filter(field -> isRenderableRelationshipField(field, processedClasses))
            .collect(Collectors.toList());
        List<ProcessedClass> relationshipTargets = resolveRelationshipDependencies(processedClass, processedClasses);
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePackage).append(".controller;\n\n");
        sb.append("import ").append(basePackage).append(".dto.").append(processedClass.dtoName()).append(";\n");
        sb.append("import ").append(basePackage).append(".service.").append(processedClass.serviceName()).append(";\n");
        sb.append("import org.springframework.stereotype.Controller;\n");
        sb.append("import org.springframework.ui.Model;\n");
        sb.append("import org.springframework.web.bind.annotation.*;\n\n");

        sb.append("@Controller\n");
        sb.append("@RequestMapping(\"/").append(controllerPath).append("\")\n");
        sb.append("public class ").append(processedClass.controllerName()).append(" {\n\n");
        sb.append("    private final ").append(processedClass.serviceName()).append(" service;\n");
        if (!relationshipTargets.isEmpty()) {
            for (ProcessedClass target : relationshipTargets) {
                sb.append("    private final ").append(target.serviceName()).append(" ")
                    .append(getServiceFieldName(target)).append(";\n");
            }
        }
        sb.append("\n");
        sb.append("    public ").append(processedClass.controllerName()).append("(")
            .append(processedClass.serviceName()).append(" service");
        if (!relationshipTargets.isEmpty()) {
            for (ProcessedClass target : relationshipTargets) {
                sb.append(", ").append(target.serviceName()).append(" ")
                    .append(getServiceFieldName(target));
            }
        }
        sb.append(") {\n");
        sb.append("        this.service = service;\n");
        if (!relationshipTargets.isEmpty()) {
            for (ProcessedClass target : relationshipTargets) {
                String dependencyField = getServiceFieldName(target);
                sb.append("        this.").append(dependencyField).append(" = ").append(dependencyField).append(";\n");
            }
        }
        sb.append("    }\n\n");

        sb.append("    @GetMapping\n");
        sb.append("    public String list(Model model) {\n");
        sb.append("        model.addAttribute(\"items\", service.findAll());\n");
        sb.append("        return \"").append(controllerPath).append("/list\";\n");
        sb.append("    }\n\n");

        sb.append("    @GetMapping(\"/create\")\n");
        sb.append("    public String showCreateForm(Model model) {\n");
        sb.append("        model.addAttribute(\"item\", new ").append(processedClass.dtoName()).append("());\n");
        if (!relationshipFields.isEmpty()) {
            sb.append("        populateRelationships(model);\n");
        }
        sb.append("        return \"").append(controllerPath).append("/form\";\n");
        sb.append("    }\n\n");

        sb.append("    @GetMapping(\"/{id}/edit\")\n");
        sb.append("    public String showEditForm(@PathVariable ").append(processedClass.identifier().type())
            .append(" id, Model model) {\n");
        sb.append("        model.addAttribute(\"item\", service.findById(id));\n");
        if (!relationshipFields.isEmpty()) {
            sb.append("        populateRelationships(model);\n");
        }
        sb.append("        return \"").append(controllerPath).append("/form\";\n");
        sb.append("    }\n\n");

        sb.append("    @PostMapping(\"/save\")\n");
        sb.append("    public String save(@ModelAttribute(\"item\") ").append(processedClass.dtoName()).append(" dto) {\n");
        sb.append("        if (dto.get").append(StringUtils.capitalize(processedClass.identifier().name()))
            .append("() == null) {\n");
        sb.append("            service.create(dto);\n");
        sb.append("        } else {\n");
        sb.append("            service.update(dto.get").append(StringUtils.capitalize(processedClass.identifier().name()))
            .append("(), dto);\n");
        sb.append("        }\n");
        sb.append("        return \"redirect:/").append(controllerPath).append("\";\n");
        sb.append("    }\n\n");

        sb.append("    @PostMapping(\"/{id}/delete\")\n");
        sb.append("    public String delete(@PathVariable ").append(processedClass.identifier().type()).append(" id) {\n");
        sb.append("        service.delete(id);\n");
        sb.append("        return \"redirect:/").append(controllerPath).append("\";\n");
        sb.append("    }\n");
        if (!relationshipFields.isEmpty()) {
            sb.append("\n");
            sb.append("    private void populateRelationships(Model model) {\n");
            for (ProcessedField field : relationshipFields) {
                ProcessedClass target = processedClasses.get(field.targetEntity());
                if (target == null) {
                    continue;
                }
                sb.append("        model.addAttribute(\"").append(getRelationshipOptionsAttributeName(field)).append("\", ")
                    .append(getServiceFieldName(target)).append(".findAll());\n");
            }
            sb.append("    }\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String buildListTemplate(ProcessedClass processedClass, String controllerPath) {
        String idField = processedClass.identifier().name();
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html xmlns:th=\"http://www.thymeleaf.org\">\n");
        sb.append("<head>\n");
        sb.append("    <meta charset=\"UTF-8\">\n");
        sb.append("    <title>").append(processedClass.entityName()).append(" - Lista</title>\n");
        sb.append("    <style>\n");
        sb.append("        body { font-family: Arial, sans-serif; margin: 30px; }\n");
        sb.append("        table { width: 100%; border-collapse: collapse; margin-top: 20px; }\n");
        sb.append("        th, td { border: 1px solid #ccc; padding: 10px; text-align: left; }\n");
        sb.append("        th { background: #f0f0f0; }\n");
        sb.append("        a.button { display: inline-block; padding: 8px 16px; background: #2c3e50; color: #fff; text-decoration: none; border-radius: 4px; }\n");
        sb.append("        .actions { display: flex; gap: 8px; }\n");
        sb.append("        .actions form { display: inline; }\n");
        sb.append("    </style>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append("    <h1>").append(processedClass.entityName()).append("</h1>\n");
        sb.append("    <a class=\"button\" th:href=\"@{'/").append(controllerPath).append("/create'}\">Novo registro</a>\n");
        sb.append("    <table>\n");
        sb.append("        <thead>\n");
        sb.append("            <tr>\n");
        for (ProcessedField field : processedClass.fields()) {
            sb.append("                <th>").append(StringUtils.capitalize(field.name())).append("</th>\n");
        }
        sb.append("                <th>Ações</th>\n");
        sb.append("            </tr>\n");
        sb.append("        </thead>\n");
        sb.append("        <tbody>\n");
        sb.append("            <tr th:each=\"item : ${items}\">\n");
        for (ProcessedField field : processedClass.fields()) {
            sb.append("                <td th:text=\"${item.")
                .append(getDtoFieldName(field)).append("}\"></td>\n");
        }
        sb.append("                <td class=\"actions\">\n");
        sb.append("                    <a class=\"button\" th:href=\"@{'/").append(controllerPath).append("/' + ${item.")
            .append(idField).append("} + '/edit'}\">Editar</a>\n");
        sb.append("                    <form th:action=\"@{'/").append(controllerPath).append("/' + ${item.")
            .append(idField).append("} + '/delete'}\" method=\"post\" style=\"display:inline;\">\n");
        sb.append("                        <button type=\"submit\">Excluir</button>\n");
        sb.append("                    </form>\n");
        sb.append("                </td>\n");
        sb.append("            </tr>\n");
        sb.append("        </tbody>\n");
        sb.append("    </table>\n");
        sb.append("</body>\n");
        sb.append("</html>\n");
        return sb.toString();
    }

    private String buildFormTemplate(ProcessedClass processedClass, String controllerPath,
                                     Map<String, ProcessedClass> processedClasses) {
        String idField = processedClass.identifier().name();
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html xmlns:th=\"http://www.thymeleaf.org\">\n");
        sb.append("<head>\n");
        sb.append("    <meta charset=\"UTF-8\">\n");
        sb.append("    <title>").append(processedClass.entityName()).append(" - Formulário</title>\n");
        sb.append("    <style>\n");
        sb.append("        body { font-family: Arial, sans-serif; margin: 30px; }\n");
        sb.append("        form { max-width: 600px; }\n");
        sb.append("        label { display: block; margin-top: 20px; font-weight: bold; }\n");
        sb.append("        input { width: 100%; padding: 10px; margin-top: 8px; box-sizing: border-box; }\n");
        sb.append("        .buttons { margin-top: 20px; display: flex; gap: 10px; }\n");
        sb.append("        button, a.button { padding: 10px 16px; border: none; border-radius: 4px; cursor: pointer; }\n");
        sb.append("        button { background: #27ae60; color: #fff; }\n");
        sb.append("        a.button { background: #bdc3c7; color: #2c3e50; text-decoration: none; }\n");
        sb.append("    </style>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append("    <h1>Gerenciar ").append(processedClass.entityName()).append("</h1>\n");
        sb.append("    <form th:action=\"@{'/").append(controllerPath).append("/save'}\" method=\"post\" th:object=\"${item}\">\n");
        sb.append("        <input type=\"hidden\" th:field=\"*{").append(idField).append("}\">\n");
        for (ProcessedField field : processedClass.fields()) {
            if (field.identifier()) {
                continue;
            }
            sb.append("        <label>").append(StringUtils.capitalize(field.name())).append("</label>\n");
            if (isRenderableRelationshipField(field, processedClasses)) {
                ProcessedClass target = processedClasses.get(field.targetEntity());
                if (target == null) {
                    sb.append("        <input type=\"text\" th:field=\"*{").append(getDtoFieldName(field)).append("}\" />\n");
                } else {
                    String selectName = getDtoFieldName(field);
                    String optionAttribute = getRelationshipOptionsAttributeName(field);
                    String optionVar = getRelationshipOptionVariableName(field);
                    String optionValue = optionVar + "." + target.identifier().name();
                    String labelField = resolveTargetDisplayFieldName(target);
                    sb.append("        <select");
                    if (field.isCollection()) {
                        sb.append(" multiple size=\"5\"");
                    }
                    sb.append(" th:field=\"*{").append(selectName).append("}\">\n");
                    if (!field.isCollection()) {
                        sb.append("            <option value=\"\">Selecione...</option>\n");
                    }
                    sb.append("            <option th:each=\"").append(optionVar).append(" : ${")
                        .append(optionAttribute).append("}\" th:value=\"${").append(optionValue)
                        .append("}\" th:text=\"${").append(optionVar).append(".").append(labelField)
                        .append("}\"></option>\n");
                    sb.append("        </select>\n");
                }
            } else {
                sb.append("        <input type=\"text\" th:field=\"*{").append(getDtoFieldName(field)).append("}\" />\n");
            }
        }
        sb.append("        <div class=\"buttons\">\n");
        sb.append("            <button type=\"submit\">Salvar</button>\n");
        sb.append("            <a class=\"button\" th:href=\"@{'/").append(controllerPath).append("'}\">Cancelar</a>\n");
        sb.append("        </div>\n");
        sb.append("    </form>\n");
        sb.append("</body>\n");
        sb.append("</html>\n");
        return sb.toString();
    }

    private String buildServiceTest(String basePackage, ProcessedClass processedClass) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePackage).append(".service;\n\n");
        sb.append("import ").append(basePackage).append(".dto.").append(processedClass.dtoName()).append(";\n");
        sb.append("import ").append(basePackage).append(".entity.").append(processedClass.entityName()).append(";\n");
        sb.append("import ").append(basePackage).append(".repository.").append(processedClass.repositoryName()).append(";\n");
        sb.append("import org.junit.jupiter.api.BeforeEach;\n");
        sb.append("import org.junit.jupiter.api.Test;\n");
        sb.append("import org.junit.jupiter.api.extension.ExtendWith;\n");
        sb.append("import org.mockito.Mock;\n");
        sb.append("import org.mockito.junit.jupiter.MockitoExtension;\n");
        sb.append("import java.util.List;\n");
        sb.append("import static org.assertj.core.api.Assertions.assertThat;\n");
        sb.append("import static org.mockito.Mockito.*;\n\n");
        sb.append("@ExtendWith(MockitoExtension.class)\n");
        sb.append("class ").append(processedClass.serviceName()).append("Test {\n\n");
        sb.append("    @Mock\n");
        sb.append("    private ").append(processedClass.repositoryName()).append(" repository;\n\n");
        sb.append("    private ").append(processedClass.serviceName()).append(" service;\n\n");
        sb.append("    @BeforeEach\n");
        sb.append("    void setUp() {\n");
        sb.append("        service = new ").append(processedClass.serviceName()).append("(repository);\n");
        sb.append("    }\n\n");
        sb.append("    @Test\n");
        sb.append("    void findAllShouldReturnDtoList() {\n");
        sb.append("        ").append(processedClass.entityName()).append(" entity = new ")
            .append(processedClass.entityName()).append("();\n");
        sb.append("        when(repository.findAll()).thenReturn(Collections.singletonList(entity));\n\n");
        sb.append("        List<").append(processedClass.dtoName()).append("> result = service.findAll();\n\n");
        sb.append("        assertThat(result).hasSize(1);\n");
        sb.append("        verify(repository).findAll();\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    private String buildControllerTest(String basePackage, ProcessedClass processedClass,
                                       Map<String, ProcessedClass> processedClasses,
                                       boolean thymeleafViews) {
        String controllerPackage = basePackage + ".controller";
        String controllerPath = buildControllerPath(processedClass.entityName());
        String requestPath = thymeleafViews ? "/" + controllerPath : "/api/" + controllerPath;
        List<ProcessedClass> relationshipDependencies = resolveRelationshipDependencies(processedClass, processedClasses);
        SampleValue sampleValue = resolveSampleValue(processedClass.identifier().type());

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(controllerPackage).append(";\n\n");
        sb.append("import ").append(basePackage).append(".dto.").append(processedClass.dtoName()).append(";\n");
        sb.append("import ").append(basePackage).append(".service.").append(processedClass.serviceName()).append(";\n");
        for (ProcessedClass dependency : relationshipDependencies) {
            sb.append("import ").append(basePackage).append(".service.")
                .append(dependency.serviceName()).append(";\n");
        }
        if (sampleValue.importName() != null) {
            sb.append("import ").append(sampleValue.importName()).append(";\n");
        }
        sb.append("import org.junit.jupiter.api.Test;\n");
        sb.append("import org.springframework.beans.factory.annotation.Autowired;\n");
        sb.append("import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;\n");
        sb.append("import org.springframework.boot.test.mock.mockito.MockBean;\n");
        sb.append("import org.springframework.test.web.servlet.MockMvc;\n");
        sb.append("import java.util.Collections;\n");
        sb.append("import java.util.List;\n");
        sb.append("\n");
        sb.append("import static org.mockito.Mockito.when;\n");
        sb.append("import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;\n");
        sb.append("import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;\n");
        if (!thymeleafViews) {
            sb.append("import static org.hamcrest.Matchers.notNullValue;\n");
        }
        sb.append("\n");
        sb.append("@WebMvcTest(").append(processedClass.controllerName()).append(".class)\n");
        sb.append("class ").append(processedClass.controllerName()).append("Test {\n\n");
        sb.append("    @Autowired\n");
        sb.append("    private MockMvc mockMvc;\n\n");
        sb.append("    @MockBean\n");
        sb.append("    private ").append(processedClass.serviceName()).append(" service;\n");
        if (!relationshipDependencies.isEmpty()) {
            sb.append("\n");
            for (ProcessedClass dependency : relationshipDependencies) {
                sb.append("    @MockBean\n");
                sb.append("    private ").append(dependency.serviceName()).append(" ")
                    .append(getServiceFieldName(dependency)).append(";\n");
            }
        }
        sb.append("\n");
        sb.append("    @Test\n");
        sb.append("    void shouldListItems() throws Exception {\n");
        sb.append("        ").append(processedClass.dtoName()).append(" dto = new ")
            .append(processedClass.dtoName()).append("();\n");
        sb.append("        dto.").append(getDtoSetterName(processedClass.identifier()))
            .append("(").append(sampleValue.expression()).append(");\n");
        sb.append("        when(service.findAll()).thenReturn(Collections.singletonList(dto));\n\n");
        sb.append("        mockMvc.perform(get(\"").append(requestPath).append("\"))\n");
        sb.append("            .andExpect(status().isOk())\n");
        if (thymeleafViews) {
            sb.append("            .andExpect(view().name(\"").append(controllerPath).append("/list\"))\n");
            sb.append("            .andExpect(model().attributeExists(\"items\"));\n");
        } else {
            sb.append("            .andExpect(jsonPath(\"$[0].").append(processedClass.identifier().name())
                .append("\").value(notNullValue()));\n");
        }
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

    private Set<String> resolveFieldImports(List<ProcessedField> fields, String currentPackage,
                                            Map<String, String> customTypePackages) {
        Set<String> imports = new TreeSet<>();
        if (fields == null) {
            return imports;
        }
        for (ProcessedField field : fields) {
            addCustomImport(field.type(), currentPackage, customTypePackages, imports);
            if (field.objectField()) {
                addCustomImport(field.targetEntity(), currentPackage, customTypePackages, imports);
                if (field.isCollection()) {
                    imports.add("java.util.List");
                }
            }
        }
        return imports;
    }

    private String buildReadme(CrudGenerationRequest request, String basePackage) {
        StringBuilder sb = new StringBuilder();
        sb.append("# CRUD Module\n\n");
        sb.append("Base package: `").append(basePackage).append("`\n\n");
        sb.append("Classes generated:\n");
        for (CrudClassDefinition definition : request.getClasses()) {
            sb.append("- ").append(definition.getName()).append("\n");
        }
        sb.append("\nGenerated layers: Entity, DTO, Repository, Service, Controller.\n");
        if (request.isThymeleafViews()) {
            sb.append("Includes Thymeleaf MVC controllers, list and form templates.\n");
        } else {
            sb.append("Exposes RESTful controllers with JSON endpoints.\n");
        }
        return sb.toString();
    }

    private Set<String> resolveMethodImports(List<ProcessedMethod> methods, String currentPackage,
                                             Map<String, String> customTypePackages) {
        Set<String> imports = new TreeSet<>();
        if (methods == null) {
            return imports;
        }
        for (ProcessedMethod method : methods) {
            addCustomImport(method.returnType(), currentPackage, customTypePackages, imports);
            for (ProcessedParameter parameter : method.parameters()) {
                addCustomImport(parameter.type(), currentPackage, customTypePackages, imports);
            }
        }
        return imports;
    }

    private SampleValue resolveSampleValue(String typeName) {
        return switch (typeName) {
            case "Long" -> new SampleValue("1L", null);
            case "Integer" -> new SampleValue("1", null);
            case "Double" -> new SampleValue("1.0d", null);
            case "BigDecimal" -> new SampleValue("BigDecimal.ONE", "java.math.BigDecimal");
            case "UUID" -> new SampleValue("UUID.randomUUID()", "java.util.UUID");
            case "LocalDate" -> new SampleValue("LocalDate.now()", "java.time.LocalDate");
            case "LocalDateTime" -> new SampleValue("LocalDateTime.now()", "java.time.LocalDateTime");
            case "Boolean" -> new SampleValue("Boolean.TRUE", null);
            case "String" -> new SampleValue("\"sample-value\"", null);
            default -> new SampleValue("1L", null);
        };
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

    private String resolveEntityFieldType(ProcessedField field) {
        if (field.objectField()) {
            return field.isCollection() ? "List<" + field.targetEntity() + ">" : field.targetEntity();
        }
        return field.type();
    }

    private List<GeneratedField> buildDtoFields(ProcessedClass processedClass,
                                                Map<String, ProcessedClass> processedClasses) {
        List<GeneratedField> fields = new ArrayList<>();
        for (ProcessedField field : processedClass.fields()) {
            if (field.objectField()) {
                fields.add(new GeneratedField(getDtoFieldName(field), getDtoFieldType(field, processedClasses)));
            } else {
                fields.add(new GeneratedField(field.name(), field.type()));
            }
        }
        return fields;
    }

    private String getDtoFieldName(ProcessedField field) {
        if (!field.objectField()) {
            return field.name();
        }
        return field.isCollection() ? field.name() + "Ids" : field.name() + "Id";
    }

    private String getDtoFieldType(ProcessedField field, Map<String, ProcessedClass> processedClasses) {
        if (!field.objectField()) {
            return field.type();
        }
        String identifierType = resolveTargetIdType(field, processedClasses);
        if (field.isCollection()) {
            return "List<" + identifierType + ">";
        }
        return identifierType;
    }

    private String resolveTargetIdType(ProcessedField field, Map<String, ProcessedClass> processedClasses) {
        ProcessedClass target = processedClasses.get(field.targetEntity());
        if (target == null) {
            return "Long";
        }
        return target.identifier().type();
    }

    private String resolveTargetIdFieldName(ProcessedField field, Map<String, ProcessedClass> processedClasses) {
        ProcessedClass target = processedClasses.get(field.targetEntity());
        if (target == null) {
            return "id";
        }
        return target.identifier().name();
    }

    private boolean isRenderableRelationshipField(ProcessedField field,
                                                  Map<String, ProcessedClass> processedClasses) {
        return field.objectField() && processedClasses.containsKey(field.targetEntity());
    }

    private List<ProcessedClass> resolveRelationshipDependencies(ProcessedClass processedClass,
                                                                 Map<String, ProcessedClass> processedClasses) {
        List<ProcessedClass> dependencies = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ProcessedField field : processedClass.fields()) {
            if (!isRenderableRelationshipField(field, processedClasses)) {
                continue;
            }
            ProcessedClass target = processedClasses.get(field.targetEntity());
            if (target != null && seen.add(target.entityName())) {
                dependencies.add(target);
            }
        }
        return dependencies;
    }

    private String getRelationshipOptionsAttributeName(ProcessedField field) {
        return field.name() + "Options";
    }

    private String getRelationshipOptionVariableName(ProcessedField field) {
        return field.name() + "Option";
    }

    private String getServiceFieldName(ProcessedClass processedClass) {
        return StringUtils.uncapitalize(processedClass.serviceName());
    }

    private String resolveTargetDisplayFieldName(ProcessedClass target) {
        return target.fields().stream()
            .filter(f -> !f.identifier() && !f.objectField())
            .map(ProcessedField::name)
            .findFirst()
            .orElse(target.identifier().name());
    }

    private String getDtoSetterName(ProcessedField field) {
        return "set" + StringUtils.capitalize(getDtoFieldName(field));
    }

    private String getDtoGetterName(ProcessedField field) {
        return "get" + StringUtils.capitalize(getDtoFieldName(field));
    }

    private String buildRelationshipAnnotations(ProcessedField field, ProcessedClass owner,
                                                Map<String, ProcessedClass> processedClasses) {
        if (!field.objectField() || field.relationshipType() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String columnName = toSnakeCase(field.name()) + "_id";
        switch (field.relationshipType()) {
            case ONE_TO_ONE -> {
                sb.append("    @OneToOne\n");
                sb.append("    @JoinColumn(name = \"").append(columnName).append("\")\n");
            }
            case MANY_TO_ONE -> {
                sb.append("    @ManyToOne\n");
                sb.append("    @JoinColumn(name = \"").append(columnName).append("\")\n");
            }
            case ONE_TO_MANY -> {
                sb.append("    @OneToMany\n");
                sb.append("    @JoinColumn(name = \"").append(toSnakeCase(owner.entityName())).append("_id\")\n");
            }
            case MANY_TO_MANY -> {
                sb.append("    @ManyToMany\n");
                String joinTable = toSnakeCase(owner.entityName()) + "_" + toSnakeCase(field.targetEntity());
                sb.append("    @JoinTable(name = \"").append(joinTable).append("\",\n");
                sb.append("        joinColumns = @JoinColumn(name = \"").append(toSnakeCase(owner.entityName()))
                    .append("_id\"),\n");
                sb.append("        inverseJoinColumns = @JoinColumn(name = \"")
                    .append(toSnakeCase(field.targetEntity())).append("_id\"))\n");
            }
        }
        return sb.toString();
    }

    private void appendConstructors(StringBuilder sb, String className, List<GeneratedField> fields) {
        sb.append("    public ").append(className).append("() {\n");
        sb.append("    }\n\n");
        sb.append("    public ").append(className).append("(");
        for (int i = 0; i < fields.size(); i++) {
            GeneratedField field = fields.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(field.type()).append(" ").append(field.name());
        }
        sb.append(") {\n");
        for (GeneratedField field : fields) {
            sb.append("        this.").append(field.name()).append(" = ").append(field.name()).append(";\n");
        }
        sb.append("    }\n\n");
    }

    private void appendGettersAndSetters(StringBuilder sb, List<GeneratedField> fields) {
        for (GeneratedField field : fields) {
            String capitalized = StringUtils.capitalize(field.name());
            sb.append("    public ").append(field.type()).append(" get").append(capitalized).append("() {\n");
            sb.append("        return ").append(field.name()).append(";\n");
            sb.append("    }\n\n");
            sb.append("    public void set").append(capitalized).append("(").append(field.type())
                .append(" ").append(field.name()).append(") {\n");
            sb.append("        this.").append(field.name()).append(" = ").append(field.name()).append(";\n");
            sb.append("    }\n\n");
        }
    }

    private void appendClassMethods(StringBuilder sb, List<ProcessedMethod> methods, boolean allowAbstract) {
        if (methods == null || methods.isEmpty()) {
            return;
        }
        for (ProcessedMethod method : methods) {
            sb.append("    public ");
            if (allowAbstract && method.abstractMethod()) {
                sb.append("abstract ");
            }
            sb.append(method.returnType()).append(" ").append(method.name())
                .append("(").append(formatParameters(method.parameters())).append(")");
            if (allowAbstract && method.abstractMethod()) {
                sb.append(";\n\n");
                continue;
            }
            sb.append(" {\n");
            String body = method.body();
            if (StringUtils.hasText(body)) {
                sb.append(indentBody(body));
            } else if (!isVoidReturn(method.returnType())) {
                sb.append("        return ").append(defaultReturnValueLiteral(method.returnType())).append(";\n");
            } else {
                sb.append("        // TODO Auto-generated method stub\n");
            }
            sb.append("    }\n\n");
        }
    }

    private void appendInterfaceMethods(StringBuilder sb, List<ProcessedMethod> methods) {
        if (methods == null || methods.isEmpty()) {
            return;
        }
        for (ProcessedMethod method : methods) {
            sb.append("    ");
            if (method.defaultImplementation()) {
                sb.append("default ");
            }
            sb.append(method.returnType()).append(" ").append(method.name())
                .append("(").append(formatParameters(method.parameters())).append(")");
            if (method.defaultImplementation()) {
                sb.append(" {\n");
                String body = method.body();
                if (StringUtils.hasText(body)) {
                    sb.append(indentBody(body));
                } else if (!isVoidReturn(method.returnType())) {
                    sb.append("        return ").append(defaultReturnValueLiteral(method.returnType())).append(";\n");
                } else {
                    sb.append("        // TODO Auto-generated method stub\n");
                }
                sb.append("    }\n\n");
            } else {
                sb.append(";\n\n");
            }
        }
    }

    private String formatParameters(List<ProcessedParameter> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "";
        }
        return parameters.stream()
            .map(parameter -> parameter.type() + " " + parameter.name())
            .collect(Collectors.joining(", "));
    }

    private boolean isVoidReturn(String type) {
        return "void".equals(type) || "Void".equals(type);
    }

    private String defaultReturnValueLiteral(String type) {
        String normalized = type.trim();
        return switch (normalized) {
            case "int", "Integer", "short", "Short", "byte", "Byte", "long", "Long" -> "0";
            case "double", "Double" -> "0.0d";
            case "float", "Float" -> "0.0f";
            case "boolean", "Boolean" -> "false";
            default -> "null";
        };
    }

    private String resolveDefaultValueLiteral(String type) {
        return defaultReturnValueLiteral(type);
    }

    private String indentBody(String body) {
        String normalized = body.replace("\r\n", "\n");
        StringBuilder result = new StringBuilder();
        for (String line : normalized.split("\n", -1)) {
            if (line.isEmpty()) {
                result.append("\n");
            } else {
                result.append("        ").append(line).append("\n");
            }
        }
        return result.toString();
    }

    private List<String> collectTypeTokens(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            return Collections.emptyList();
        }
        String sanitized = rawType.replaceAll("[<>\\[\\]()]", " ");
        List<String> tokens = new ArrayList<>();
        for (String candidate : sanitized.split("[,\\s]+")) {
            if (StringUtils.hasText(candidate)) {
                tokens.add(candidate);
            }
        }
        return tokens;
    }

    private void addCustomImport(String rawType, String currentPackage,
                                 Map<String, String> customTypePackages, Set<String> imports) {
        if (!StringUtils.hasText(rawType)) {
            return;
        }
        for (String token : collectTypeTokens(rawType)) {
            String standardImport = FIELD_IMPORTS.get(token);
            if (standardImport != null) {
                imports.add(standardImport);
            }
            if (customTypePackages != null) {
                String targetPackage = customTypePackages.get(token);
                if (targetPackage != null && !targetPackage.equals(currentPackage)) {
                    imports.add(targetPackage + "." + token);
                }
            }
        }
        if (requiresListImport(rawType)) {
            imports.add("java.util.List");
        }
        if (rawType.contains("Set<") || "Set".equals(rawType)) {
            imports.add("java.util.Set");
        }
    }

    private boolean requiresListImport(String type) {
        return StringUtils.hasText(type)
            && (type.contains("List<") || "List".equals(type));
    }

    private List<ProcessedMethod> processMethods(List<CrudMethodDefinition> methods,
                                                 CrudStructureType type) {
        if (methods == null) {
            return Collections.emptyList();
        }
        List<ProcessedMethod> processed = new ArrayList<>();
        for (CrudMethodDefinition method : methods) {
            if (method == null) {
                continue;
            }
            String name = toCamelCase(method.getName());
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String returnType = Optional.ofNullable(method.getReturnType()).map(String::trim)
                .filter(StringUtils::hasText).orElse("void");
            List<ProcessedParameter> parameters = new ArrayList<>();
            if (method.getParameters() != null) {
                for (CrudMethodParameterDefinition parameter : method.getParameters()) {
                    String paramName = toCamelCase(parameter.getName());
                    String paramType = Optional.ofNullable(parameter.getType()).map(String::trim)
                        .filter(StringUtils::hasText).orElse("");
                    if (!StringUtils.hasText(paramName) || !StringUtils.hasText(paramType)) {
                        continue;
                    }
                    parameters.add(new ProcessedParameter(paramName, paramType));
                }
            }
            boolean defaultImplementation = type == CrudStructureType.INTERFACE
                && method.isDefaultImplementation();
            boolean abstractMethod = type != CrudStructureType.INTERFACE && method.isAbstractMethod();
            String body = Optional.ofNullable(method.getBody()).orElse("").trim();
            processed.add(new ProcessedMethod(name, returnType, parameters, abstractMethod,
                defaultImplementation, body));
        }
        return processed;
    }

    private String toConstantCase(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("[^a-zA-Z0-9]", "_")
            .replaceAll("_+", "_")
            .toUpperCase(Locale.ROOT);
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
        boolean unique,
        boolean objectField,
        CrudRelationshipType relationshipType,
        String targetEntity
    ) {
        boolean isCollection() {
            return objectField && (relationshipType == CrudRelationshipType.ONE_TO_MANY
                || relationshipType == CrudRelationshipType.MANY_TO_MANY);
        }
    }

    private record GeneratedField(String name, String type) { }

    private record SampleValue(String expression, String importName) { }

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

    private record ProcessedParameter(String name, String type) { }

    private record ProcessedMethod(
        String name,
        String returnType,
        List<ProcessedParameter> parameters,
        boolean abstractMethod,
        boolean defaultImplementation,
        String body
    ) { }

    private record SupplementalStructure(
        String name,
        CrudStructureType type,
        List<ProcessedField> fields,
        List<ProcessedMethod> methods,
        List<String> enumConstants
    ) { }
}
