package food_delivery.Platform.common.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;

import food_delivery.Platform.common.error.UnauthorizedException;

class JwtEncoderDecoderTest {

	private RSAKey signingKey;
	private SecretKey encryptionKey;
	private JwtEncoder encoder;
	private JwtDecoder decoder;

	@BeforeEach
	void setUp() throws Exception {
		signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
		encryptionKey = generateAesKey();

		encoder = new JwtEncoder(signingKey, encryptionKey, "fdp-identity-service");
		decoder = new JwtDecoder(signingKey.toPublicJWK(), encryptionKey, "fdp-identity-service");
	}

	@Test
	void encodeThenDecode_roundTripsTheClaims() {
		JwtClaims claims = new JwtClaims("user-123", Set.of("order:create", "order:read"),
				Instant.now().truncatedTo(ChronoUnit.SECONDS),
				Instant.now().plusSeconds(900).truncatedTo(ChronoUnit.SECONDS));

		JwtClaims decoded = decoder.decode(encoder.encode(claims));

		assertThat(decoded.subject()).isEqualTo("user-123");
		assertThat(decoded.permissions()).containsExactlyInAnyOrder("order:create", "order:read");
		assertThat(decoded.issuedAt()).isEqualTo(claims.issuedAt());
		assertThat(decoded.expiresAt()).isEqualTo(claims.expiresAt());
	}

	@Test
	void theTokenNeverContainsTheClaimsInPlainText() {
		String token = encoder.encode(new JwtClaims("user-123", Set.of("order:create"), Instant.now(),
				Instant.now().plusSeconds(900)));

		assertThat(token).doesNotContain("user-123").doesNotContain("order:create");
	}

	@Test
	void decode_rejectsAnExpiredToken() {
		String token = encoder.encode(new JwtClaims("user-123", Set.of(), Instant.now().minusSeconds(3600),
				Instant.now().minusSeconds(1800)));

		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(UnauthorizedException.class);
	}

	@Test
	void decode_rejectsATokenSignedByADifferentKey() throws Exception {
		RSAKey rogueSigningKey = new RSAKeyGenerator(2048).keyID("rogue-key").generate();
		JwtEncoder rogueEncoder = new JwtEncoder(rogueSigningKey, encryptionKey, "fdp-identity-service");
		String token = rogueEncoder.encode(
				new JwtClaims("user-123", Set.of(), Instant.now(), Instant.now().plusSeconds(900)));

		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(UnauthorizedException.class);
	}

	@Test
	void decode_rejectsATokenEncryptedWithADifferentKey() throws Exception {
		JwtEncoder rogueEncoder = new JwtEncoder(signingKey, generateAesKey(), "fdp-identity-service");
		String token = rogueEncoder.encode(
				new JwtClaims("user-123", Set.of(), Instant.now(), Instant.now().plusSeconds(900)));

		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(UnauthorizedException.class);
	}

	@Test
	void decode_rejectsGarbage() {
		assertThatThrownBy(() -> decoder.decode("not-a-real-token")).isInstanceOf(UnauthorizedException.class);
	}

	private static SecretKey generateAesKey() throws Exception {
		KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
		keyGenerator.init(256);
		return keyGenerator.generateKey();
	}

}
