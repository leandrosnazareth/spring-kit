package com.leandrosnazareth.spring_kit.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProjectRequest {
    private String projectType = "maven";
    private String language = "java";
    private String springBootVersion;
    private String javaVersion = "17";
    
    private String groupId = "com.example";
    private String artifactId = "demo";
    private String name = "demo";
    private String description = "Demo project for Spring Boot";
    private String packageName = "com.example.demo";
    private String packaging = "jar";
    
    private List<String> dependencies = new ArrayList<>();
}
