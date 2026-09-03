/**
 * Shared error-handling kernel used by every FDP service's global exception handler.
 *
 * <p>Holds the {@link food_delivery.Platform.common.error.DomainException} hierarchy and the
 * {@link food_delivery.Platform.common.error.ApiErrorResponse} envelope described in
 * docs/RULES.md §14. Each service still owns its own {@code @RestControllerAdvice} — this
 * package only supplies the shared types that advice maps from and to, so all nine services
 * return byte-identical error shapes.
 *
 * <h2>Exception taxonomy</h2>
 *
 * <p>{@code DomainException} splits into two families, covering every HTTP 4xx and 5xx status a
 * service is realistically expected to raise:
 *
 * <ul>
 * <li>{@link food_delivery.Platform.common.error.ClientErrorException} (4xx) — the request is the
 * problem: {@code BadRequestException} (400), {@code UnauthorizedException} (401),
 * {@code ForbiddenException} (403), {@code ResourceNotFoundException} (404),
 * {@code MethodNotAllowedException} (405), {@code NotAcceptableException} (406),
 * {@code RequestTimeoutException} (408), {@code ConflictException} (409), {@code GoneException}
 * (410), {@code PreconditionFailedException} (412), {@code PayloadTooLargeException} (413),
 * {@code UnsupportedMediaTypeException} (415), {@code BusinessRuleViolationException} (422),
 * {@code LockedException} (423), {@code TooManyRequestsException} (429).</li>
 * <li>{@link food_delivery.Platform.common.error.ServerErrorException} (5xx) — this service, or
 * something it depends on, is the problem: {@code InternalServerException} (500),
 * {@code BadGatewayException} (502), {@code ServiceUnavailableException} (503),
 * {@code GatewayTimeoutException} (504).</li>
 * </ul>
 *
 * <p>Nothing represents 1xx or 2xx: those are never thrown, they're a controller method's normal
 * return value (a 2xx isn't an error, and a 1xx is protocol-level, handled below the application).
 * Nothing represents 3xx either: redirection has no real place in a JSON REST API — if a service
 * ever needs one, it's a plain {@code ResponseEntity} with a {@code Location} header, not an
 * exception. An exception hierarchy models things that went wrong; 1xx/2xx/3xx aren't that.
 */
package food_delivery.Platform.common.error;
