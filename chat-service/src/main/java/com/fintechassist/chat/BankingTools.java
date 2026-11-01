package com.fintechassist.chat;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class BankingTools {
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};

    private static final Pattern YES = Pattern.compile(
            "^\\s*(yes|yep|yeah|y|confirm|confirmed|ok|okay|sure|proceed|go ahead|do it|please do|haan|ji)\\b.*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ASKED_TO_CONFIRM = Pattern.compile(
            "confirm|are you sure|proceed|go ahead|shall i|should i", Pattern.CASE_INSENSITIVE);
    private static final int MAX_CONFIRMATION_LENGTH = 80;

    private final RestClient rest;
    private final JwtUser user;
    private final String userMessage;
    private final String previousAssistantMessage;

    public BankingTools(RestClient rest, JwtUser user, String userMessage, String previousAssistantMessage) {
        this.rest = rest;
        this.user = user;
        this.userMessage = userMessage == null ? "" : userMessage;
        this.previousAssistantMessage = previousAssistantMessage == null ? "" : previousAssistantMessage;
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
        return call(() -> fetchCards());
    }

    @Tool(description = "Get the customer's account number and current balance.")
    public Object getAccountBalance() {
        return call(() -> rest.get()
                .uri("/api/accounts/{id}?name={n}", user.userId(), user.name() == null ? "" : user.name())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
                .retrieve().body(MAP));
    }

    @Tool(description = "Permanently block one of the customer's cards. Only call this after you asked the customer to confirm blocking that specific card (mention its last 4 digits) and they replied yes. Call getCards first to get the card id.")
    public Object blockCard(@ToolParam(description = "The card id exactly as returned by getCards") String cardId) {
        if (!customerConfirmed()) {
            return Map.of("error", "NOT_CONFIRMED: the customer has not confirmed yet. Ask them to confirm blocking this "
                    + "specific card (mention its last 4 digits) and wait for a yes before calling blockCard again.");
        }
        return call(() -> {
            List<Map<String, Object>> cards = fetchCards();
            Map<String, Object> card = cards.stream().filter(c -> cardId.equals(c.get("id"))).findFirst().orElse(null);
            if (card != null && !previousAssistantMessage.contains(String.valueOf(card.get("last4")))) {
                return Map.of("error", "CARD_NOT_CONFIRMED: the customer was not asked about the card ending "
                        + card.get("last4") + ". Ask them to confirm that card first.");
            }
            return rest.post()
                    .uri("/api/cards/{u}/{c}/block", user.userId(), cardId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
                    .retrieve().body(MAP);
        });
    }

    boolean customerConfirmed() {
        return userMessage.length() <= MAX_CONFIRMATION_LENGTH
                && YES.matcher(userMessage).matches()
                && ASKED_TO_CONFIRM.matcher(previousAssistantMessage).find();
    }

    private List<Map<String, Object>> fetchCards() {
        return rest.get()
                .uri("/api/cards/{id}", user.userId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
                .retrieve().body(LIST);
    }

    private Object call(Supplier<Object> s) {
        try {
            return s.get();
        } catch (Exception e) {
            return Map.of("error", "Banking service request failed: " + e.getMessage());
        }
    }
}
