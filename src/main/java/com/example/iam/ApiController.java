package com.example.iam;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {

    /** No token needed. */
    @GetMapping("/public")
    public Map<String, String> publicInfo() {
        return Map.of("message", "anyone can see this");
    }

    /** Any valid token. Shows what the API learned from the token. */
    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .toList();
        return Map.of(
                "subject", String.valueOf(jwt.getSubject()),
                "username", String.valueOf(jwt.getClaimAsString("preferred_username")),
                "authorities", authorities);
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAnyRole('reader','editor','admin')")
    public List<Map<String, String>> listReports() {
        return List.of(
                Map.of("id", "1", "title", "Q3 summary"),
                Map.of("id", "2", "title", "Audit review"));
    }

    @PostMapping("/reports")
    @PreAuthorize("hasAnyRole('editor','admin')")
    public ResponseEntity<Map<String, String>> createReport() {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "created"));
    }

    @DeleteMapping("/reports/{id}")
    @PreAuthorize("hasRole('admin')")
    public Map<String, String> deleteReport(@PathVariable String id) {
        return Map.of("status", "deleted", "id", id);
    }
}
