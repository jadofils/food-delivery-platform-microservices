package food_delivery.Platform.deliveryservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Powers {@code @CreatedDate}/{@code @LastModifiedDate} on {@code DeliveryAssignment}. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
