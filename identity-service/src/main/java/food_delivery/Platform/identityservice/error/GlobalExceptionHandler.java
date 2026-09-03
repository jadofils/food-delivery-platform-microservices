package food_delivery.Platform.identityservice.error;

import org.springframework.web.bind.annotation.RestControllerAdvice;

import food_delivery.Platform.common.error.AbstractGlobalExceptionHandler;

/**
 * Wires the shared exception-handling base into this service. {@code identity-service} makes no
 * outbound Feign calls yet, so there is nothing to add beyond the shared base — see
 * {@link AbstractGlobalExceptionHandler}'s javadoc for what a service that does would add here.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends AbstractGlobalExceptionHandler {
}
