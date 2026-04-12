package me.bill.fakePlayerPlugin.ai;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract interface for AI conversation providers.
 *
 * <p>Implementations must support streaming or blocking text generation
 * from a conversation history (user message + bot personality context).
 */
public interface AIProvider {

    /**
     * @return the display name of this provider (e.g. "OpenAI", "Groq", "Ollama")
     */
    String getName();

    /**
     * @return {@code true} if this provider is properly configured and ready to use
     */
    boolean isAvailable();

    /**
     * Generates a conversational response from the AI based on conversation history.
     *
     * @param messages conversation history (oldest first); each entry is [role, content]
     *                 where role is "system", "user", or "assistant"
     * @param botName  the bot's name (for context/logging)
     * @param personality optional personality prompt for the bot (can be null/blank)
     * @return a {@link CompletableFuture} that completes with the AI's response text,
     *         or fails with an exception if generation fails
     */
    CompletableFuture<String> generateResponse(
            List<ChatMessage> messages,
            String botName,
            String personality
    );

    /**
     * A single message in a conversation.
     *
     * @param role    "system", "user", or "assistant"
     * @param content the message text
     */
    record ChatMessage(String role, String content) {}
}

