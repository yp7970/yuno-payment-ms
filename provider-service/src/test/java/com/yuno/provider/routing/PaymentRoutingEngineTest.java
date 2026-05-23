package com.yuno.provider.routing;

import com.yuno.commons.enums.PaymentMethod;
import com.yuno.commons.enums.ProviderType;
import com.yuno.provider.provider.PaymentProviderConnector;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentRoutingEngine Tests")
class PaymentRoutingEngineTest {

    private PaymentRoutingEngine engine;
    private PaymentProviderConnector providerA;
    private PaymentProviderConnector providerB;

    @BeforeEach
    void setUp() {
        providerA = mock(PaymentProviderConnector.class);
        when(providerA.getProviderType()).thenReturn(ProviderType.PROVIDER_A);

        providerB = mock(PaymentProviderConnector.class);
        when(providerB.getProviderType()).thenReturn(ProviderType.PROVIDER_B);

        engine = new PaymentRoutingEngine(List.of(providerA, providerB));
    }

    @Test @DisplayName("CARD primary → Provider A")
    void card_primaryIsProviderA() {
        assertThat(engine.getPrimary(PaymentMethod.CARD).getProviderType())
                .isEqualTo(ProviderType.PROVIDER_A);
    }

    @Test @DisplayName("CARD failover → Provider B")
    void card_failoverIsProviderB() {
        assertThat(engine.getFailover(PaymentMethod.CARD).getProviderType())
                .isEqualTo(ProviderType.PROVIDER_B);
    }

    @Test @DisplayName("UPI primary → Provider B")
    void upi_primaryIsProviderB() {
        assertThat(engine.getPrimary(PaymentMethod.UPI).getProviderType())
                .isEqualTo(ProviderType.PROVIDER_B);
    }

    @Test @DisplayName("UPI failover → Provider A")
    void upi_failoverIsProviderA() {
        assertThat(engine.getFailover(PaymentMethod.UPI).getProviderType())
                .isEqualTo(ProviderType.PROVIDER_A);
    }

    @Test @DisplayName("Primary and failover are always different")
    void primaryAndFailover_alwaysDiffer() {
        for (PaymentMethod method : PaymentMethod.values()) {
            assertThat(engine.getPrimary(method).getProviderType())
                    .isNotEqualTo(engine.getFailover(method).getProviderType());
        }
    }

    @Test @DisplayName("CARD failover == UPI primary (symmetric routing)")
    void routingIsSymmetric() {
        assertThat(engine.getFailover(PaymentMethod.CARD).getProviderType())
                .isEqualTo(engine.getPrimary(PaymentMethod.UPI).getProviderType());
    }

    @Test @DisplayName("getPrimaryType returns enum correctly for CARD")
    void getPrimaryType_card_returnsProviderA() {
        assertThat(engine.getPrimaryType(PaymentMethod.CARD)).isEqualTo(ProviderType.PROVIDER_A);
    }

    @Test @DisplayName("getPrimaryType returns enum correctly for UPI")
    void getPrimaryType_upi_returnsProviderB() {
        assertThat(engine.getPrimaryType(PaymentMethod.UPI)).isEqualTo(ProviderType.PROVIDER_B);
    }
}
