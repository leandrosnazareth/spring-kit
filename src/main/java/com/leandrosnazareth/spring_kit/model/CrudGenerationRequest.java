package com.leandrosnazareth.spring_kit.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CrudGenerationRequest {

    @NotBlank
    private String moduleName = "crud-module";

    @NotBlank
    private String basePackage = "com.example.demo";

    @Valid
    @NotEmpty
    private List<CrudClassDefinition> classes = new ArrayList<>();
}
