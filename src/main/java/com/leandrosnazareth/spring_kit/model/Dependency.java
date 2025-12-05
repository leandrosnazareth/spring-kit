package com.leandrosnazareth.spring_kit.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Dependency {
    private String id;
    private String name;
    private String description;
    private String groupId;
    private String artifactId;
}
