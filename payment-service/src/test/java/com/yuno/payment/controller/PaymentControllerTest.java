package com.yuno.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yuno.commons.enums.PaymentMethod;
import com.yuno.commons.enums.PaymentStatus;
import com.yuno.commons.enums.ProviderType;
import com.yuno.payment.dto.CreatePaymentRequest;
import com.yuno.payment.dto.PaymentResponse;
import com.yuno.payment.exception.GlobalExceptionHandler;
import com.yuno.payment.exception.PaymentNotFoundException;
import com.yuno.payment.service.PaymentService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentController HTTP Contract Tests")
class PaymentControllerTest {

    @Mock private PaymentService paymentService;
    @InjectMocks private PaymentController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private static final String KEY = "idem-test-key";
    private static final UUID ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private PaymentResponse pendingResponse() {
        return PaymentResponse.builder()
                .paymentId(ID).status(PaymentStatus.PENDING)
                .paymentMethod(PaymentMethod.CARD)
                .amount(new BigDecimal("100.00")).currency("USD").build();
    }

    // ── POST /payments ──────────────────────────────────────────────────────

    @Test @DisplayName("202 Accepted for valid CARD payment")
    void createCardPayment_validRequest_returns202() throws Exception {
        when(paymentService.createPayment(any(), eq(KEY))).thenReturn(pendingResponse());

        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                CreatePaymentRequest.builder()
                                        .paymentMethod(PaymentMethod.CARD)
                                        .amount(new BigDecimal("100.00"))
                                        .currency("USD").build())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test @DisplayName("202 Accepted for valid UPI payment")
    void createUpiPayment_validRequest_returns202() throws Exception {
        PaymentResponse upiResp = pendingResponse().toBuilder()
                .paymentMethod(PaymentMethod.UPI).currency("INR").build();
        when(paymentService.createPayment(any(), eq(KEY))).thenReturn(upiResp);

        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                CreatePaymentRequest.builder()
                                        .paymentMethod(PaymentMethod.UPI)
                                        .amount(new BigDecimal("5000.00"))
                                        .currency("INR").build())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.paymentMethod").value("UPI"));
    }

    @Test @DisplayName("400 when Idempotency-Key header is missing")
    void createPayment_missingIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CARD\",\"amount\":100,\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("400 when amount is null")
    void createPayment_nullAmount_returns400() throws Exception {
        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CARD\",\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("amount")));
    }

    @Test @DisplayName("400 when amount is zero")
    void createPayment_zeroAmount_returns400() throws Exception {
        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CARD\",\"amount\":0,\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("400 when currency is lowercase")
    void createPayment_lowercaseCurrency_returns400() throws Exception {
        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CARD\",\"amount\":100,\"currency\":\"usd\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("currency")));
    }

    @Test @DisplayName("400 when currency is not 3 characters")
    void createPayment_shortCurrency_returns400() throws Exception {
        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CARD\",\"amount\":100,\"currency\":\"US\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("400 when paymentMethod is missing")
    void createPayment_missingPaymentMethod_returns400() throws Exception {
        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100,\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("paymentMethod")));
    }

    @Test @DisplayName("400 when description exceeds 256 characters")
    void createPayment_longDescription_returns400() throws Exception {
        String longDesc = "x".repeat(257);
        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"CARD\",\"amount\":100,\"currency\":\"USD\",\"description\":\"" + longDesc + "\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /payments/{id} ──────────────────────────────────────────────────

    @Test @DisplayName("200 OK for existing payment")
    void getPayment_existingId_returns200() throws Exception {
        when(paymentService.getPayment(ID)).thenReturn(pendingResponse());

        mockMvc.perform(get("/payments/" + ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentId").value(ID.toString()));
    }

    @Test @DisplayName("404 for unknown payment ID")
    void getPayment_unknownId_returns404() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(paymentService.getPayment(unknown))
                .thenThrow(new PaymentNotFoundException("id", unknown.toString()));

        mockMvc.perform(get("/payments/" + unknown))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test @DisplayName("400 for malformed UUID path variable")
    void getPayment_malformedUuid_returns400() throws Exception {
        mockMvc.perform(get("/payments/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /payments?status= ───────────────────────────────────────────────

    @Test @DisplayName("200 with filtered list by status")
    void listPayments_withStatusFilter_returns200() throws Exception {
        when(paymentService.getPaymentsByStatus(PaymentStatus.SUCCESS))
                .thenReturn(List.of(pendingResponse().toBuilder().status(PaymentStatus.SUCCESS).build()));

        mockMvc.perform(get("/payments").param("status", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test @DisplayName("200 with empty list when no payments match")
    void listPayments_noMatches_returnsEmptyList() throws Exception {
        when(paymentService.getPaymentsByStatus(PaymentStatus.FAILED)).thenReturn(List.of());

        mockMvc.perform(get("/payments").param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    private PaymentResponse pendingResponse(String paymentId) {
        return PaymentResponse.builder()
                .paymentId(UUID.fromString(paymentId))
                .status(PaymentStatus.PENDING)
                .paymentMethod(PaymentMethod.UPI)
                .currency("INR")
                .amount(new BigDecimal("5000.00"))
                .build();
    }

}
