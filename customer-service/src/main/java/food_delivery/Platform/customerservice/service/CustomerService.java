package food_delivery.Platform.customerservice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import food_delivery.Platform.common.error.ConflictException;
import food_delivery.Platform.common.error.ResourceNotFoundException;
import food_delivery.Platform.common.security.jwt.JwtClaims;
import food_delivery.Platform.customerservice.dto.CustomerRegistrationRequest;
import food_delivery.Platform.customerservice.dto.CustomerUpdateRequest;
import food_delivery.Platform.customerservice.entity.Customer;
import food_delivery.Platform.customerservice.repository.CustomerRepository;

@Service
public class CustomerService {

	private final CustomerRepository customerRepository;

	public CustomerService(CustomerRepository customerRepository) {
		this.customerRepository = customerRepository;
	}

	/**
	 * Creates this Keycloak user's FDP customer profile — the "registration" step from
	 * customer-service's point of view (RULES.md §8: Keycloak already handled the actual account
	 * creation; this is domain onboarding on top of it). One profile per Keycloak identity, so a
	 * repeat call is a {@link ConflictException}, not a silent no-op or a second row.
	 */
	@Transactional
	public Customer register(Jwt jwt, CustomerRegistrationRequest request) {
		String keycloakId = JwtClaims.subject(jwt);
		if (customerRepository.existsByKeycloakId(keycloakId)) {
			throw new ConflictException("A customer profile already exists for this account.");
		}
		Customer customer = new Customer(
				keycloakId,
				JwtClaims.email(jwt),
				request.phoneNumber(),
				JwtClaims.firstName(jwt),
				JwtClaims.lastName(jwt));
		return customerRepository.save(customer);
	}

	@Transactional(readOnly = true)
	public Customer getOwnProfile(Jwt jwt) {
		return findByKeycloakIdOrThrow(JwtClaims.subject(jwt));
	}

	@Transactional
	public Customer updateOwnProfile(Jwt jwt, CustomerUpdateRequest request) {
		Customer customer = findByKeycloakIdOrThrow(JwtClaims.subject(jwt));
		customer.setPhoneNumber(request.phoneNumber());
		return customer;
	}

	/** Admin-facing lookup by structural id — gated by {@code user:read} at the controller. */
	@Transactional(readOnly = true)
	public Customer getById(Long id) {
		return customerRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("No customer with id " + id));
	}

	/** Admin-facing listing — gated by {@code user:read} at the controller. */
	@Transactional(readOnly = true)
	public Page<Customer> list(Pageable pageable) {
		return customerRepository.findAll(pageable);
	}

	private Customer findByKeycloakIdOrThrow(String keycloakId) {
		return customerRepository.findByKeycloakId(keycloakId)
				.orElseThrow(() -> new ResourceNotFoundException(
						"No customer profile yet for this account — POST /api/customers/me first."));
	}

}
