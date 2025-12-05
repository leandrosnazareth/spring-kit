package com.leandrosnazareth.spring_kit.controller;

import com.leandrosnazareth.spring_kit.model.CrudGenerationRequest;
import com.leandrosnazareth.spring_kit.service.CrudScaffoldingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/crud")
public class CrudGeneratorController {

    private final CrudScaffoldingService crudScaffoldingService;

    public CrudGeneratorController(CrudScaffoldingService crudScaffoldingService) {
        this.crudScaffoldingService = crudScaffoldingService;
    }

    @PostMapping("/generate")
    public ResponseEntity<byte[]> generateCrudModule(@Valid @RequestBody CrudGenerationRequest request) {
        try {
            byte[] zip = crudScaffoldingService.generateCrudModule(request);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", request.getModuleName() + "-crud.zip");
            return ResponseEntity.ok()
                .headers(headers)
                .body(zip);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
