package food_delivery.Platform.orderservice.client;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * Token relay: copies the inbound request's own {@code Authorization} header onto every outbound
 * Feign call, so {@code customer-service}/{@code restaurant-service} see exactly the same
 * Keycloak-issued token the original caller sent — order-service never mints its own credential or
 * calls with elevated/service-account permissions to validate an order on someone else's behalf
 * (RULES.md §8). Registered as a plain {@code @Component}, not scoped to one
 * {@code @FeignClient(configuration = ...)}, since every Feign client this service has needs the
 * same relay.
 */
@Component
public class TokenRelayRequestInterceptor implements RequestInterceptor {

	@Override
	public void apply(RequestTemplate template) {
		if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes servletAttributes) {
			String authorization = servletAttributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
			if (authorization != null) {
				template.header(HttpHeaders.AUTHORIZATION, authorization);
			}
		}
	}

}
