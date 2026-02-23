package org.example.user.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.shared.dto.ErrorResponse;
import org.example.shared.dto.UserResponse;
import org.example.user.dto.AssignRoleRequest;
import org.example.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * REST контроллер для управления пользователями
 */
@Slf4j
@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * GET /users/{email}
     * Получить профиль пользователя
     */
    @GetMapping("/{email}")
    public ResponseEntity<?> getProfile(@PathVariable String email) {
        try {
            log.info("Getting profile for: {}", email);
            UserResponse response = userService.getUserProfile(email);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Error getting profile", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ErrorResponse.builder()
                            .error("user_not_found")
                            .error_description(e.getMessage())
                            .status(404)
                            .timestamp(LocalDateTime.now())
                            .build()
            );
        }
    }

    /**
     * POST /users
     * Создать нового пользователя (вызывается из auth-service)
     */
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody UserResponse userRequest) {
        try {
            log.info("Creating new user: {}", userRequest.getEmail());
            UserResponse response = userService.createUser(userRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            log.error("Error creating user", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    ErrorResponse.builder()
                            .error("bad_request")
                            .error_description(e.getMessage())
                            .status(400)
                            .timestamp(LocalDateTime.now())
                            .build()
            );
        }
    }

    /**
     * PUT /users/{email}/role
     * Назначить роль пользователю (только ADMIN)
     */
    @PutMapping("/{email}/role")
    public ResponseEntity<?> assignRole(@PathVariable String email,
                                        @RequestBody AssignRoleRequest request,
                                        Authentication authentication) {
        try {
            if (authentication == null || !hasAdminRole(authentication)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                        ErrorResponse.builder()
                                .error("permission_denied")
                                .error_description("Only ADMIN users can assign roles")
                                .status(403)
                                .timestamp(LocalDateTime.now())
                                .build()
                );
            }

            String adminEmail = authentication.getName();
            log.info("Admin {} is assigning role to {}", adminEmail, email);

            UserResponse response = userService.assignRole(email, request, adminEmail);
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("Error assigning role", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    ErrorResponse.builder()
                            .error("not_found")
                            .error_description(e.getMessage())
                            .status(404)
                            .timestamp(LocalDateTime.now())
                            .build()
            );
        }
    }

    /**
     * GET /users
     * Получить список всех пользователей (только ADMIN)
     */
    @GetMapping
    public ResponseEntity<?> listUsers(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       Authentication authentication) {
        try {
            if (authentication == null || !hasAdminRole(authentication)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                        ErrorResponse.builder()
                                .error("permission_denied")
                                .error_description("Only ADMIN users can list all users")
                                .status(403)
                                .timestamp(LocalDateTime.now())
                                .build()
                );
            }

            if (page < 0 || size < 1 || size > 100) {
                return ResponseEntity.badRequest().body(
                        ErrorResponse.builder()
                                .error("bad_request")
                                .error_description("Page must be >= 0 and size must be between 1 and 100")
                                .status(400)
                                .timestamp(LocalDateTime.now())
                                .build()
                );
            }

            log.info("Listing users: page={}, size={}", page, size);
            Pageable pageable = PageRequest.of(page, size);
            Page<UserResponse> response = userService.getAllUsers(pageable);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error listing users", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    ErrorResponse.builder()
                            .error("internal_error")
                            .error_description("Failed to list users")
                            .status(500)
                            .timestamp(LocalDateTime.now())
                            .build()
            );
        }
    }

    /**
     * Проверить есть ли роль ADMIN у пользователя
     */
    private boolean hasAdminRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN"));
    }
}

