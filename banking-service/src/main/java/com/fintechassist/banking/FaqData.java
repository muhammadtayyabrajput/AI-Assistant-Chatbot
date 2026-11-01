package com.fintechassist.banking;

import com.fintechassist.banking.Models.Faq;

import java.util.List;

public final class FaqData {
    private FaqData() {}

    public static final List<Faq> FAQS = List.of(
            new Faq("faq-1", "Cards", "How do I block my card?",
                    "You can block a card instantly from the chat assistant, the mobile app (Cards > Freeze/Block) or by calling the 24/7 helpline at 0800-FINTECH. Blocking is immediate and a replacement card can be requested afterwards."),
            new Faq("faq-2", "Cards", "What is my card limit and how can I increase it?",
                    "Credit limits and daily ATM limits are shown on the Cards page. To request an increase, go to Cards > Request limit increase. Requests are reviewed within 2 business days based on your repayment history."),
            new Faq("faq-3", "Cards", "How do I get a replacement card?",
                    "If your card is lost, stolen or damaged, block it first, then request a replacement from Cards > Replace card. Delivery takes 5-7 business days and the first replacement is free."),
            new Faq("faq-4", "Cards", "How do I activate my new card?",
                    "Activate your new card in the app under Cards > Activate, or make your first PIN-based transaction at any ATM. Activation is instant."),
            new Faq("faq-5", "Cards", "How do I change my card PIN?",
                    "You can change your PIN at any FinTech ATM or in the app under Cards > Change PIN. Never share your PIN with anyone, including bank staff."),
            new Faq("faq-6", "Transactions", "Where is my transaction? It is pending.",
                    "Card payments appear as PENDING for up to 3 business days while the merchant settles. Pending amounts are already deducted from your available balance. If a transaction is still pending after 5 business days, contact support."),
            new Faq("faq-7", "Transactions", "How do I dispute a transaction?",
                    "To dispute an unrecognised or incorrect charge, open the transaction in the app and tap Report a problem, or contact support within 60 days of the transaction date. We will issue a provisional credit while we investigate."),
            new Faq("faq-8", "Transactions", "Why was my transaction declined?",
                    "Common reasons are insufficient balance, exceeding your daily limit, an expired or blocked card, an incorrect PIN, or a merchant restriction such as international transactions being disabled."),
            new Faq("faq-9", "Transactions", "How long do refunds take?",
                    "Merchant refunds typically reach your account within 5-10 business days after the merchant approves them."),
            new Faq("faq-10", "Transfers", "How do I transfer money to another account?",
                    "Go to Transfers > New transfer, enter the beneficiary account number and amount, and confirm with your OTP. Transfers between FinTech accounts are instant; other banks take up to 1 business day."),
            new Faq("faq-11", "Transfers", "What are the transfer limits and fees?",
                    "The daily online transfer limit is $5,000. Transfers between FinTech accounts are free; interbank transfers cost $0.50 per transaction."),
            new Faq("faq-12", "Account", "How do I check my account balance?",
                    "Your balance is displayed on the dashboard and can be requested from the chat assistant at any time. You can also use the app or any FinTech ATM."),
            new Faq("faq-13", "Account", "How do I reset my password?",
                    "On the login page, tap Forgot password and follow the instructions. A secure reset link will be sent to your registered email address. For your safety, we will never ask for your password over phone or chat."),
            new Faq("faq-14", "Account", "How do I update my phone number or email?",
                    "Go to Profile > Personal details. Changing contact details requires OTP verification sent to your current phone number."),
            new Faq("faq-15", "Account", "How can I download my account statement?",
                    "Statements for the last 24 months are available under Accounts > Statements as PDF. Monthly statements are also emailed on the 1st of each month."),
            new Faq("faq-16", "Security", "What should I do if I suspect fraud?",
                    "Block your card immediately using the chat assistant or app, then call the 24/7 helpline. Do not share OTPs, PINs or passwords with anyone. We will never ask for them."),
            new Faq("faq-17", "Security", "How do I enable international transactions?",
                    "Go to Cards > Settings > International usage and toggle it on. You can set it for a limited date range for extra safety when travelling."),
            new Faq("faq-18", "Fees", "What are the account and card fees?",
                    "There is no monthly fee on the standard savings account. Debit card annual fee is $10, credit card annual fee is $40 (waived with $2,000 yearly spend). ATM withdrawals at other banks cost $1."),
            new Faq("faq-19", "Loans", "How do I apply for a personal loan?",
                    "Go to Loans > Personal loan, check your pre-approved offer and submit the request. Approval usually takes 1-2 business days and funds are disbursed to your account instantly after approval."),
            new Faq("faq-20", "Support", "How can I contact customer support?",
                    "Call 0800-FINTECH (24/7), email support@fintechassist.example, or use this chat assistant. For complex issues the assistant can hand you over to a human agent during business hours (9am-6pm)."));
}
