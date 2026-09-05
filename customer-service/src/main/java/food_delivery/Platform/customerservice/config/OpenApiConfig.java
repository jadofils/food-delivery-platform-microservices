package food_delivery.Platform.customerservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Registers one global {@code bearerAuth} scheme so every operation in Swagger UI shows an
 * "Authorize" button, rather than annotating each controller method individually — every endpoint
 * needs the exact same Keycloak-issued Bearer token (RULES.md §8). Reachable at
 * {@code /swagger-ui/index.html} once the service is running (`docs/technologies` convention).
 */
@Configuration
public class OpenApiConfig {

	private static final String BEARER_SCHEME = "bearerAuth";

	@Bean
	public OpenAPI customerServiceOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("FDP customer-service")
						.description("Customer profiles and delivery addresses. See docs/services/customer-service.md.")
						.version("v1"))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
				.components(new Components().addSecuritySchemes(BEARER_SCHEME,
						new SecurityScheme()
								.name(BEARER_SCHEME)
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")));
	}

}
