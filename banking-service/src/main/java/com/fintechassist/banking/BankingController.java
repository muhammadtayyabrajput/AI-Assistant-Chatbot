package com.fintechassist.banking;

import com.fintechassist.banking.Models.Account;
import com.fintechassist.banking.Models.BlockResult;
import com.fintechassist.banking.Models.Card;
import com.fintechassist.banking.Models.Faq;
import com.fintechassist.banking.Models.Transaction;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api")
public class BankingController {
    private final BankingStore store;

    public BankingController(BankingStore store) {
        this.store = store;
    }

    @GetMapping("/transactions/{userId}")
    public List<Transaction> transactions(@PathVariable long userId,
                                          @RequestParam(defaultValue = "10") int limit,
                                          Authentication auth) {
        requireSelf(userId, auth);
        return store.transactions(userId, Math.min(limit, 50));
    }

    @GetMapping("/cards/{userId}")
    public List<Card> cards(@PathVariable long userId, Authentication auth) {
        requireSelf(userId, auth);
        return store.cards(userId);
    }

    @PostMapping("/cards/{userId}/{cardId}/block")
    public BlockResult block(@PathVariable long userId, @PathVariable String cardId, Authentication auth) {
        requireSelf(userId, auth);
        return store.blockCard(userId, cardId);
    }

    @GetMapping("/accounts/{userId}")
    public Account account(@PathVariable long userId,
                           @RequestParam(required = false) String name,
                           Authentication auth) {
        requireSelf(userId, auth);
        return store.account(userId, name);
    }

    @GetMapping("/faqs")
    public List<Faq> faqs() {
        return FaqData.FAQS;
    }

    /** A user may only touch their own data: the path userId must match the JWT subject. */
    private void requireSelf(long userId, Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Long id) || id != userId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only access your own data");
        }
    }
}
