package com.example.wallet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransferFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void transferMovesMoneyEndToEnd() throws Exception {
        String a = extractAccountId(mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initialBalance\":100}"))
                .andExpect(status().isCreated())
                .andReturn());

        String b = extractAccountId(mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                """
                                {"transactionId":"int-tx-1","fromAccountId":"%s","toAccountId":"%s","amount":30}
                                """,
                                a, b)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromBalanceAfter").value(70))
                .andExpect(jsonPath("$.toBalanceAfter").value(30))
                .andExpect(jsonPath("$.idempotentReplay").value(false));

        mockMvc.perform(post("/transfer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                """
                                {"transactionId":"int-tx-1","fromAccountId":"%s","toAccountId":"%s","amount":30}
                                """,
                                a, b)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(true));

        mockMvc.perform(post("/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("{\"accountId\":\"%s\",\"amount\":0.01}", a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newBalance").value(70.01));
    }

    private String extractAccountId(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("accountId").asText();
    }
}
