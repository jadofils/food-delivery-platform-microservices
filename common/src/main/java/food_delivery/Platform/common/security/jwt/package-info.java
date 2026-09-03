/**
 * Shared JWT kernel: {@link food_delivery.Platform.common.security.jwt.JwtClaims} (the claim
 * shape), {@link food_delivery.Platform.common.security.jwt.JwtEncoder}/
 * {@link food_delivery.Platform.common.security.jwt.JwtDecoder} (nested sign-then-encrypt /
 * decrypt-then-verify), and the request pipeline that enforces it:
 * {@link food_delivery.Platform.common.security.jwt.JwtAuthenticationFilter} (the servlet
 * filter-chain link that decodes a Bearer token) feeding
 * {@link food_delivery.Platform.common.security.jwt.PermissionInterceptor} (the Spring MVC
 * dispatch step that enforces {@link food_delivery.Platform.common.security.jwt.Public} /
 * {@link food_delivery.Platform.common.security.jwt.RequiresPermission}). identity-service is the
 * sole holder of the RSA private signing key; every service that validates holds only the public
 * half plus the shared symmetric encryption key. See docs/RULES.md §8.
 */
package food_delivery.Platform.common.security.jwt;
