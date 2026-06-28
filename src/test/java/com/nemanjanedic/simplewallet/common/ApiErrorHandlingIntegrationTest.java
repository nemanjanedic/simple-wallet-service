package com.nemanjanedic.simplewallet.common;

import com.nemanjanedic.simplewallet.account.AccountService;
import com.nemanjanedic.simplewallet.account.dto.AccountResponse;
import com.nemanjanedic.simplewallet.account.dto.CreateAccountRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiErrorHandlingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accountService;

    @Test
    void shouldReturnBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{initialBalance:100.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Malformed JSON request")))
                .andExpect(jsonPath("$.path", is("/api/accounts")))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void shouldReturnBadRequestForValidationError() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initialBalance\":-1.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Request validation failed")))
                .andExpect(jsonPath("$.path", is("/api/accounts")))
                .andExpect(jsonPath("$.details", hasItem("initialBalance: Initial balance cannot be negative")));
    }

    @Test
    void shouldReturnNotFoundForMissingAccountBalance() throws Exception {
        mockMvc.perform(get("/api/accounts/999999/balance"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", is("Account not found: 999999")))
                .andExpect(jsonPath("$.path", is("/api/accounts/999999/balance")))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void shouldReturnConflictForInsufficientFunds() throws Exception {
        AccountResponse account = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("10.00"))
        );

        mockMvc.perform(post("/api/accounts/{accountId}/withdraw", account.accountId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":20.00,\"description\":\"Too much\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", is("Account does not have enough funds: " + account.accountId())))
                .andExpect(jsonPath("$.path", is("/api/accounts/" + account.accountId() + "/withdraw")))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void shouldReturnBadRequestForSameAccountTransfer() throws Exception {
        AccountResponse account = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("100.00"))
        );

        String requestBody = """
                {
                  "fromAccountId": %d,
                  "toAccountId": %d,
                  "amount": 10.00,
                  "description": "Same account"
                }
                """.formatted(account.accountId(), account.accountId());

        mockMvc.perform(post("/api/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Cannot transfer funds to the same account")))
                .andExpect(jsonPath("$.path", is("/api/transfers")))
                .andExpect(jsonPath("$.details").isArray());
    }
}
