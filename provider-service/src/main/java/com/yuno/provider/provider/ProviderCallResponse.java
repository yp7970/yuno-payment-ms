package com.yuno.provider.provider;

import com.yuno.commons.enums.ProviderType;
import com.yuno.commons.events.PaymentInitiatedEvent;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProviderCallResponse {
    private boolean success;
    private String transactionId;
    private String errorMessage;
    private ProviderType provider;
    private long processingTimeMs;

    public static ProviderCallResponse success(ProviderType p, String txnId, long ms) {
        return ProviderCallResponse.builder().success(true).provider(p).transactionId(txnId).processingTimeMs(ms).build();
    }
    public static ProviderCallResponse failure(ProviderType p, String error, long ms) {
        return ProviderCallResponse.builder().success(false).provider(p).errorMessage(error).processingTimeMs(ms).build();
    }
}
