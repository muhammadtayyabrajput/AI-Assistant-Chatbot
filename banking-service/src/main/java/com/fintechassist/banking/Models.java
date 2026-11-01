package com.fintechassist.banking;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class Models {
    private Models() {}

    public record Transaction(String id, String merchant, String category, BigDecimal amount,
                              String type, String status, LocalDateTime date, String cardLast4) {}

    public record Card(String id, String type, String network, String maskedNumber, String last4,
                       String expiry, BigDecimal creditLimit, BigDecimal availableLimit,
                       BigDecimal dailyAtmLimit, String status) {}

    public record Account(String accountNumber, String holderName, BigDecimal balance, String currency) {}

    public record Faq(String id, String category, String question, String answer) {}

    public record BlockResult(String cardId, String status, String message) {}
}
