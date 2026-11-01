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

## Run locally

**Requirements:** Java 17+, Maven 3.9+, Node 20+, Docker Desktop, and an [OpenRouter](https://openrouter.ai/keys) API key.

1. **Create your environment file.** Copy `.env.example` to `.env` in the project root and put your key in it:
   ```
   OPENROUTER_API_KEY=sk-or-v1-...
   ```
   All three services read this file automatically. It is git-ignored.

2. **Start the databases** (MySQL on port 3307, PostgreSQL with pgvector on port 5434):
   ```bash
   docker compose up -d
   ```

3. **Start the three backend services**, each in its own terminal:
   ```bash
   cd auth-service    && mvn spring-boot:run   # http://localhost:8081
   cd banking-service && mvn spring-boot:run   # http://localhost:8082
   cd chat-service    && mvn spring-boot:run   # http://localhost:8083
   ```
   The first start of chat-service downloads the ~90 MB embedding model and then embeds the FAQs, so give it a minute or two. Wait for `Started ChatServiceApplication`.

4. **Start the frontend:**
   ```bash
   cd frontend
   npm install
   npm run dev                                  # http://localhost:3000
   ```

5. Open http://localhost:3000, create an account, and try:
   - "What was my last transaction?"
   - "What is my card limit?"
   - "Block my card"
   - "How do I dispute a charge?"

On Windows, `.\start-all.ps1` does steps 2 to 4 in separate windows (it uses `mvn` from your PATH, or the path in `$env:MVN_CMD`).

**Local notes**
- With no email server configured, the forgot-password page shows the reset link on screen. This only happens in development.
- Banking data is generated in memory, so it resets when banking-service restarts. Users, chats and FAQ embeddings are stored in the databases.
- If chat replies say the API key was rejected, check `.env` and restart chat-service. Settings are read at startup.
- Change the default `JWT_SECRET` before running anywhere other than your own machine.

## Tests

```bash
cd auth-service    && mvn test     # 50 tests
cd banking-service && mvn test     # 27 tests
cd chat-service    && mvn test     # 73 tests
cd frontend        && npm test     # 33 tests
```

The backend tests need no running databases or API key: they use in-memory H2 and mock the AI and vector store. They cover:
- **Authentication:** signup rules, one account per email, login lockout, password-reset flow (single use, expiry), JWT signing, tampering and expiry.
- **Authorization:** a user can never read or change another user's data, in either the banking or the chat service.
- **Attack cases:** SQL-injection strings, forged and unsigned tokens, oversized input, CORS from other origins, and rate limits.
- **AI safety:** a card can only be blocked after the customer was asked about that exact card and said yes, and a customer's name can't inject instructions into the prompt.

The frontend tests cover the streaming parser, API error handling, token-expiry detection and the password rules.
