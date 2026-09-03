package food_delivery.Platform.common.security.masking;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Backs {@link Masked} — calls {@code toString()} so it works on any field type, then masks it.
 * {@code ValueSerializer} (not {@code JsonSerializer}): Boot 4 ships Jackson 3.x, which renamed
 * this type — see common/pom.xml's comment on the jackson-databind dependency.
 */
public class MaskedFieldSerializer extends ValueSerializer<Object> {

	@Override
	public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) {
		gen.writeString(value == null ? null : PiiMasking.mask(value.toString()));
	}

}
