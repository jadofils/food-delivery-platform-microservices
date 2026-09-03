package food_delivery.Platform.identityservice.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import food_delivery.Platform.common.security.jwt.Public;
import food_delivery.Platform.identityservice.dto.LoginRequest;
import food_delivery.Platform.identityservice.dto.LoginResponse;
import food_delivery.Platform.identityservice.dto.RegisterUserRequest;
import food_delivery.Platform.identityservice.dto.UserResponse;
import food_delivery.Platform.identityservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Every endpoint here is {@link Public} — this is the only surface a caller can reach with no token yet. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Registration and login")
@Public
public class AuthController {

	private final UserService userService;

	@PostMapping("/register")
	@Operation(summary = "Register a new user account")
	public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
	}

	@PostMapping("/login")
	@Operation(summary = "Validate credentials and return the authenticated user plus an access token")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return userService.login(request);
	}

}
