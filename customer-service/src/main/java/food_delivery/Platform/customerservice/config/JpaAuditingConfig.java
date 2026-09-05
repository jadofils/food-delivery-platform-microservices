package food_delivery.Platform.customerservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Powers {@code @CreatedDate}/{@code @LastModifiedDate} on {@code Customer}/{@code Address}. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
