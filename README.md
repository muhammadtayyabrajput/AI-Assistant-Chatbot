# FintechAssist AI

AI customer-support chatbot for a bank. Answers from real (mock) account data, retrieves bank policy from a
vector database (RAG), and can take actions such as blocking a card (tool calling). Powered by Claude.

```
Next.js :3000 ──► auth-service    :8081  JWT signup/login            (MySQL auth_db)
              ├─► banking-service :8082  mock accounts/cards/txns/FAQs (in-memory)
              └─► chat-service    :8083  Claude + RAG + tools + SSE    (MySQL chat_db, pgvector)
```

## How a message flows
1. Browser POSTs to `/api/chat` with the JWT; response is a Server-Sent Events stream.
2. chat-service embeds the question **locally** (all-MiniLM-L6-v2 ONNX, no API key/cost) and pulls the top FAQs from pgvector.
3. Claude (`claude-haiku-4-5`) gets: system prompt + FAQ context + last 10 messages + tools.
4. If it needs the user's data it calls a tool (`getRecentTransactions`, `getCards`, `getAccountBalance`, `blockCard`).
   Tools call banking-service **as the user** - the userId comes from the JWT, never from the model.
5. `blockCard` is only allowed after the user explicitly confirms (enforced by the system prompt).
6. Tokens stream to the UI; both messages are saved in MySQL and shown in the sidebar.

## Run it
Requirements: Java 17+, Maven, Node 20+, Docker Desktop.

1. Get an API key at https://openrouter.ai/keys (add a few dollars of credit, or use a free model;
   set OPENROUTER_MODEL).
2. ```powershell
   $env:OPENROUTER_API_KEY = "sk-or-v1-..."
   .\start-all.ps1
   ```
   (or start each piece manually: `docker compose up -d`, `mvn spring-boot:run` in each service, `npm run dev` in `frontend`)
3. Open http://localhost:3000, sign up, and try:
   - "What was my last transaction?"
   - "What is my card limit?"
   - "Block my card" (the bot asks which card and to confirm first)
   - "How do I dispute a charge?" (answered from the FAQ vector store)

Ports: MySQL `3307`, pgvector `5434`. First start of chat-service downloads the ~90 MB embedding model.

## Config
| Env var | Default | Used by |
|---|---|---|
| `OPENROUTER_API_KEY` | - | chat-service |
| `OPENROUTER_MODEL` | `anthropic/claude-haiku-4.5` | chat-service |
| `JWT_SECRET` | dev value (>=32 chars) | all services - **change for anything real** |
