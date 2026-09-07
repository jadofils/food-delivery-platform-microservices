package food_delivery.Platform.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** Same global {@code bearerAuth} scheme pattern as the other services' configs. */
@Configuration
public class OpenApiConfig {

	private static final String BEARER_SCHEME = "bearerAuth";

	@Bean
	public OpenAPI notificationServiceOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("FDP notification-service")
						.description("Notification/audit history, consumes domain events. See docs/services/notification-service.md.")
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
