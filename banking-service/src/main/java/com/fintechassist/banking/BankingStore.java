package com.fintechassist.banking;

import com.fintechassist.banking.Models.Account;
import com.fintechassist.banking.Models.BlockResult;
import com.fintechassist.banking.Models.Card;
import com.fintechassist.banking.Models.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory mock bank. Data for each user is generated deterministically the first time it is requested,
 * so every new signup immediately gets a realistic-looking account, cards and transaction history.
 */
@Component
public class BankingStore {
    private record Merchant(String name, String category, int minAmt, int maxAmt) {}

    private static final List<Merchant> MERCHANTS = List.of(
            new Merchant("Daraz", "Shopping", 15, 180),
            new Merchant("Foodpanda", "Food", 8, 40),
            new Merchant("Uber", "Transport", 5, 30),
            new Merchant("Careem", "Transport", 4, 25),
            new Merchant("Netflix", "Entertainment", 10, 16),
            new Merchant("Amazon", "Shopping", 20, 250),
            new Merchant("K-Electric", "Utilities", 40, 120),
            new Merchant("Carrefour", "Groceries", 25, 140),
            new Merchant("Spotify", "Entertainment", 5, 12),
            new Merchant("Shell Fuel", "Transport", 30, 70),
            new Merchant("Starbucks", "Food", 4, 15));

    private static final class UserData {
        final Account account;
        final List<Card> cards;
        final List<Transaction> transactions;

        UserData(Account account, List<Card> cards, List<Transaction> transactions) {
            this.account = account;
            this.cards = cards;
            this.transactions = transactions;
        }
    }

    private final Map<Long, UserData> users = new ConcurrentHashMap<>();

    public Account account(long userId, String holderName) {
        return data(userId, holderName).account;
    }

    public List<Card> cards(long userId) {
        return List.copyOf(data(userId, null).cards);
    }

    public List<Transaction> transactions(long userId, int limit) {
        return data(userId, null).transactions.stream().limit(Math.max(1, limit)).toList();
    }

    public synchronized BlockResult blockCard(long userId, String cardId) {
        UserData d = data(userId, null);
        for (int i = 0; i < d.cards.size(); i++) {
            Card c = d.cards.get(i);
            if (c.id().equals(cardId)) {
                if ("BLOCKED".equals(c.status())) {
                    return new BlockResult(cardId, "BLOCKED", "Card ending " + c.last4() + " is already blocked.");
                }
                d.cards.set(i, new Card(c.id(), c.type(), c.network(), c.maskedNumber(), c.last4(), c.expiry(),
                        c.creditLimit(), c.availableLimit(), c.dailyAtmLimit(), "BLOCKED"));
                return new BlockResult(cardId, "BLOCKED", "Card ending " + c.last4() + " has been blocked.");
            }
        }
        return new BlockResult(cardId, "NOT_FOUND", "No card with id " + cardId + " found for this user.");
    }

    public Optional<Card> findCard(long userId, String cardId) {
        return cards(userId).stream().filter(c -> c.id().equals(cardId)).findFirst();
    }

    private UserData data(long userId, String holderName) {
        return users.computeIfAbsent(userId, id -> generate(id, holderName));
    }

    private UserData generate(long userId, String holderName) {
        Random rnd = new Random(userId * 7919L + 13);
        String debitLast4 = String.format("%04d", 1000 + rnd.nextInt(9000));
        String creditLast4 = String.format("%04d", 1000 + rnd.nextInt(9000));

        List<Card> cards = new ArrayList<>(List.of(
                new Card("card-debit-" + userId, "DEBIT", "VISA", "**** **** **** " + debitLast4, debitLast4,
                        "08/28", null, null, BigDecimal.valueOf(1000), "ACTIVE"),
                new Card("card-credit-" + userId, "CREDIT", "MASTERCARD", "**** **** **** " + creditLast4, creditLast4,
                        "03/29", BigDecimal.valueOf(5000), BigDecimal.valueOf(5000 - (500 + rnd.nextInt(2000))),
                        BigDecimal.valueOf(500), "ACTIVE")));

        List<Transaction> txs = new ArrayList<>();
        BigDecimal balance = BigDecimal.valueOf(2500 + rnd.nextInt(5000));
        LocalDateTime now = LocalDateTime.now().withNano(0);
        for (int i = 0; i < 18; i++) {
            Merchant m = MERCHANTS.get(rnd.nextInt(MERCHANTS.size()));
            BigDecimal amt = BigDecimal.valueOf(m.minAmt() + rnd.nextDouble() * (m.maxAmt() - m.minAmt()))
                    .setScale(2, RoundingMode.HALF_UP);
            LocalDateTime date = now.minusDays(i * 2L + rnd.nextInt(2)).minusHours(rnd.nextInt(12));
            boolean pending = i == 0 && rnd.nextBoolean();
            String last4 = rnd.nextInt(4) == 0 ? creditLast4 : debitLast4;
            txs.add(new Transaction("txn-" + userId + "-" + (1000 + i), m.name(), m.category(), amt,
                    "DEBIT", pending ? "PENDING" : "COMPLETED", date, last4));
        }
        txs.add(new Transaction("txn-" + userId + "-sal", "Salary - Acme Corp", "Income",
                BigDecimal.valueOf(3200), "CREDIT", "COMPLETED", now.minusDays(9), debitLast4));
        txs.sort(Comparator.comparing(Transaction::date).reversed());

        String accountNo = "PK" + String.format("%02d", 10 + rnd.nextInt(80)) + "FINT"
                + String.format("%016d", Math.abs(rnd.nextLong()) % 10_000_000_000_000_000L);
        return new UserData(new Account(accountNo, holderName == null ? "Account Holder" : holderName,
                balance, "USD"), cards, txs);
    }
}
