package food_delivery.Platform.restaurantservice.config;

import java.time.Duration;

import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;

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
 */
@Configuration
@EnableCaching
public class CacheConfig {

	public static final String RESTAURANT_CACHE = "restaurant-service:restaurant";
	public static final String MENU_CACHE = "restaurant-service:menu";

	private static final Duration TTL = Duration.ofMinutes(2);

	@Bean
	public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
		RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
				.entryTtl(TTL)
				// Keys are plain restaurant/menu ids -- a plain string serializer avoids the default
				// JDK serialization's opaque, non-human-readable key bytes, so `redis-cli keys '*'`
				// shows something legible while verifying this live.
				.serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
				// Values are RestaurantResponse/MenuItemResponse records -- plain JDK serialization
				// (RedisCacheConfiguration's own default) requires java.io.Serializable, which these
				// deliberately don't implement (confirmed empirically: IllegalStateException,
				// "Cannot serialize value ... without a serializer"). JSON keeps the same
				// human-readable-value benefit the string key serializer above is for, and needs no
				// change to the DTOs themselves.
				//
				// GenericJackson2JsonRedisSerializer is genuinely classic Jackson 2
				// (com.fasterxml.jackson.databind.ObjectMapper) — spring-data-redis 4.1.1 hasn't been
				// updated for Jackson 3 (tools.jackson), what this Boot 4.1 app otherwise uses
				// everywhere else. Its no-arg constructor's internal ObjectMapper has no JSR-310
				// module registered, so it can't serialize java.time.Instant (confirmed empirically:
				// SerializationException). findAndRegisterModules() picks up
				// jackson-datatype-jsr310:2.x off the classpath (pulled in transitively by Boot's own
				// Jackson-2-compatibility layer, spring-boot-jackson2) without needing to reference
				// the module class directly.
				.serializeValuesWith(RedisSerializationContext.SerializationPair
						.fromSerializer(new GenericJackson2JsonRedisSerializer(new ObjectMapper().findAndRegisterModules())));
		return builder -> builder
				.withCacheConfiguration(RESTAURANT_CACHE, base)
				.withCacheConfiguration(MENU_CACHE, base);
	}

}
