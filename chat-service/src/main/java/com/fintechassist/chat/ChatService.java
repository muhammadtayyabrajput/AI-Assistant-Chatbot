package com.fintechassist.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static final String SYSTEM_PROMPT = """
            You are FintechAssist, a friendly and professional AI customer-support assistant for FinTech Bank.
            You are talking to the customer %s. Today's date is %s. All amounts are in USD.

            RULES
            - Only help with banking topics (accounts, cards, transactions, transfers, fees, security, support).
              Politely decline anything else.
            - For anything about the customer's own data (transactions, cards, limits, balance) you MUST use the
              provided tools. Never guess or invent figures.
            - For general bank policy questions, answer from the KNOWLEDGE BASE below. If the answer is not there,
              say you are not sure and suggest contacting support on 0800-FINTECH.
            - BLOCKING A CARD is irreversible. Never call blockCard on the first request. First call getCards, tell the
              customer exactly which card(s) you would block (type and last 4 digits) and ask for explicit confirmation.
              Call blockCard only after they confirm. If they have several cards and did not say which, ask.
            - Never reveal full card numbers, PINs, passwords or OTPs, and never ask for them.
            - Be concise. Format dates in a friendly way (e.g. "5th Oct"). Use short paragraphs or bullet lists.

            KNOWLEDGE BASE (retrieved FAQs, may be empty):
            %s
            """;

    private final ChatClient chatClient;
    private final ChatRepository chats;
    private final ChatMessageRepository messages;
    private final FaqRagService rag;
    private final RestClient banking;
    private final ObjectMapper mapper;
    private final int historyWindow;

    public ChatService(ChatClient.Builder builder, ChatRepository chats, ChatMessageRepository messages,
                       FaqRagService rag, ObjectMapper mapper,
                       @Value("${banking.url}") String bankingUrl,
                       @Value("${chat.history-window}") int historyWindow) {
        this.chatClient = builder.build();
        this.chats = chats;
        this.messages = messages;
        this.rag = rag;
        this.mapper = mapper;
        this.banking = RestClient.builder().baseUrl(bankingUrl).build();
        this.historyWindow = historyWindow;
    }

    public Flux<ServerSentEvent<String>> stream(JwtUser user, String chatId, String text) {
        // ---- blocking prep (request thread) ----
        Chat chat = (chatId == null || chatId.isBlank())
                ? chats.save(new Chat(user.userId(), title(text)))
                : chats.findByIdAndUserId(chatId, user.userId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found"));

        List<Message> history = loadHistory(chat.getId());
        messages.save(new ChatMessage(chat.getId(), "user", text));
        chat.touch();
        chats.save(chat);

        String knowledge = String.join("\n---\n", rag.search(text));
        if (knowledge.isBlank()) knowledge = "(no relevant FAQ found)";
        String system = SYSTEM_PROMPT.formatted(
                user.name() == null ? "(name unknown)" : user.name(), LocalDate.now(), knowledge);

        StringBuilder answer = new StringBuilder();
        Flux<ServerSentEvent<String>> tokens = chatClient.prompt()
                .system(system)
                .messages(history)
                .user(text)
                .tools(new BankingTools(banking, user))
                .stream().content()
                .doOnNext(answer::append)
                .map(t -> event("token", Map.of("t", t)))
                .doFinally(sig -> {
                    if ((sig == SignalType.ON_COMPLETE || sig == SignalType.CANCEL) && !answer.isEmpty()) {
                        messages.save(new ChatMessage(chat.getId(), "assistant", answer.toString()));
                    }
                })
                .onErrorResume(e -> {
                    log.error("Chat stream failed", e);
                    return Flux.just(event("error", Map.of("message", friendly(e))));
                });

        return Flux.concat(
                Flux.just(event("meta", Map.of("chatId", chat.getId(), "title", chat.getTitle()))),
                tokens,
                Flux.just(event("done", Map.of())));
    }

    private List<Message> loadHistory(String chatId) {
        List<ChatMessage> recent = new ArrayList<>(
                messages.findTop10ByChatIdOrderByIdDesc(chatId).stream().limit(historyWindow).toList());
        Collections.reverse(recent);
        while (!recent.isEmpty() && !"user".equals(recent.get(0).getRole())) recent.remove(0);
        List<Message> out = new ArrayList<>();
        for (ChatMessage m : recent) {
            out.add("user".equals(m.getRole()) ? new UserMessage(m.getContent()) : new AssistantMessage(m.getContent()));
        }
        return out;
    }

    private static String title(String text) {
        String t = text.strip().replaceAll("\\s+", " ");
        return t.length() > 40 ? t.substring(0, 40) + "…" : t;
    }

    private static String friendly(Throwable e) {
        String m = String.valueOf(e.getMessage()).toLowerCase();
        if (m.contains("401") || m.contains("authentication") || m.contains("x-api-key") || m.contains("api key")) {
            return "The AI service rejected the API key. Check OPENROUTER_API_KEY on the chat-service.";
        }
        if (m.contains("credit balance") || m.contains("402")) {
            return "Your OpenRouter account has no credits for this model. Add credits at openrouter.ai or pick a free model.";
        }
        if (m.contains("429") || m.contains("rate")) {
            return "The AI service is rate-limited right now. Please try again in a moment.";
        }
        return "Sorry, something went wrong while generating the answer. Please try again.";
    }

    private ServerSentEvent<String> event(String name, Object payload) {
        try {
            return ServerSentEvent.<String>builder(mapper.writeValueAsString(payload)).event(name).build();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
