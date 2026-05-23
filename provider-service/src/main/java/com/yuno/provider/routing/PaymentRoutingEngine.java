package com.yuno.provider.routing;

import com.yuno.commons.enums.PaymentMethod;
import com.yuno.commons.enums.ProviderType;
import com.yuno.provider.provider.PaymentProviderConnector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Routing engine — maps PaymentMethod to primary and failover providers.
 *
 * Routing table (static, per spec):
 *   CARD → primary: PROVIDER_A  | failover: PROVIDER_B
 *   UPI  → primary: PROVIDER_B  | failover: PROVIDER_A
 *
 * Adding a new payment method or provider requires:
 *   1. Implement PaymentProviderConnector (Spring picks it up automatically)
 *   2. Add a row to ROUTING_TABLE
 *   Zero changes elsewhere (Open/Closed Principle).
 */
@Component
@Slf4j
public class PaymentRoutingEngine {

    private static final Map<PaymentMethod, RoutingRule> ROUTING_TABLE = Map.of(
            PaymentMethod.CARD, new RoutingRule(ProviderType.PROVIDER_A, ProviderType.PROVIDER_B),
            PaymentMethod.UPI,  new RoutingRule(ProviderType.PROVIDER_B, ProviderType.PROVIDER_A)
    );

    private final Map<ProviderType, PaymentProviderConnector> connectors;

    public PaymentRoutingEngine(List<PaymentProviderConnector> connectorList) {
        this.connectors = connectorList.stream()
                .collect(Collectors.toMap(PaymentProviderConnector::getProviderType, Function.identity()));
        log.info("Routing engine initialised with providers: {}", connectors.keySet());
    }

    public PaymentProviderConnector getPrimary(PaymentMethod method) {
        return connectors.get(getRule(method).primary());
    }

    public PaymentProviderConnector getFailover(PaymentMethod method) {
        return connectors.get(getRule(method).failover());
    }

    public ProviderType getPrimaryType(PaymentMethod method) {
        return getRule(method).primary();
    }

    public ProviderType getFailoverType(PaymentMethod method) {
        return getRule(method).failover();
    }

    private RoutingRule getRule(PaymentMethod method) {
        RoutingRule rule = ROUTING_TABLE.get(method);
        if (rule == null) {
            throw new IllegalArgumentException("No routing rule for payment method: " + method);
        }
        return rule;
    }

    private record RoutingRule(ProviderType primary, ProviderType failover) {}
}
