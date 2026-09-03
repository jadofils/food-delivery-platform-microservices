package food_delivery.Platform.common.security.jwt;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import food_delivery.Platform.common.error.ForbiddenException;
import food_delivery.Platform.common.error.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Enforces {@link Public} / {@link RequiresPermission} against the {@link JwtClaims} that
 * {@link JwtAuthenticationFilter} attached to the request. Default-secure: any endpoint not
 * marked {@link Public} requires a valid token, whether or not it also names a specific
 * permission — "secure all endpoints" (RULES.md §8) means opting OUT is explicit, not opting in.
 * Register this via a {@code WebMvcConfigurer} in each MVC-based service.
 *
 * <p>Throwing here (rather than writing the response directly, like the filter does) is
 * deliberate: {@code HandlerInterceptor#preHandle} runs inside Spring MVC's own dispatch, so
 * these exceptions reach {@code @RestControllerAdvice} normally and come back in the standard
 * {@code ApiErrorResponse} shape with no special-casing needed here.
 */
public class PermissionInterceptor implements HandlerInterceptor {

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!(handler instanceof HandlerMethod handlerMethod)) {
			return true;
		}
		if (handlerMethod.hasMethodAnnotation(Public.class)
				|| handlerMethod.getBeanType().isAnnotationPresent(Public.class)) {
			return true;
		}

		Object attribute = request.getAttribute(JwtAuthenticationFilter.REQUEST_ATTRIBUTE);
		if (!(attribute instanceof JwtClaims claims)) {
			throw new UnauthorizedException("Authentication required.");
		}

		RequiresPermission required = handlerMethod.getMethodAnnotation(RequiresPermission.class);
		if (required == null) {
			required = handlerMethod.getBeanType().getAnnotation(RequiresPermission.class);
		}
		if (required != null && !claims.permissions().contains(required.value())) {
			throw new ForbiddenException("Missing required permission '%s'.".formatted(required.value()));
		}

		return true;
	}

}
