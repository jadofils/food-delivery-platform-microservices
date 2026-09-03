package food_delivery.Platform.identityservice.service;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import food_delivery.Platform.common.error.ConflictException;
import food_delivery.Platform.common.error.LockedException;
import food_delivery.Platform.common.error.ResourceNotFoundException;
import food_delivery.Platform.common.error.UnauthorizedException;
import food_delivery.Platform.identityservice.domain.Permission;
import food_delivery.Platform.identityservice.domain.Role;
import food_delivery.Platform.identityservice.domain.User;
import food_delivery.Platform.identityservice.dto.LoginRequest;
import food_delivery.Platform.identityservice.dto.RegisterUserRequest;
import food_delivery.Platform.identityservice.dto.UserResponse;
import food_delivery.Platform.identityservice.repository.RoleRepository;
import food_delivery.Platform.identityservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;

	@Transactional
	public UserResponse register(RegisterUserRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new ConflictException("Email '%s' is already registered.".formatted(request.email()));
		}
		User user = new User(request.email(), passwordEncoder.encode(request.password()));
		return toResponse(userRepository.save(user));
	}

	/**
	 * Validates credentials and returns the authenticated user with its roles/permissions.
	 * Deliberately doesn't issue a token yet — see docs/services/identity-service.md for why
	 * that's separate, later scope. The failure message never distinguishes "no such email" from
	 * "wrong password", to avoid leaking which emails are registered (a classic user-enumeration
	 * mistake).
	 */
	@Transactional(readOnly = true)
	public UserResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(request.email())
				.orElseThrow(() -> new UnauthorizedException("Invalid email or password."));

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new UnauthorizedException("Invalid email or password.");
		}
		if (!user.isEnabled()) {
			throw new UnauthorizedException("Invalid email or password.");
		}
		if (!user.isAccountNonLocked()) {
			throw new LockedException("This account is locked.");
		}
		return toResponse(user);
	}

	@Transactional(readOnly = true)
	public UserResponse findById(UUID id) {
		return toResponse(getUserOrThrow(id));
	}

	@Transactional(readOnly = true)
	public Page<UserResponse> findAll(Pageable pageable) {
		return userRepository.findAll(pageable).map(UserService::toResponse);
	}

	/** Idempotent: assigning a role the user already has just returns the current state. */
	@Transactional
	public UserResponse assignRole(UUID userId, String roleName) {
		User user = getUserOrThrow(userId);
		Role role = roleRepository.findByName(roleName)
				.orElseThrow(() -> new ResourceNotFoundException("Role '%s' not found.".formatted(roleName)));
		user.getRoles().add(role);
		return toResponse(user);
	}

	/** Idempotent: removing a role the user doesn't have is a no-op, not an error. */
	@Transactional
	public UserResponse removeRole(UUID userId, String roleName) {
		User user = getUserOrThrow(userId);
		user.getRoles().removeIf(role -> role.getName().equals(roleName));
		return toResponse(user);
	}

	@Transactional
	public UserResponse lock(UUID userId) {
		User user = getUserOrThrow(userId);
		user.setAccountNonLocked(false);
		return toResponse(user);
	}

	@Transactional
	public UserResponse unlock(UUID userId) {
		User user = getUserOrThrow(userId);
		user.setAccountNonLocked(true);
		return toResponse(user);
	}

	private User getUserOrThrow(UUID id) {
		return userRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("User '%s' not found.".formatted(id)));
	}

	private static UserResponse toResponse(User user) {
		Set<String> roleNames = new LinkedHashSet<>();
		Set<String> permissionNames = new LinkedHashSet<>();
		for (Role role : user.getRoles()) {
			roleNames.add(role.getName());
			for (Permission permission : role.getPermissions()) {
				permissionNames.add(permission.getName());
			}
		}
		return new UserResponse(user.getId(), user.getEmail(), user.isEnabled(), user.isAccountNonLocked(),
				roleNames, permissionNames, user.getCreatedAt());
	}

}
