package com.yuno.provider.mapper;

import com.yuno.provider.model.ProviderCall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * MyBatis mapper — all SQL in ProviderCallMapper.xml.
 * Records every provider call attempt for audit trails and analytics.
 */
@Mapper
public interface ProviderCallMapper {
    void insert(ProviderCall call);
    List<ProviderCall> findByPaymentId(@Param("paymentId") UUID paymentId);
}
