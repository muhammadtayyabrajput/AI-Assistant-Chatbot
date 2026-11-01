# FintechAssist AI

An AI customer-support assistant for a bank. Instead of answering "where is my transaction?", "what is my card limit?" or "block my card" with canned replies, it looks at the customer's real (mock) account data, finds the relevant bank policy, and can take the action itself, with the customer's confirmation.

## Features

**Assistant**
- Streaming chat (replies appear word by word, like ChatGPT).
- Answers from live account data: balance, recent transactions, cards and limits.
- Answers policy questions (disputes, fees, transfers, security) from a bank FAQ knowledge base.
- Can act: blocks a card, but only after asking which card and getting an explicit "yes".
- Remembers the conversation; every chat is saved and listed in the sidebar, grouped by date.

**Customer dashboard**
- Balance, card visuals (a blocked card is stamped BLOCKED), spending by category, recent activity.
- Refreshes automatically after each reply, so a blocked card shows up immediately.

**Accounts and security**
- Sign up and log in with JWT. One account per email address (case-insensitive).
- Password rules, checked in the browser and again on the server: starts with a capital letter, has a number, has a special character.
- Forgot / reset password with single-use links that expire after 30 minutes.
- Each browser tab has its own login. Logging out clears everything, signs out the user's other tabs, and tokens expire automatically.

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | Next.js, React, Tailwind CSS |
| Backend | Spring Boot 3 (Java 17), Spring Security, Spring Data JPA, three services |
| AI | Spring AI, LLM via OpenRouter (default `anthropic/claude-haiku-4.5`) |
| Embeddings | all-MiniLM-L6-v2 running locally in the JVM (ONNX), no external API |
| Vector search | PostgreSQL with pgvector |
| Data | MySQL (users, chats, messages) |

## Architecture

```
                    ┌──────────────────────┐
                    │  Next.js frontend    │
                    └─────────┬────────────┘
        ┌─────────────────────┼──────────────────────┐
        ▼                     ▼                      ▼
┌───────────────┐   ┌───────────────────┐   ┌─────────────────────────┐
│ auth-service  │   │ banking-service   │   │ chat-service            │
│ :8081         │   │ :8082             │   │ :8083                   │
│ signup, login │◄──┤ accounts, cards,  │◄──┤ LLM + RAG + tools + SSE │
│ password reset│   │ transactions, FAQs│   │ chat history            │
└──────┬────────┘   └───────────────────┘   └───────┬─────────┬───────┘
       ▼              (mock data, in memory)         ▼         ▼
     MySQL                                         MySQL    pgvector
   (auth_db)                                     (chat_db)  (FAQ embeddings)
```

| Service | Responsibility | Main endpoints |
|---|---|---|
| **auth-service** | Accounts and JWT issuing | `POST /api/auth/signup`, `/login`, `/forgot-password`, `/reset-password` |
| **banking-service** | Mock bank: generates realistic data per user on first access | `GET /api/accounts/{id}`, `/api/cards/{id}`, `/api/transactions/{id}`, `POST /api/cards/{id}/{cardId}/block`, `GET /api/faqs` |
| **chat-service** | The AI brain and chat storage | `POST /api/chat` (SSE stream), `GET /api/chats`, `GET /api/chats/{id}/messages`, `DELETE /api/chats/{id}` |

The services share only a JWT secret. Each one validates the token itself, so no service has to call auth-service at request time.

## How a message works

1. The browser sends the question to `POST /api/chat` with the user's JWT. The reply comes back as a stream of Server-Sent Events.
2. **Retrieval (RAG):** the question is turned into a vector locally and compared with the embedded FAQs in pgvector. The 3 closest FAQs are added to the prompt.
3. **Prompt:** the model receives the system rules (banking topics only, never invent figures, confirm before blocking), the retrieved FAQs, the last 10 messages, and the available tools.
4. **Tool calling:** when the answer needs the customer's data, the model calls a tool instead of guessing:

   | Tool | What it does |
   |---|---|
   | `getRecentTransactions` | Latest transactions, newest first |
   | `getCards` | Cards with limits and status |
   | `getAccountBalance` | Account number and balance |
   | `blockCard` | Blocks one card, only after the customer confirms |

   Tools call banking-service with the customer's own token.
5. The model's words stream to the screen as they are generated. When the stream ends, the question and answer are saved to MySQL.

Example: *"What was my last transaction?"* → the model calls `getRecentTransactions(1)` → *"Your last transaction was $22.52 at Careem on 19th Oct..."*

Example: *"Block my card"* → `getCards` → the assistant lists the debit and credit cards and asks which one → the customer says "the debit card" → `blockCard` → the card turns BLOCKED on the dashboard.

## Security design

- **The model never chooses whose data it sees.** The user id comes from the verified JWT, never from the model or the request body, so a prompt can't make it read another customer's account.
- banking-service rejects any request where the user id in the URL doesn't match the token (403).
- Card numbers are always masked; the assistant is told never to ask for or reveal PINs, passwords or OTPs.
- Passwords are hashed with BCrypt. Reset tokens are random, stored only as a SHA-256 hash, single-use and time-limited.
- Password-reset responses are identical for known and unknown emails, so they can't be used to discover who has an account.
- CORS only allows the configured frontend origin.

## Project structure

```
auth-service/      Spring Boot: users, JWT, password reset
banking-service/   Spring Boot: mock accounts, cards, transactions, FAQ list
chat-service/      Spring Boot: LLM client, RAG, tools, streaming, chat history
frontend/          Next.js app (login, signup, forgot/reset password, chat + dashboard)
infra/             Database init script, reverse-proxy config
```
