package food_delivery.Platform.common.security.jwt;

import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.crypto.SecretKey;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import food_delivery.Platform.common.error.UnauthorizedException;

/**
 * Decrypts then verifies a nested JWT built by {@link JwtEncoder}. Every failure mode —
 * malformed token, decryption failure, bad signature, expiry, wrong issuer — surfaces as the
 * same {@link UnauthorizedException} with the same message, so a caller never learns which check
 * failed (RULES.md §14's "never leak specifics" principle, applied to auth failures).
 */
public class JwtDecoder {

	private static final String INVALID_TOKEN = "Invalid or expired token.";

	private final RSAKey verificationKey;
	private final SecretKey encryptionKey;
	private final String expectedIssuer;

	public JwtDecoder(RSAKey verificationKey, SecretKey encryptionKey, String expectedIssuer) {
		this.verificationKey = verificationKey;
		this.encryptionKey = encryptionKey;
		this.expectedIssuer = expectedIssuer;
	}

	public JwtClaims decode(String token) {
		try {
			JWEObject jweObject = JWEObject.parse(token);
			jweObject.decrypt(new DirectDecrypter(encryptionKey));

			SignedJWT signedJwt = jweObject.getPayload().toSignedJWT();
			if (signedJwt == null || !signedJwt.verify(new RSASSAVerifier(verificationKey))) {
				throw new UnauthorizedException(INVALID_TOKEN);
			}

			JWTClaimsSet claimsSet = signedJwt.getJWTClaimsSet();
			if (!expectedIssuer.equals(claimsSet.getIssuer())) {
				throw new UnauthorizedException(INVALID_TOKEN);
			}
			Date expiration = claimsSet.getExpirationTime();
			if (expiration == null || expiration.before(new Date())) {
				throw new UnauthorizedException(INVALID_TOKEN);
			}

			@SuppressWarnings("unchecked")
			List<String> permissions = (List<String>) claimsSet.getClaim("permissions");
			return new JwtClaims(claimsSet.getSubject(),
					permissions == null ? Set.of() : Set.copyOf(permissions),
					claimsSet.getIssueTime().toInstant(), expiration.toInstant());
		} catch (UnauthorizedException e) {
			throw e;
		} catch (ParseException | JOSEException | RuntimeException e) {
			throw new UnauthorizedException(INVALID_TOKEN);
		}
	}

}
