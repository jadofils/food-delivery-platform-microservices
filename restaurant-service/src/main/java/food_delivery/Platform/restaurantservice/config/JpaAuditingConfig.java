package food_delivery.Platform.restaurantservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Powers {@code @CreatedDate}/{@code @LastModifiedDate} on {@code Restaurant}/{@code MenuItem}. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
