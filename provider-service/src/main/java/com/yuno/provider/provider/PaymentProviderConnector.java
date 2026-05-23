package com.yuno.provider.provider;

import com.yuno.commons.enums.ProviderType;
import com.yuno.commons.events.PaymentInitiatedEvent;

public interface PaymentProviderConnector {
    ProviderCallResponse process(PaymentInitiatedEvent event);
    ProviderType getProviderType();
}
