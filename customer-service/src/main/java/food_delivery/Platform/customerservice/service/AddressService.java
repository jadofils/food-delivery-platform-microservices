package food_delivery.Platform.customerservice.service;

import java.util.List;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import food_delivery.Platform.common.error.ResourceNotFoundException;
import food_delivery.Platform.common.security.jwt.JwtClaims;
import food_delivery.Platform.customerservice.dto.AddressRequest;
import food_delivery.Platform.customerservice.entity.Address;
import food_delivery.Platform.customerservice.entity.Customer;
import food_delivery.Platform.customerservice.repository.AddressRepository;
import food_delivery.Platform.customerservice.repository.CustomerRepository;

/**
 * Every method here resolves "which customer" from the caller's own JWT, never from a
 * client-supplied customer id — an authenticated customer can only ever act on their own
 * addresses (RULES.md §8). {@link AddressRepository#findByIdAndCustomerId} is what actually
 * enforces that at the query level, not just in application logic.
 */
@Service
public class AddressService {

	private final AddressRepository addressRepository;
	private final CustomerRepository customerRepository;

	public AddressService(AddressRepository addressRepository, CustomerRepository customerRepository) {
		this.addressRepository = addressRepository;
		this.customerRepository = customerRepository;
	}

	@Transactional(readOnly = true)
	public List<Address> listForOwner(Jwt jwt) {
		Customer customer = ownerOf(jwt);
		return customerRepository.findWithAddressesByKeycloakId(customer.getKeycloakId())
				.map(Customer::getAddresses)
				.orElseGet(List::of);
	}

	@Transactional
	public Address addForOwner(Jwt jwt, AddressRequest request) {
		Customer customer = ownerOf(jwt);
		Address address = new Address(request.label(), request.street(), request.city(), request.state(),
				request.postalCode(), request.country(), request.isDefault());
		customer.addAddress(address);
		return addressRepository.save(address);
	}

	@Transactional(readOnly = true)
	public Address getForOwner(Jwt jwt, Long addressId) {
		Customer customer = ownerOf(jwt);
		return addressRepository.findByIdAndCustomerId(addressId, customer.getId())
				.orElseThrow(() -> new ResourceNotFoundException("No address " + addressId + " on this account."));
	}

	@Transactional
	public Address updateForOwner(Jwt jwt, Long addressId, AddressRequest request) {
		Address address = getForOwner(jwt, addressId);
		address.setLabel(request.label());
		address.setStreet(request.street());
		address.setCity(request.city());
		address.setState(request.state());
		address.setPostalCode(request.postalCode());
		address.setCountry(request.country());
		address.setDefault(request.isDefault());
		return address;
	}

	@Transactional
	public void deleteForOwner(Jwt jwt, Long addressId) {
		Address address = getForOwner(jwt, addressId);
		Customer customer = address.getCustomer();
		customer.removeAddress(address);
	}

	private Customer ownerOf(Jwt jwt) {
		String keycloakId = JwtClaims.subject(jwt);
		return customerRepository.findByKeycloakId(keycloakId)
				.orElseThrow(() -> new ResourceNotFoundException(
						"No customer profile yet for this account — POST /api/customers/me first."));
	}

}
