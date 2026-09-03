package food_delivery.Platform.identityservice.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import food_delivery.Platform.common.error.ConflictException;
import food_delivery.Platform.identityservice.domain.Permission;
import food_delivery.Platform.identityservice.dto.PermissionRequest;
import food_delivery.Platform.identityservice.dto.PermissionResponse;
import food_delivery.Platform.identityservice.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PermissionService {

	private final PermissionRepository permissionRepository;

	@Transactional
	public PermissionResponse create(PermissionRequest request) {
		if (permissionRepository.existsByName(request.name())) {
			throw new ConflictException("Permission '%s' already exists.".formatted(request.name()));
		}
		Permission saved = permissionRepository.save(new Permission(request.name(), request.description()));
		return toResponse(saved);
	}

	@Transactional(readOnly = true)
	public List<PermissionResponse> findAll() {
		return permissionRepository.findAll().stream().map(PermissionService::toResponse).toList();
	}

	private static PermissionResponse toResponse(Permission permission) {
		return new PermissionResponse(permission.getId(), permission.getName(), permission.getDescription());
	}

}
