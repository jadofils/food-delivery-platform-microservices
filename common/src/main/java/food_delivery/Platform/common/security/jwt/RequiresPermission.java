package food_delivery.Platform.common.security.jwt;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requires the caller's token to carry this exact permission string (RULES.md §8 — services
 * authorize on permission strings, not role names). An endpoint with neither this nor
 * {@link Public} still requires authentication, just no specific permission.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface RequiresPermission {

	String value();

}
