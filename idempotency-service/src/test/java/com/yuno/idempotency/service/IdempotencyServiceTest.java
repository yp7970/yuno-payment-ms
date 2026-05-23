package com.yuno.idempotency.service;

import com.yuno.idempotency.dto.IdempotencyCheckResponse;
import com.yuno.idempotency.dto.IdempotencyStoreRequest;
import com.yuno.idempotency.mapper.IdempotencyMapper;
import com.yuno.idempotency.model.IdempotencyRecord;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyService Tests")
class IdempotencyServiceTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private IdempotencyMapper idempotencyMapper;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks private IdempotencyService service;

    private static final String KEY = "test-idempotency-key";
    private static final String BODY = "{\"paymentId\":\"abc\",\"status\":\"SUCCESS\"}";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ── check() ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("check: Redis HIT → returns cached response, no DB call")
    void check_redisHit_returnsCachedResponse_withoutDbCall() {
        when(valueOps.get("idempotency:" + KEY)).thenReturn(BODY);

        IdempotencyCheckResponse resp = service.check(KEY);

        assertThat(resp.isFound()).isTrue();
        assertThat(resp.getResponseBody()).isEqualTo(BODY);
        verifyNoInteractions(idempotencyMapper);
    }

    @Test
    @DisplayName("check: Redis MISS, DB HIT → returns cached response, backfills Redis")
    void check_redisMiss_dbHit_returnsCachedAndBackfillsRedis() {
        when(valueOps.get(anyString())).thenReturn(null);
        IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(KEY).responseBody(BODY).httpStatus(202)
                .expiresAt(LocalDateTime.now().plusHours(1)).build();
        when(idempotencyMapper.findByKey(KEY)).thenReturn(record);

        IdempotencyCheckResponse resp = service.check(KEY);

        assertThat(resp.isFound()).isTrue();
        assertThat(resp.getResponseBody()).isEqualTo(BODY);
        // Verify Redis backfill was called
        verify(valueOps, times(1)).set(eq("idempotency:" + KEY), eq(BODY), any());
    }

    @Test
    @DisplayName("check: Redis MISS, DB MISS → returns not found")
    void check_totalMiss_returnsNotFound() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(idempotencyMapper.findByKey(KEY)).thenReturn(null);

        IdempotencyCheckResponse resp = service.check(KEY);

        assertThat(resp.isFound()).isFalse();
        assertThat(resp.getResponseBody()).isNull();
    }

    @Test
    @DisplayName("check: Redis throws exception → gracefully falls back to DB")
    void check_redisThrows_fallsBackToDb() {
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis down"));
        when(idempotencyMapper.findByKey(KEY)).thenReturn(null);

        // Should not throw
        assertThatNoException().isThrownBy(() -> service.check(KEY));
        verify(idempotencyMapper).findByKey(KEY);
    }

    // ── store() ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("store: writes to Redis and DB when key does not exist")
    void store_newKey_writesToRedisAndDb() {
        when(idempotencyMapper.existsByKey(KEY)).thenReturn(false);
        doNothing().when(idempotencyMapper).insert(any());

        service.store(IdempotencyStoreRequest.builder()
                .idempotencyKey(KEY).responseBody(BODY).httpStatus(202).build());

        verify(valueOps).set(eq("idempotency:" + KEY), eq(BODY), any());
        verify(idempotencyMapper).insert(any(IdempotencyRecord.class));
    }

    @Test
    @DisplayName("store: Redis write fails → still writes to DB")
    void store_redisWriteFails_stillWritesToDb() {
        doThrow(new RuntimeException("Redis unavailable"))
                .when(valueOps).set(anyString(), anyString(), any());
        when(idempotencyMapper.existsByKey(KEY)).thenReturn(false);

        assertThatNoException().isThrownBy(() ->
                service.store(IdempotencyStoreRequest.builder()
                        .idempotencyKey(KEY).responseBody(BODY).httpStatus(202).build()));

        verify(idempotencyMapper).insert(any(IdempotencyRecord.class));
    }

    @Test
    @DisplayName("store: key already in DB → skips DB insert (no duplicate)")
    void store_existingDbKey_skipsInsert() {
        when(idempotencyMapper.existsByKey(KEY)).thenReturn(true);

        service.store(IdempotencyStoreRequest.builder()
                .idempotencyKey(KEY).responseBody(BODY).httpStatus(202).build());

        verify(idempotencyMapper, never()).insert(any());
    }

    // ── cleanupExpiredRecords() ─────────────────────────────────────────────

    @Test
    @DisplayName("cleanupExpiredRecords: calls deleteExpired with current time")
    void cleanupExpiredRecords_callsDeleteExpired() {
        when(idempotencyMapper.deleteExpired(any(LocalDateTime.class))).thenReturn(5);
        service.cleanupExpiredRecords();
        verify(idempotencyMapper).deleteExpired(any(LocalDateTime.class));
    }
}
