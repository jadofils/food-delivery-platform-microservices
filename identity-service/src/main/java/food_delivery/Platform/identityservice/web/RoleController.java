package food_delivery.Platform.identityservice.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import food_delivery.Platform.common.security.jwt.RequiresPermission;
import food_delivery.Platform.identityservice.dto.RoleRequest;
import food_delivery.Platform.identityservice.dto.RoleResponse;
import food_delivery.Platform.identityservice.dto.RoleSummaryResponse;
import food_delivery.Platform.identityservice.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Class-level {@link RequiresPermission}: every role-management operation needs the same permission. */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Roles", description = "Roles and their permission grants")
@RequiresPermission("role:manage")
public class RoleController {

	private final RoleService roleService;

	@PostMapping
	@Operation(summary = "Create a role")
	public ResponseEntity<RoleResponse> create(@Valid @RequestBody RoleRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(roleService.create(request));
	}

	@GetMapping
	@Operation(summary = "List all roles")
	public List<RoleSummaryResponse> list() {
		return roleService.findAll();
	}

	@GetMapping("/{name}")
	@Operation(summary = "Get a role by name, with its permissions")
	public RoleResponse get(@PathVariable String name) {
		return roleService.findByName(name);
	}

	@PostMapping("/{name}/permissions/{permissionName}")
	@Operation(summary = "Grant a permission to a role")
	public RoleResponse attachPermission(@PathVariable String name, @PathVariable String permissionName) {
		return roleService.attachPermission(name, permissionName);
	}

	@DeleteMapping("/{name}/permissions/{permissionName}")
	@Operation(summary = "Revoke a permission from a role")
	public RoleResponse detachPermission(@PathVariable String name, @PathVariable String permissionName) {
		return roleService.detachPermission(name, permissionName);
	}

}
