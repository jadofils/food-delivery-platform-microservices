package food_delivery.Platform.restaurantservice.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import food_delivery.Platform.restaurantservice.dto.MenuItemResponse;
import food_delivery.Platform.restaurantservice.dto.RestaurantResponse;

/**
 * Distributed caching (RULES.md §12) for restaurant-service's read-heavy public browsing
 * endpoints — {@code GET /api/restaurants/{id}} and {@code GET /api/restaurants/{id}/menu-items}.
 * Redis, not an in-memory cache: per RULES.md §12, "no per-instance local/in-memory caches, which
 * would break statelessness across horizontally-scaled replicas."
 *
 * <p>Two cache names, each namespaced with this service's own name so multiple services can share
 * one Redis instance without key collisions (RULES.md §12): {@code restaurant-service:restaurant}
 * (single restaurant by id) and {@code restaurant-service:menu} (a restaurant's menu item list, by
 * restaurant id). Every entry gets an explicit TTL — nothing is cached indefinitely (RULES.md §12).
 * Two minutes is a deliberate local-dev/demo choice, short enough to observe expiry directly via
 * {@code redis-cli ttl} without a long wait, not a claim about the right value for a real
 * production read/write ratio.
 *
 * <p>Each cache gets its own {@link Jackson2JsonRedisSerializer} bound to its one, always-known
 * value type ({@code RestaurantResponse}, {@code List<MenuItemResponse>}) — deliberately NOT
 * {@code GenericJackson2JsonRedisSerializer} with default typing, which was tried first and failed
 * two different ways once a genuine cache HIT was actually exercised live (population/eviction
 * checks alone never re-read a value back out, so neither failure showed up until later):
 * <ol>
 *   <li>The raw {@code new GenericJackson2JsonRedisSerializer(objectMapper)} constructor embeds no
 *   type hint at all — a hit deserializes to a generic {@code LinkedHashMap}, not the original DTO
 *   type ({@code ClassCastException}).</li>
 *   <li>Switching to {@code .builder().defaultTyping(true).build()} fixes single-object values
 *   ({@code RestaurantResponse}) but breaks the {@code List<MenuItemResponse>} value: Jackson can't
 *   attach a {@code @class} property to a bare JSON array, so the list itself is written with no
 *   type wrapper, and on read Jackson's type-id resolver chokes on the array's first element
 *   ({@code MismatchedInputException: Unexpected token (START_OBJECT), expected VALUE_STRING}) —
 *   the well-known "default typing doesn't play well with root-level collections" limitation.</li>
 * </ol>
 * A type-specific serializer sidesteps both: since the target type is always known per cache name,
 * no type hint needs to be embedded in the JSON at all (also removing any need to reason about
 * Jackson's polymorphic-deserialization attack surface — there's nothing polymorphic here).
 */
@Configuration
@EnableCaching
public class CacheConfig {

	public static final String RESTAURANT_CACHE = "restaurant-service:restaurant";
	public static final String MENU_CACHE = "restaurant-service:menu";

	private static final Duration TTL = Duration.ofMinutes(2);

	@Bean
	public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
		// GenericJackson2JsonRedisSerializer (used for the key serializer's sibling concern
		// elsewhere) is genuinely classic Jackson 2 (com.fasterxml.jackson.databind.ObjectMapper) —
		// spring-data-redis 4.1.1 hasn't been updated for Jackson 3 (tools.jackson), what this Boot
		// 4.1 app otherwise uses everywhere else. The default ObjectMapper has no JSR-310 module
		// registered, so it can't serialize java.time.Instant (confirmed empirically:
		// SerializationException). findAndRegisterModules() picks up jackson-datatype-jsr310:2.x off
		// the classpath (pulled in transitively by Boot's own Jackson-2-compatibility layer,
		// spring-boot-jackson2) without needing to reference the module class directly.
		ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
		JavaType menuListType = mapper.getTypeFactory().constructCollectionType(List.class, MenuItemResponse.class);

		RedisCacheConfiguration keysAndTtl = RedisCacheConfiguration.defaultCacheConfig()
				.entryTtl(TTL)
				// Keys are plain restaurant/menu ids -- a plain string serializer avoids the default
				// JDK serialization's opaque, non-human-readable key bytes, so `redis-cli keys '*'`
				// shows something legible while verifying this live.
				.serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()));

		RedisCacheConfiguration restaurantCacheConfig = keysAndTtl.serializeValuesWith(
				RedisSerializationContext.SerializationPair
						.fromSerializer(new Jackson2JsonRedisSerializer<>(mapper, RestaurantResponse.class)));

		RedisCacheConfiguration menuCacheConfig = keysAndTtl.serializeValuesWith(
				RedisSerializationContext.SerializationPair
						.fromSerializer(new Jackson2JsonRedisSerializer<List<MenuItemResponse>>(mapper, menuListType)));

		return builder -> builder
				.withCacheConfiguration(RESTAURANT_CACHE, restaurantCacheConfig)
				.withCacheConfiguration(MENU_CACHE, menuCacheConfig);
	}

}
