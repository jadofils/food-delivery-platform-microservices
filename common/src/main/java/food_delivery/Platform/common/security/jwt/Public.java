package food_delivery.Platform.common.security.jwt;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint as intentionally unauthenticated (registration, login, …). Every endpoint
 * NOT marked with this requires a valid token by default — see {@link PermissionInterceptor}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface Public {
}
