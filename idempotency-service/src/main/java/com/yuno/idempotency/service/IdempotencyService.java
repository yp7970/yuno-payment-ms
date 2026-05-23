package com.yuno.idempotency.service;

import com.yuno.idempotency.dto.IdempotencyCheckResponse;
import com.yuno.idempotency.dto.IdempotencyStoreRequest;
import com.yuno.idempotency.mapper.IdempotencyMapper;
import com.yuno.idempotency.model.IdempotencyRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Idempotency service — two-tier storage for at-most-once payment processing.
 *
 * Lookup order:
 *   1. Redis  → sub-millisecond, 24hr TTL
 *   2. MyBatis DB SELECT → if Redis miss (e.g. restart/eviction)
 *   3. Cache MISS → allow new payment
 *
 * Write order (on store()):
 *   1. Redis SET (with TTL)
 *   2. MyBatis INSERT (ON CONFLICT DO NOTHING for concurrent requests)
 *
 * Why both?
 *   Redis provides speed. DB provides durability.
 *   A payment system must never lose idempotency guarantees
 *   due to a cache restart — the DB fallback covers that gap.
 *
 * MyBatis XML query highlights:
 *   - findByKey includes expires_at > CURRENT_TIMESTAMP — DB handles TTL
 *   - insert uses ON CONFLICT DO NOTHING — safe for concurrent duplicates
 *   - deleteExpired scheduled cleanup keeps the table lean
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private static final String REDIS_PREFIX  = "idempotency:";
    private static final Duration REDIS_TTL   = Duration.ofHours(24);

    private final RedisTemplate<String, String> redisTemplate;
    private final IdempotencyMapper idempotencyMapper;

    /**
     * Check if a response already exists for this idempotency key.
     * Redis first, DB fallback, then MISS.
     */
    public IdempotencyCheckResponse check(String idempotencyKey) {
        // ── Tier 1: Redis (fast path) ────────────────────────────────────
        try {
            String cached = redisTemplate.opsForValue().get(redisKey(idempotencyKey));
            if (cached != null) {
                log.info("Idempotency HIT (Redis) key={}", idempotencyKey);
                return IdempotencyCheckResponse.builder()
                        .found(true).responseBody(cached).httpStatus(202).build();
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for check key={}: {}", idempotencyKey, e.getMessage());
        }

        // ── Tier 2: DB fallback (MyBatis SELECT) ─────────────────────────
        // findByKey query includes expires_at > CURRENT_TIMESTAMP check
        IdempotencyRecord record = idempotencyMapper.findByKey(idempotencyKey);
        if (record != null) {
            log.info("Idempotency HIT (DB fallback) key={}", idempotencyKey);
            // Backfill Redis to restore fast-path on next request
            backfillRedis(idempotencyKey, record.getResponseBody());
            return IdempotencyCheckResponse.builder()
                    .found(true)
                    .responseBody(record.getResponseBody())
                    .httpStatus(record.getHttpStatus())
                    .build();
        }

        log.info("Idempotency MISS key={}", idempotencyKey);
        return IdempotencyCheckResponse.builder().found(false).build();
    }

    /**
     * Store a payment response for future duplicate requests.
     * Writes to Redis (fast) and DB (durable) in sequence.
     */
    public void store(IdempotencyStoreRequest request) {
        // ── Write to Redis ────────────────────────────────────────────────
        try {
            redisTemplate.opsForValue().set(
                    redisKey(request.getIdempotencyKey()),
                    request.getResponseBody(),
                    REDIS_TTL
            );
            log.debug("Idempotency stored in Redis key={}", request.getIdempotencyKey());
        } catch (Exception e) {
            log.warn("Redis write failed for key={}: {}", request.getIdempotencyKey(), e.getMessage());
        }

        // ── Write to DB via MyBatis INSERT ────────────────────────────────
        // ON CONFLICT DO NOTHING in SQL handles concurrent duplicate requests safely
        if (!idempotencyMapper.existsByKey(request.getIdempotencyKey())) {
            IdempotencyRecord record = IdempotencyRecord.builder()
                    .idempotencyKey(request.getIdempotencyKey())
                    .responseBody(request.getResponseBody())
                    .httpStatus(request.getHttpStatus())
                    .expiresAt(LocalDateTime.now().plus(REDIS_TTL))
                    .build();
            idempotencyMapper.insert(record);
            log.debug("Idempotency stored in DB key={}", request.getIdempotencyKey());
        }
    }

    /**
     * Scheduled cleanup of expired DB records — runs daily at 02:00.
     * Redis handles its own TTL-based expiry automatically.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupExpiredRecords() {
        int deleted = idempotencyMapper.deleteExpired(LocalDateTime.now());
        log.info("Idempotency cleanup: {} expired records removed", deleted);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private String redisKey(String key) {
        return REDIS_PREFIX + key;
    }

    private void backfillRedis(String key, String value) {
        try {
            redisTemplate.opsForValue().set(redisKey(key), value, REDIS_TTL);
        } catch (Exception e) {
            log.warn("Redis backfill failed for key={}: {}", key, e.getMessage());
        }
    }
}
