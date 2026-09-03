package food_delivery.Platform.identityservice.dto;

/** {@code accessToken} is never masked — it's an opaque, encrypted credential, not display PII. */
public record LoginResponse(UserResponse user, String accessToken) {
}
