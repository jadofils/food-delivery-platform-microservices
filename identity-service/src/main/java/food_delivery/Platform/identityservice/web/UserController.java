package food_delivery.Platform.identityservice.web;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import food_delivery.Platform.common.security.jwt.RequiresPermission;
import food_delivery.Platform.identityservice.dto.UserResponse;
import food_delivery.Platform.identityservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User accounts, role assignments, and account locking")
public class UserController {

	private final UserService userService;

	@GetMapping("/{id}")
	@RequiresPermission("user:read")
	@Operation(summary = "Get a user by id")
	public UserResponse get(@PathVariable UUID id) {
		return userService.findById(id);
	}

	@GetMapping
	@RequiresPermission("user:read")
	@Operation(summary = "List users, paginated")
	public Page<UserResponse> list(@PageableDefault(size = 20) Pageable pageable) {
		return userService.findAll(pageable);
	}

	@PostMapping("/{id}/roles/{roleName}")
	@RequiresPermission("user:manage")
	@Operation(summary = "Assign a role to a user")
	public UserResponse assignRole(@PathVariable UUID id, @PathVariable String roleName) {
		return userService.assignRole(id, roleName);
	}

	@DeleteMapping("/{id}/roles/{roleName}")
	@RequiresPermission("user:manage")
	@Operation(summary = "Remove a role from a user")
	public UserResponse removeRole(@PathVariable UUID id, @PathVariable String roleName) {
		return userService.removeRole(id, roleName);
	}

	@PostMapping("/{id}/lock")
	@RequiresPermission("user:manage")
	@Operation(summary = "Lock a user's account")
	public UserResponse lock(@PathVariable UUID id) {
		return userService.lock(id);
	}

	@PostMapping("/{id}/unlock")
	@RequiresPermission("user:manage")
	@Operation(summary = "Unlock a user's account")
	public UserResponse unlock(@PathVariable UUID id) {
		return userService.unlock(id);
	}

}
