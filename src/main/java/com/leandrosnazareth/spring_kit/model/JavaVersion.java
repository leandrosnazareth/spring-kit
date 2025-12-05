package com.leandrosnazareth.spring_kit.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum JavaVersion {
    JAVA_8("8", "Java 8 (LTS)", new String[]{"2.0.9.RELEASE", "2.1.18.RELEASE", "2.2.13.RELEASE", "2.3.12.RELEASE"}),
    JAVA_11("11", "Java 11 (LTS)", new String[]{"2.3.12.RELEASE", "2.4.13", "2.5.15", "2.6.15", "2.7.18"}),
    JAVA_17("17", "Java 17 (LTS)", new String[]{"2.7.18", "3.0.13", "3.1.12", "3.2.5", "3.3.0"}),
    JAVA_21("21", "Java 21 (LTS)", new String[]{"3.2.5", "3.3.0"}),
    JAVA_25("25", "Java 25 (LTS - Future)", new String[]{"3.4.0"});

    private final String version;
    private final String label;
    private final String[] compatibleSpringVersions;
}
