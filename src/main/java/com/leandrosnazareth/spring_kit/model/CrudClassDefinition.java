package com.leandrosnazareth.spring_kit.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CrudClassDefinition {

    @NotBlank
    private String name;

    private String tableName;

    @Valid
    @NotEmpty
    private List<CrudFieldDefinition> fields = new ArrayList<>();

    private CrudStructureType structureType = CrudStructureType.CLASS;

    @Valid
    private List<CrudMethodDefinition> methods = new ArrayList<>();

    private List<String> enumConstants = new ArrayList<>();
}
