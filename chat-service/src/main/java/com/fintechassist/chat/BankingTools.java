package com.fintechassist.chat;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Tools Claude can call. One instance is created per request and is bound to the authenticated user:
 * the userId always comes from the JWT, never from the model, so the model cannot touch another user's data.
 */
public class BankingTools {
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private final RestClient rest;
    private final JwtUser user;

    public BankingTools(RestClient rest, JwtUser user) {
        this.rest = rest;
        this.user = user;
    }

    @Tool(description = "Get the customer's most recent transactions, newest first. Each has merchant, category, amount (USD), type (DEBIT/CREDIT), status (COMPLETED/PENDING), date and the last 4 digits of the card used.")
    public Object getRecentTransactions(
            @ToolParam(description = "How many transactions to return (1-20). Use 5 if unsure.") int limit) {
        return call(() -> rest.get()
                .uri("/api/transactions/{id}?limit={l}", user.userId(), Math.max(1, Math.min(limit, 20)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
                .retrieve().body(LIST));
    }

    @Tool(description = "Get all of the customer's cards with id, type (DEBIT/CREDIT), masked number, expiry, credit limit, available limit, daily ATM limit and status (ACTIVE/BLOCKED).")
    public Object getCards() {
        return call(() -> rest.get()
                .uri("/api/cards/{id}", user.userId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
                .retrieve().body(LIST));
    }

    @Tool(description = "Get the customer's account number and current balance.")
    public Object getAccountBalance() {
        return call(() -> rest.get()
                .uri("/api/accounts/{id}?name={n}", user.userId(), user.name() == null ? "" : user.name())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
                .retrieve().body(MAP));
    }

    @Tool(description = "Permanently block one of the customer's cards. IMPORTANT: only call this AFTER the customer has explicitly confirmed (for example answered 'yes') which specific card to block. Call getCards first to get the card id.")
    public Object blockCard(@ToolParam(description = "The card id exactly as returned by getCards") String cardId) {
        return call(() -> rest.post()
                .uri("/api/cards/{u}/{c}/block", user.userId(), cardId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
                .retrieve().body(MAP));
    }

    private Object call(java.util.function.Supplier<Object> s) {
        try {
            return s.get();
        } catch (Exception e) {
            return Map.of("error", "Banking service request failed: " + e.getMessage());
        }
    }
}
