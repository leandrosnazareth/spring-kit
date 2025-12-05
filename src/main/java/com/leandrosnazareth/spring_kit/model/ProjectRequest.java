package com.leandrosnazareth.spring_kit.model;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProjectRequest {
    private String projectType = "maven";
    private String language = "java";
    private String springBootVersion;
    private String javaVersion = "17";
    
    @NotBlank(message = "Group ID is required")
    @Pattern(regexp = "^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*$", 
             message = "Group ID must follow Java package naming conventions (lowercase letters, numbers, underscores, dots)")
    private String groupId = "com.example";
    
    @NotBlank(message = "Artifact ID is required")
    @Pattern(regexp = "^[a-z][a-z0-9-]*$", 
             message = "Artifact ID must start with a lowercase letter and contain only lowercase letters, numbers, and hyphens")
    private String artifactId = "demo";
    
    @NotBlank(message = "Name is required")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9]*$", 
             message = "Name must start with a letter and contain only letters and numbers (no spaces or special characters)")
    private String name = "demo";
    
    private String description = "Demo project for Spring Boot";
    
    @NotBlank(message = "Package name is required")
    @Pattern(regexp = "^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*$", 
             message = "Package name must follow Java package naming conventions (lowercase letters, numbers, underscores, dots)")
    private String packageName = "com.example.demo";
    
    private String packaging = "jar";
    
    private List<String> dependencies = new ArrayList<>();
}
