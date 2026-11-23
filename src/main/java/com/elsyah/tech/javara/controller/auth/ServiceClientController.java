package com.elsyah.tech.javara.controller.auth;

import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.elsyah.tech.javara.request.auth.CreateServiceClientRequest;
import com.elsyah.tech.javara.response.common.ApiResponse;
import com.elsyah.tech.javara.response.auth.ProvisionedServiceClientResponse;
import com.elsyah.tech.javara.service.auth.ServiceClientService;

@RestController
@RequestMapping("/api/v1/service-clients")
@ConditionalOnProperty(
        prefix = "app.security.service-auth",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ServiceClientController {
    private final ServiceClientService service;

    public ServiceClientController(ServiceClientService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ProvisionedServiceClientResponse>> provision(
            @Valid @RequestBody CreateServiceClientRequest request) {
        ProvisionedServiceClientResponse client = service.provision(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, "Service client provisioned", client));
    }

    @PostMapping("/{clientId}/rotate")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ProvisionedServiceClientResponse> rotate(@PathVariable String clientId) {
        return ApiResponse.success(
                HttpStatus.OK,
                "Service client secret rotated; store the new secret now",
                service.rotate(clientId));
    }

    @DeleteMapping("/{clientId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> revoke(@PathVariable String clientId) {
        service.revoke(clientId);
        return ResponseEntity.noContent().build();
    }
}
