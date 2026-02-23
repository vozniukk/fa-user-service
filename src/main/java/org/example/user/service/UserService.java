package org.example.user.service;

import lombok.extern.slf4j.Slf4j;
import org.example.shared.dto.UserResponse;
import org.example.user.dto.AssignRoleRequest;
import org.example.user.entity.Role;
import org.example.user.entity.User;
import org.example.user.entity.UserRole;
import org.example.user.entity.UserRoleId;
import org.example.user.repository.RoleRepository;
import org.example.user.repository.UserRepository;
import org.example.user.repository.UserRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * User Service - управление пользователями и ролями
 */
@Slf4j
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    /**
     * Получить профиль пользователя
     */
    public UserResponse getUserProfile(String email) {
        log.info("Getting profile for user: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("User not found: {}", email);
                    return new RuntimeException("User not found");
                });

        List<String> roles = getRolesForUser(email);

        return UserResponse.builder()
                .email(user.getEmail())
                .full_name(user.getFullName())
                .profile_picture_url(user.getProfilePictureUrl())
                .roles(roles)
                .is_active(user.isActive())
                .created_at(user.getCreatedAt())
                .build();
    }

    /**
     * Создать нового пользователя
     */
    @Transactional
    public UserResponse createUser(UserResponse userRequest) {
        log.info("Creating new user: {}", userRequest.getEmail());

        // Проверить что пользователь не существует
        if (userRepository.existsByEmail(userRequest.getEmail())) {
            log.warn("User already exists: {}", userRequest.getEmail());
            return getUserProfile(userRequest.getEmail());
        }

        // Создать пользователя
        User user = User.builder()
                .email(userRequest.getEmail())
                .googleId(userRequest.getEmail())  // Заглушка
                .fullName(userRequest.getFull_name())
                .profilePictureUrl(userRequest.getProfile_picture_url())
                .isActive(true)
                .build();

        userRepository.save(user);
        log.info("Created user: {}", userRequest.getEmail());

        // Назначить роль GUEST по умолчанию
        Role guestRole = roleRepository.findByRoleName("GUEST")
                .orElseThrow(() -> new RuntimeException("GUEST role not found"));

        UserRole userRole = UserRole.builder()
                .id(new UserRoleId(user.getEmail(), guestRole.getId()))
                .build();

        userRoleRepository.save(userRole);
        log.info("Assigned GUEST role to user: {}", user.getEmail());

        return getUserProfile(userRequest.getEmail());
    }

    /**
     * Назначить роль пользователю (только ADMIN)
     */
    @Transactional
    public UserResponse assignRole(String userEmail, AssignRoleRequest request, String adminEmail) {
        log.info("Admin {} is assigning role {} to user {}", adminEmail, request.getRole_name(), userEmail);

        // Проверить что пользователь существует
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Получить роль
        Role role = roleRepository.findByRoleName(request.getRole_name())
                .orElseThrow(() -> new RuntimeException("Role not found: " + request.getRole_name()));

        // Назначить роль
        UserRoleId id = new UserRoleId(userEmail, role.getId());
        UserRole userRole = UserRole.builder()
                .id(id)
                .assignedBy(adminEmail)
                .build();

        userRoleRepository.save(userRole);
        log.info("Assigned role {} to user {}", role.getRoleName(), userEmail);

        return getUserProfile(userEmail);
    }

    /**
     * Получить список всех пользователей (только для ADMIN)
     */
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        log.info("Fetching all users with pagination");

        Page<User> users = userRepository.findAll(pageable);
        return users.map(user -> {
            List<String> roles = getRolesForUser(user.getEmail());
            return UserResponse.builder()
                    .email(user.getEmail())
                    .full_name(user.getFullName())
                    .roles(roles)
                    .is_active(user.isActive())
                    .created_at(user.getCreatedAt())
                    .build();
        });
    }

    /**
     * Получить роли пользователя
     */
    private List<String> getRolesForUser(String email) {
        List<UserRole> userRoles = userRoleRepository.findByUserId(email);
        return userRoles.stream()
                .map(ur -> roleRepository.findById(ur.getId().getRoleId())
                        .map(Role::getRoleName)
                        .orElse("UNKNOWN"))
                .collect(Collectors.toList());
    }
}

