package food_delivery.Platform.orderservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Powers {@code @CreatedDate}/{@code @LastModifiedDate} on {@code Order}. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
