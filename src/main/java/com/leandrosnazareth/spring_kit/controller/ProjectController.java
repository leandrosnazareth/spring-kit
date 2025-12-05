package com.leandrosnazareth.spring_kit.controller;

import com.leandrosnazareth.spring_kit.model.JavaVersion;
import com.leandrosnazareth.spring_kit.model.ProjectRequest;
import com.leandrosnazareth.spring_kit.service.DependencyService;
import com.leandrosnazareth.spring_kit.service.ProjectGeneratorService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import java.io.IOException;

@Controller
public class ProjectController {

    private final DependencyService dependencyService;
    private final ProjectGeneratorService projectGeneratorService;

    public ProjectController(DependencyService dependencyService, 
                            ProjectGeneratorService projectGeneratorService) {
        this.dependencyService = dependencyService;
        this.projectGeneratorService = projectGeneratorService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("javaVersions", JavaVersion.values());
        model.addAttribute("dependencies", dependencyService.getAllDependencies());
        if (!model.containsAttribute("projectRequest")) {
            model.addAttribute("projectRequest", new ProjectRequest());
        }
        return "index";
    }

    @GetMapping("/api/spring-versions")
    @ResponseBody
    public String[] getSpringVersions(@RequestParam String javaVersion) {
        for (JavaVersion jv : JavaVersion.values()) {
            if (jv.getVersion().equals(javaVersion)) {
                return jv.getCompatibleSpringVersions();
            }
        }
        return new String[]{};
    }

    @PostMapping("/generate")
    public ResponseEntity<byte[]> generateProject(@Valid @ModelAttribute ProjectRequest request, 
                                                   BindingResult bindingResult,
                                                   RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().build();
        }
        
        // Additional custom validations
        if (!isValidJavaIdentifier(request.getName())) {
            return ResponseEntity.badRequest().build();
        }
        
        try {
            byte[] zipFile = projectGeneratorService.generateProject(request);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", request.getArtifactId() + ".zip");
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(zipFile);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    private boolean isValidJavaIdentifier(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
