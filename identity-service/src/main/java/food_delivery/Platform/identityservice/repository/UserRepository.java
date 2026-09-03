package food_delivery.Platform.identityservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

import food_delivery.Platform.identityservice.domain.User;

public interface UserRepository extends JpaRepository<User, UUID> {

	boolean existsByEmail(String email);

	/**
	 * Fetches the user together with its roles AND each role's permissions in one query — the
	 * shape login and permission-lookup callers actually need. {@code existsByEmail} above stays
	 * the right call for a plain existence check, which needs no entity loaded at all.
	 */
	@EntityGraph(attributePaths = { "roles", "roles.permissions" })
	Optional<User> findByEmail(String email);

	/**
	 * Overrides the inherited {@code findById} to attach the same graph — every caller of this
	 * method (get-by-id, role assignment) needs the full permission set to build a
	 * {@code UserResponse}. Spring Data JPA explicitly supports re-declaring an inherited method
	 * to customize its fetch graph.
	 */
	@Override
	@EntityGraph(attributePaths = { "roles", "roles.permissions" })
	Optional<User> findById(@NonNull UUID id);

	/**
	 * Same graph, applied to the paginated listing so a page of N users doesn't turn into N+1
	 * queries when each row's roles/permissions are rendered.
	 */
	@Override
	@EntityGraph(attributePaths = { "roles", "roles.permissions" })
	Page<User> findAll(@NonNull Pageable pageable);

}
