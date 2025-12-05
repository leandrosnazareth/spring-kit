package com.leandrosnazareth.spring_kit.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CrudClassDefinition {

    @NotBlank
    private String name;

    private String tableName;

    @Valid
    @NotEmpty
    private List<CrudFieldDefinition> fields = new ArrayList<>();
}
