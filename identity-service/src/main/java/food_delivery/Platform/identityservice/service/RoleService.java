package food_delivery.Platform.identityservice.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import food_delivery.Platform.common.error.ConflictException;
import food_delivery.Platform.common.error.ResourceNotFoundException;
import food_delivery.Platform.identityservice.domain.Permission;
import food_delivery.Platform.identityservice.domain.Role;
import food_delivery.Platform.identityservice.dto.RoleRequest;
import food_delivery.Platform.identityservice.dto.RoleResponse;
import food_delivery.Platform.identityservice.dto.RoleSummaryResponse;
import food_delivery.Platform.identityservice.repository.PermissionRepository;
import food_delivery.Platform.identityservice.repository.RoleRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoleService {

	private final RoleRepository roleRepository;
	private final PermissionRepository permissionRepository;

	@Transactional
	public RoleResponse create(RoleRequest request) {
		if (roleRepository.existsByName(request.name())) {
			throw new ConflictException("Role '%s' already exists.".formatted(request.name()));
		}
		Role saved = roleRepository.save(new Role(request.name(), request.description()));
		return toResponse(saved);
	}

	/** List view — permissions are never loaded here; see {@link RoleSummaryResponse}'s javadoc. */
	@Transactional(readOnly = true)
	public List<RoleSummaryResponse> findAll() {
		return roleRepository.findAll().stream()
				.map(role -> new RoleSummaryResponse(role.getId(), role.getName(), role.getDescription()))
				.toList();
	}

	@Transactional(readOnly = true)
	public RoleResponse findByName(String name) {
		return toResponse(getRoleOrThrow(name));
	}

	/** Idempotent: granting a permission the role already has just returns the current state. */
	@Transactional
	public RoleResponse attachPermission(String roleName, String permissionName) {
		Role role = getRoleOrThrow(roleName);
		Permission permission = permissionRepository.findByName(permissionName)
				.orElseThrow(
						() -> new ResourceNotFoundException("Permission '%s' not found.".formatted(permissionName)));
		role.getPermissions().add(permission);
		return toResponse(role);
	}

	/** Idempotent: revoking a permission the role doesn't have is a no-op, not an error. */
	@Transactional
	public RoleResponse detachPermission(String roleName, String permissionName) {
		Role role = getRoleOrThrow(roleName);
		role.getPermissions().removeIf(permission -> permission.getName().equals(permissionName));
		return toResponse(role);
	}

	private Role getRoleOrThrow(String name) {
		return roleRepository.findByName(name)
				.orElseThrow(() -> new ResourceNotFoundException("Role '%s' not found.".formatted(name)));
	}

	private static RoleResponse toResponse(Role role) {
		Set<String> permissionNames = new LinkedHashSet<>();
		role.getPermissions().forEach(permission -> permissionNames.add(permission.getName()));
		return new RoleResponse(role.getId(), role.getName(), role.getDescription(), permissionNames);
	}

}
