package com.yuno.idempotency.mapper;

import com.yuno.idempotency.model.IdempotencyRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * MyBatis mapper — all SQL in IdempotencyMapper.xml.
 *
 * Why MyBatis here instead of Spring Data Redis alone?
 * Redis is the fast path (sub-ms). The DB is the durable fallback.
 * After a Redis restart or eviction, the DB record ensures
 * idempotency is never lost — critical for a payments system.
 */
@Mapper
public interface IdempotencyMapper {
    IdempotencyRecord findByKey(@Param("idempotencyKey") String idempotencyKey);
    void insert(IdempotencyRecord record);
    boolean existsByKey(@Param("idempotencyKey") String idempotencyKey);
    int deleteExpired(@Param("now") LocalDateTime now);
}
