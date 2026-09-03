package food_delivery.Platform.identityservice.config;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;

import food_delivery.Platform.common.security.jwt.JwtAuthenticationFilter;
import food_delivery.Platform.common.security.jwt.JwtDecoder;
import food_delivery.Platform.common.security.jwt.JwtEncoder;
import food_delivery.Platform.common.security.jwt.PermissionInterceptor;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires the shared JWT kernel ({@code common.security.jwt}) into this service: generates the
 * signing/encryption keys, exposes the encoder/decoder as beans, registers the authentication
 * filter and the permission interceptor that together secure every endpoint not marked
 * {@code @Public}. See docs/RULES.md §8.
 *
 * <p>Keys are generated fresh at every startup — correct for a single-instance dev setup, but
 * tokens issued before a restart won't verify after one, and a multi-instance deployment needs
 * these sourced from Config Server rather than generated locally. Both explicitly deferred until
 * Config Server exists.
 */
@Configuration
public class SecurityConfig implements WebMvcConfigurer {

	private static final String ISSUER = "fdp-identity-service";

	@Bean
	public RSAKey jwtSigningKey() throws Exception {
		return new RSAKeyGenerator(2048).keyID("identity-service-key").generate();
	}

	@Bean
	public SecretKey jwtEncryptionKey() throws Exception {
		KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
		keyGenerator.init(256);
		return keyGenerator.generateKey();
	}

	@Bean
	public JwtEncoder jwtEncoder(RSAKey jwtSigningKey, SecretKey jwtEncryptionKey) {
		return new JwtEncoder(jwtSigningKey, jwtEncryptionKey, ISSUER);
	}

	@Bean
	public JwtDecoder jwtDecoder(RSAKey jwtSigningKey, SecretKey jwtEncryptionKey) throws Exception {
		return new JwtDecoder(jwtSigningKey.toPublicJWK(), jwtEncryptionKey, ISSUER);
	}

	@Bean
	public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilter(JwtDecoder jwtDecoder,
			ObjectMapper objectMapper) {
		FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(
				new JwtAuthenticationFilter(jwtDecoder, objectMapper));
		registration.addUrlPatterns("/api/*");
		return registration;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new PermissionInterceptor());
	}

}
