package food_delivery.Platform.customerservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import food_delivery.Platform.customerservice.entity.Address;

public interface AddressRepository extends JpaRepository<Address, Long> {

	List<Address> findByCustomerId(Long customerId);

	/** Ownership check baked into the query itself — never trust a path id alone (RULES.md §8). */
	Optional<Address> findByIdAndCustomerId(Long id, Long customerId);

}
