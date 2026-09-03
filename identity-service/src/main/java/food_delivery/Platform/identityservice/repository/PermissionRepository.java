package food_delivery.Platform.identityservice.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import food_delivery.Platform.identityservice.domain.Permission;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

	Optional<Permission> findByName(String name);

	boolean existsByName(String name);

}
