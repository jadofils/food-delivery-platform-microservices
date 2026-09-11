package food_delivery.Platform.customerservice.client;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * Token relay: copies the inbound request's own {@code Authorization} header onto every outbound
 * Feign call, so {@code order-service} sees exactly the same Keycloak-issued token the customer
 * sent — customer-service never mints its own credential or calls with elevated permissions to
 * build the caller's own overview (RULES.md §8). Identical to {@code order-service}'s own class of
 * the same name; not yet promoted to {@code common} (RULES.md §3/§16 — only once a piece of logic
 * is duplicated 3+ times), since this is only the second service with an outbound Feign client.
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
