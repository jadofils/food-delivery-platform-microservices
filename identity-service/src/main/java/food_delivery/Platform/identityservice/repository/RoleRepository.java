package food_delivery.Platform.identityservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import food_delivery.Platform.identityservice.domain.Role;

public interface RoleRepository extends JpaRepository<Role, Long> {

	boolean existsByName(String name);

	/**
	 * Fetches the role together with its permissions in one query. {@code Role#permissions} stays
	 * {@code LAZY} on the entity — this is the one place the full graph is actually needed
	 * (viewing/editing a role's grants), so it's requested here explicitly rather than by making
	 * the association eager everywhere. See docs/RULES.md's optimization note.
	 */
	@EntityGraph(attributePaths = "permissions")
	Optional<Role> findByName(String name);

}
