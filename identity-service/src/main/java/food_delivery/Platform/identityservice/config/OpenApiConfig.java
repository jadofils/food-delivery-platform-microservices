package food_delivery.Platform.identityservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI identityServiceOpenApi() {
		return new OpenAPI().info(new Info()
				.title("FDP Identity Service")
				.description(
						"Users, roles, permissions (RBAC), and (later) JWT issuance. See docs/services/identity-service.md.")
				.version("v1"));
	}

}
