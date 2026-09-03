package food_delivery.Platform.identityservice.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import food_delivery.Platform.identityservice.dto.PermissionRequest;
import food_delivery.Platform.identityservice.dto.PermissionResponse;
import food_delivery.Platform.identityservice.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
@Tag(name = "Permissions", description = "Fine-grained permission strings roles are built from")
public class PermissionController {

	private final PermissionService permissionService;

	@PostMapping
	@Operation(summary = "Create a permission")
	public ResponseEntity<PermissionResponse> create(@Valid @RequestBody PermissionRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(permissionService.create(request));
	}

	@GetMapping
	@Operation(summary = "List all permissions")
	public List<PermissionResponse> list() {
		return permissionService.findAll();
	}

}
