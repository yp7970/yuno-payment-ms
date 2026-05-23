package com.yuno.idempotency.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuno.idempotency.dto.IdempotencyCheckResponse;
import com.yuno.idempotency.dto.IdempotencyStoreRequest;
import com.yuno.idempotency.service.IdempotencyService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyController Tests")
class IdempotencyControllerTest {

    @Mock private IdempotencyService idempotencyService;
    @InjectMocks private IdempotencyController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();
    private static final String KEY = "test-key-abc";
    private static final String BODY = "{\"paymentId\":\"xyz\",\"status\":\"SUCCESS\"}";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test @DisplayName("GET /idempotency/{key}: 200 when record found")
    void check_found_returns200() throws Exception {
        when(idempotencyService.check(KEY)).thenReturn(
                IdempotencyCheckResponse.builder().found(true).responseBody(BODY).httpStatus(202).build());

        mockMvc.perform(get("/idempotency/" + KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.found").value(true))
                .andExpect(jsonPath("$.data.responseBody").value(BODY));
    }

    @Test @DisplayName("GET /idempotency/{key}: 404 when record not found")
    void check_notFound_returns404() throws Exception {
        when(idempotencyService.check(KEY)).thenReturn(
                IdempotencyCheckResponse.builder().found(false).build());

        mockMvc.perform(get("/idempotency/" + KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test @DisplayName("POST /idempotency: 201 Created for valid store request")
    void store_validRequest_returns201() throws Exception {
        IdempotencyStoreRequest req = IdempotencyStoreRequest.builder()
                .idempotencyKey(KEY).responseBody(BODY).httpStatus(202).build();
        doNothing().when(idempotencyService).store(any());

        mockMvc.perform(post("/idempotency")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test @DisplayName("POST /idempotency: 400 when idempotencyKey is blank")
    void store_blankKey_returns400() throws Exception {
        mockMvc.perform(post("/idempotency")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"\",\"responseBody\":\"test\",\"httpStatus\":202}"))
                .andExpect(status().isBadRequest());
    }
}
