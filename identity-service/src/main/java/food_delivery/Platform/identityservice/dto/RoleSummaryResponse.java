package food_delivery.Platform.identityservice.dto;

/**
 * The list-view shape for roles — deliberately without {@code permissions}: the list endpoint
 * doesn't load that association (RoleService#findAll never touches the lazy graph), so this type
 * doesn't pretend to carry data it never fetched. See {@link RoleResponse} for the detail view.
 */
public record RoleSummaryResponse(Long id, String name, String description) {
}
