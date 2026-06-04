package com.zqzqq.proxyhub.management.api;

import com.zqzqq.proxyhub.core.security.AuthService;
import com.zqzqq.proxyhub.core.security.BCryptService;
import com.zqzqq.proxyhub.core.security.UserStore;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserManagementController {

    private final AuthService authService;
    private final BCryptService bcryptService;

    public UserManagementController(AuthService authService, BCryptService bcryptService) {
        this.authService = authService;
        this.bcryptService = bcryptService;
    }

    @GetMapping
    public ResponseEntity<List<UserItemResponse>> listUsers() {
        List<UserStore.UserRecord> users = authService.listUsers();
        List<UserItemResponse> result = users.stream()
                .map(u -> new UserItemResponse(
                        u.id(),
                        u.username(),
                        u.enabled(),
                        u.createdAt()
                ))
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<UserItemResponse> upsertUser(@RequestBody CreateUserRequest request) {
        if (request.username() == null || request.username().isBlank()) {
            return ResponseEntity.badRequest().body(null);
        }
        if (request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().body(null);
        }
        authService.upsertUser(request.username(), request.password(), request.enabled());
        // Re-read to return current state
        var record = authService.listUsers().stream()
                .filter(u -> u.username().equals(request.username()))
                .findFirst()
                .orElseThrow();
        return ResponseEntity.ok(new UserItemResponse(
                record.id(),
                record.username(),
                record.enabled(),
                record.createdAt()
        ));
    }

    @PutMapping("/{username}/toggle")
    public ResponseEntity<Void> toggleUser(@PathVariable String username) {
        var users = authService.listUsers();
        var user = users.stream().filter(u -> u.username().equals(username)).findFirst();
        if (user.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        authService.upsertUser(username, null, !user.get().enabled());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{username}")
    public ResponseEntity<Void> deleteUser(@PathVariable String username) {
        authService.deleteUser(username);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/generate-default")
    public ResponseEntity<DefaultCredentialsResponse> regenerateDefault() {
        // This is a destructive operation — only for initial setup recovery
        // We'll just return a message since the default is generated at startup
        return ResponseEntity.badRequest().body(
                new DefaultCredentialsResponse(
                        "Default credentials are generated only on first startup. " +
                                "To reset, delete the database file at " +
                                "proxy.users.store-path and restart."));
    }

    public record CreateUserRequest(String username, String password, Boolean enabled) {
        public CreateUserRequest {
            if (enabled == null) enabled = true;
        }
    }

    public record UserItemResponse(long id, String username, boolean enabled, String createdAt) {
    }

    public record DefaultCredentialsResponse(String message) {
    }
}
