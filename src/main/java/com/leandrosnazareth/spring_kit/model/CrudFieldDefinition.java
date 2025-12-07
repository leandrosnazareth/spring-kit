package com.leandrosnazareth.spring_kit.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CrudFieldDefinition {

    @NotBlank
    private String name;

    @NotBlank
    private String type;

    private boolean identifier;

    private boolean required;

    private boolean unique;

    private boolean objectType;

    private String targetClassName;

    private CrudRelationshipType relationshipType;
}
