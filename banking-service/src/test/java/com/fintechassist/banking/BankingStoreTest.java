package com.fintechassist.banking;

import com.fintechassist.banking.Models.BlockResult;
import com.fintechassist.banking.Models.Card;
import com.fintechassist.banking.Models.Transaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BankingStoreTest {
    private final BankingStore store = new BankingStore();

    @Test
    void sameUserAlwaysGetsTheSameCards() {
        assertEquals(store.cards(10).get(0).last4(), new BankingStore().cards(10).get(0).last4());
    }

    @Test
    void differentUsersGetDifferentData() {
        assertNotEquals(store.cards(10).get(0).last4(), store.cards(11).get(0).last4());
    }

    @Test
    void everyUserGetsOneDebitAndOneCreditCard() {
        List<Card> cards = store.cards(1);
        assertEquals(2, cards.size());
        assertEquals("DEBIT", cards.get(0).type());
        assertEquals("CREDIT", cards.get(1).type());
        assertNull(cards.get(0).creditLimit());
        assertTrue(cards.get(1).availableLimit().compareTo(cards.get(1).creditLimit()) <= 0);
    }

    @Test
    void transactionsAreNewestFirstAndRespectTheLimit() {
        List<Transaction> all = store.transactions(1, 50);
        for (int i = 1; i < all.size(); i++) {
            assertFalse(all.get(i).date().isAfter(all.get(i - 1).date()));
        }
        assertEquals(3, store.transactions(1, 3).size());
        assertEquals(1, store.transactions(1, 0).size());
        assertTrue(all.stream().noneMatch(t -> t.date().isAfter(LocalDateTime.now())));
    }

    @Test
    void amountsAreAlwaysPositive() {
        assertTrue(store.transactions(2, 50).stream().allMatch(t -> t.amount().compareTo(BigDecimal.ZERO) > 0));
    }

    @Test
    void blockCardUpdatesStatusAndIsIdempotent() {
        String id = store.cards(3).get(0).id();
        assertEquals("BLOCKED", store.blockCard(3, id).status());
        assertEquals("BLOCKED", store.cards(3).get(0).status());
        BlockResult again = store.blockCard(3, id);
        assertTrue(again.message().contains("already blocked"));
        assertEquals("ACTIVE", store.cards(3).get(1).status());
    }

    @Test
    void blockCardRejectsCardsOwnedBySomeoneElse() {
        String othersCard = store.cards(5).get(0).id();
        assertEquals("NOT_FOUND", store.blockCard(4, othersCard).status());
        assertEquals("ACTIVE", store.cards(5).get(0).status());
    }

    @Test
    void blockCardRejectsUnknownIds() {
        assertEquals("NOT_FOUND", store.blockCard(1, "../../etc/passwd").status());
        assertEquals("NOT_FOUND", store.blockCard(1, "").status());
    }

    @Test
    void accountHolderNameIsUpdatedEvenIfCardsWereLoadedFirst() {
        store.cards(20);
        assertEquals("Account Holder", store.account(20, null).holderName());
        assertEquals("Sara Ahmed", store.account(20, "Sara Ahmed").holderName());
        assertEquals("Sara Ahmed", store.account(20, null).holderName());
    }

    @Test
    void concurrentBlocksDoNotCorruptState() throws InterruptedException {
        String id = store.cards(30).get(0).id();
        Thread[] threads = new Thread[8];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> store.blockCard(30, id));
            threads[i].start();
        }
        for (Thread t : threads) t.join();
        assertEquals(2, store.cards(30).size());
        assertEquals("BLOCKED", store.cards(30).get(0).status());
    }
}
