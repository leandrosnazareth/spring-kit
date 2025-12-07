package com.leandrosnazareth.spring_kit.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CrudMethodDefinition {

    @NotBlank
    private String name;

    @NotBlank
    private String returnType = "void";

    @Valid
    private List<CrudMethodParameterDefinition> parameters = new ArrayList<>();

    private boolean abstractMethod;

    private boolean defaultImplementation;

    private String body;
}
