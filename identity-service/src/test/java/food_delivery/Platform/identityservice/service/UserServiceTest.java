package food_delivery.Platform.identityservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import food_delivery.Platform.common.error.ConflictException;
import food_delivery.Platform.common.error.LockedException;
import food_delivery.Platform.common.error.UnauthorizedException;
import food_delivery.Platform.common.security.jwt.JwtEncoder;
import food_delivery.Platform.identityservice.domain.User;
import food_delivery.Platform.identityservice.dto.LoginRequest;
import food_delivery.Platform.identityservice.dto.RegisterUserRequest;
import food_delivery.Platform.identityservice.repository.RoleRepository;
import food_delivery.Platform.identityservice.repository.UserRepository;

class UserServiceTest {

	private final UserRepository userRepository = mock(UserRepository.class);
	private final RoleRepository roleRepository = mock(RoleRepository.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final JwtEncoder jwtEncoder = mock(JwtEncoder.class);
	private final UserService service = new UserService(userRepository, roleRepository, passwordEncoder,
			jwtEncoder);

	@Test
	void register_rejectsADuplicateEmail() {
		when(userRepository.existsByEmail("a@fdp.test")).thenReturn(true);

		assertThatThrownBy(() -> service.register(new RegisterUserRequest("a@fdp.test", "password123")))
				.isInstanceOf(ConflictException.class);
	}

	@Test
	void register_neverPersistsTheRawPassword() {
		when(userRepository.existsByEmail("a@fdp.test")).thenReturn(false);
		when(passwordEncoder.encode("password123")).thenReturn("hashed-value");
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		service.register(new RegisterUserRequest("a@fdp.test", "password123"));

		var captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());
		assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed-value");
	}

	@Test
	void login_rejectsAnUnknownEmailWithTheSameMessageAsAWrongPassword() {
		when(userRepository.findByEmail("ghost@fdp.test")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.login(new LoginRequest("ghost@fdp.test", "whatever")))
				.isInstanceOf(UnauthorizedException.class)
				.hasMessage("Invalid email or password.");
	}

	@Test
	void login_rejectsAWrongPasswordWithTheSameMessageAsAnUnknownEmail() {
		User user = userWithId("a@fdp.test", "hashed");
		when(userRepository.findByEmail("a@fdp.test")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

		assertThatThrownBy(() -> service.login(new LoginRequest("a@fdp.test", "wrong")))
				.isInstanceOf(UnauthorizedException.class)
				.hasMessage("Invalid email or password.");
	}

	@Test
	void login_rejectsALockedAccountEvenWithCorrectCredentials() {
		User user = userWithId("a@fdp.test", "hashed");
		user.setAccountNonLocked(false);
		when(userRepository.findByEmail("a@fdp.test")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);

		assertThatThrownBy(() -> service.login(new LoginRequest("a@fdp.test", "password123")))
				.isInstanceOf(LockedException.class);
	}

	@Test
	void login_succeedsWithCorrectCredentialsAndReturnsAToken() {
		User user = userWithId("a@fdp.test", "hashed");
		when(userRepository.findByEmail("a@fdp.test")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
		when(jwtEncoder.encode(any())).thenReturn("encoded-token");

		var response = service.login(new LoginRequest("a@fdp.test", "password123"));

		assertThat(response.user().email()).isEqualTo("a@fdp.test");
		assertThat(response.accessToken()).isEqualTo("encoded-token");
	}

	private static User userWithId(String email, String passwordHash) {
		User user = new User(email, passwordHash);
		user.setId(UUID.randomUUID());
		return user;
	}

}
