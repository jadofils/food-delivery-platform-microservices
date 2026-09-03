package food_delivery.Platform.common.security.masking;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;

import tools.jackson.databind.annotation.JsonSerialize;

/**
 * Marks a DTO field/record component carrying human-readable PII (email, username, phone
 * number, …) for masking on the way out to JSON — see {@link PiiMasking}. See docs/RULES.md §8.
 *
 * <p><b>Never apply this to a structural identifier a client needs to address a resource with</b>
 * (a primary-key {@code id} used in a URL path). Masking that breaks the API's basic
 * addressability — the client that just registered or looked up that exact record can no longer
 * use the id it was just given — without adding real protection, since a bare id exposes nothing
 * by itself.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.RECORD_COMPONENT, ElementType.FIELD, ElementType.METHOD })
@JacksonAnnotationsInside
@JsonSerialize(using = MaskedFieldSerializer.class)
public @interface Masked {
}
