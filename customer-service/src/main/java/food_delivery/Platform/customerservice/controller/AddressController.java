package food_delivery.Platform.customerservice.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import food_delivery.Platform.customerservice.dto.AddressRequest;
import food_delivery.Platform.customerservice.dto.AddressResponse;
import food_delivery.Platform.customerservice.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Entirely self-service — every method resolves the owning customer from the caller's own JWT
 * (see {@link AddressService}), so there is no admin/other-customer variant of this controller.
 */
@RestController
@RequestMapping("/api/customers/me/addresses")
@Tag(name = "Customer addresses")
public class AddressController {

	private final AddressService addressService;

	public AddressController(AddressService addressService) {
		this.addressService = addressService;
	}

	@Operation(summary = "List the caller's own delivery addresses")
	@GetMapping
	public List<AddressResponse> list(@AuthenticationPrincipal Jwt jwt) {
		return addressService.listForOwner(jwt).stream().map(AddressResponse::from).toList();
	}

	@Operation(summary = "Add a delivery address for the caller")
	@PostMapping
	public ResponseEntity<AddressResponse> add(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody AddressRequest request) {
		AddressResponse response = AddressResponse.from(addressService.addForOwner(jwt, request));
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@Operation(summary = "Get one of the caller's own delivery addresses")
	@GetMapping("/{addressId}")
	public AddressResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long addressId) {
		return AddressResponse.from(addressService.getForOwner(jwt, addressId));
	}

	@Operation(summary = "Update one of the caller's own delivery addresses")
	@PutMapping("/{addressId}")
	public AddressResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long addressId,
			@Valid @RequestBody AddressRequest request) {
		return AddressResponse.from(addressService.updateForOwner(jwt, addressId, request));
	}

	@Operation(summary = "Delete one of the caller's own delivery addresses")
	@DeleteMapping("/{addressId}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long addressId) {
		addressService.deleteForOwner(jwt, addressId);
		return ResponseEntity.noContent().build();
	}

}
