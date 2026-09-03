package food_delivery.Platform.identityservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Backs {@code @CreatedDate}/{@code @LastModifiedDate} on the domain entities. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
