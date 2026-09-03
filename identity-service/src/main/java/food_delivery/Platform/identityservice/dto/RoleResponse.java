package food_delivery.Platform.identityservice.dto;

import java.util.Set;

/** Role detail, including its current permission grants — see {@link RoleSummaryResponse} for the list-view shape. */
public record RoleResponse(Long id, String name, String description, Set<String> permissions) {
}
