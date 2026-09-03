package food_delivery.Platform.common.security.jwt;

import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.crypto.DirectEncrypter;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import food_delivery.Platform.common.error.InternalServerException;

/**
 * Builds a nested JWT: signed first (RS256 — any service can verify authenticity from just the
 * public half of {@code signingKey}, no shared secret needed for that layer), then encrypted
 * (A256GCM, using a key every validating service also holds) so the claims themselves aren't
 * readable by decoding the token, only by a party that holds {@code encryptionKey}. See
 * docs/RULES.md §8.
 */
public class JwtEncoder {

	private final RSAKey signingKey;
	private final SecretKey encryptionKey;
	private final String issuer;

	public JwtEncoder(RSAKey signingKey, SecretKey encryptionKey, String issuer) {
		this.signingKey = signingKey;
		this.encryptionKey = encryptionKey;
		this.issuer = issuer;
	}

	public String encode(JwtClaims claims) {
		try {
			JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
					.issuer(issuer)
					.subject(claims.subject())
					.claim("permissions", List.copyOf(claims.permissions()))
					.issueTime(Date.from(claims.issuedAt()))
					.expirationTime(Date.from(claims.expiresAt()))
					.build();

			SignedJWT signedJwt = new SignedJWT(
					new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(), claimsSet);
			signedJwt.sign(new RSASSASigner(signingKey));

			JWEObject jweObject = new JWEObject(
					new JWEHeader.Builder(JWEAlgorithm.DIR, EncryptionMethod.A256GCM).build(),
					new Payload(signedJwt));
			jweObject.encrypt(new DirectEncrypter(encryptionKey));

			return jweObject.serialize();
		} catch (JOSEException e) {
			throw new InternalServerException("Failed to encode JWT.", e);
		}
	}

}
