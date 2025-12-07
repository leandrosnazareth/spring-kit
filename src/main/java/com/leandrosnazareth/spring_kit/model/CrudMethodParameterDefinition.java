package com.leandrosnazareth.spring_kit.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CrudMethodParameterDefinition {

    @NotBlank
    private String name;

    @NotBlank
    private String type;
}
