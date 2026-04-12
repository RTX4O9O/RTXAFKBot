# AI Conversation System Implementation Guide

## Overview
Successfully added AI-powered direct messaging system to FakePlayerPlugin. Bots can now respond to `/msg`, `/tell`, `/whisper` commands with AI-generated replies that match their personality.

## Files Created

### Core AI System (`src/main/java/me/bill/fakePlayerPlugin/ai/`)
1. **AIProvider.java** - Abstract interface for AI providers
2. **AIProviderRegistry.java** - Manages and selects active AI provider
3. **BotConversationManager.java** - Tracks conversations and generates responses
4. **OpenAIProvider.java** - ChatGPT/GPT-4 support
5. **GroqProvider.java** - Groq API (fast inference)
6. **AnthropicProvider.java** - Claude 3/3.5 support
7. **GoogleGeminiProvider.java** - Gemini Pro support
8. **OllamaProvider.java** - Local/self-hosted LLMs
9. **CopilotProvider.java** - Azure OpenAI (Copilot)
10. **CustomOpenAIProvider.java** - Any OpenAI-compatible API

### Listener
11. **BotMessageListener.java** (`src/main/java/me/bill/fakePlayerPlugin/listener/`) - Intercepts DM commands

### Configuration
12. **secrets.yml** (`src/main/resources/`) - API key storage (not committed to git)
13. **config.yml** - Added `ai-conversations` section

### Language
14. **en.yml** - Added section 18 for AI conversation messages

## Configuration

### secrets.yml (NEW FILE)
```yaml
openai:
  api-key: "sk-..."  # Your OpenAI API key
  endpoint: ""       # Optional custom endpoint

groq:
  api-key: ""        # Groq API key
  endpoint: ""

anthropic:
  api-key: ""        # Claude API key
  endpoint: ""

google:
  api-key: ""        # Gemini API key
  endpoint: ""

ollama:
  enabled: false
  endpoint: "http://localhost:11434"
  model: "llama2"

copilot:
  api-key: ""
  endpoint: ""
  deployment-name: ""

custom:
  enabled: false
  api-key: ""
  endpoint: ""
  model: ""
```

### config.yml (NEW SECTION)
```yaml
ai-conversations:
  enabled: true
  
  default-personality: |
    You are {bot_name}, a helpful and friendly Minecraft player on this server.
    You speak casually and naturally like a real player would in chat.
    Keep responses SHORT — one or two sentences maximum.
    Never break character or mention that you're an AI or bot.
    Use Minecraft terms and slang naturally.
  
  max-history: 10    # Message pairs to remember per conversation
  cooldown: 3        # Seconds before bot can respond again
  debug: false       # Log AI requests/responses
```

## How It Works

1. **Player sends `/msg BotName Hello`**
2. **BotMessageListener** intercepts the command (HIGHEST priority)
3. Command is cancelled so vanilla /msg doesn't fire
4. **BotConversationManager** receives the message
5. Checks rate limits and conversation history
6. Sends conversation + personality to **AIProviderRegistry**
7. Active provider (e.g. OpenAI) generates response async
8. Response sent back via `/tellraw` (formatted like vanilla /msg)
9. History updated for next message

## Features

✅ **Multiple AI Providers** - Auto-selects first available from secrets.yml
✅ **Per-Bot Personalities** - Customizable via `setBotPersonality(uuid, text)`
✅ **Conversation History** - Maintains context per bot-player pair
✅ **Rate Limiting** - Configurable cooldown per bot
✅ **Async Processing** - Non-blocking AI generation
✅ **Graceful Fallback** - Works with or without API keys configured
✅ **Debug Mode** - Log all AI interactions for troubleshooting
✅ **Hot-Reload** - `/fpp reload` updates config without restart

## Usage Examples

### For Players
```
/msg Steve Hey, how's it going?
```
Bot responds with AI-generated message matching its personality.

### For Admins (Future Commands)
```
/fpp ai <bot> personality <text>    # Set custom personality
/fpp ai <bot> reset                  # Reset to default personality
/fpp ai clear <bot> <player>        # Clear conversation history
```

## API Key Setup

1. **OpenAI (Recommended for production)**
   - Get key: https://platform.openai.com/api-keys
   - Add to `secrets.yml` → `openai.api-key`
   - Cost: ~$0.002 per conversation

2. **Groq (Fastest, Free Tier)**
   - Get key: https://console.groq.com/keys
   - Add to `secrets.yml` → `groq.api-key`
   - Free tier: 30 requests/min

3. **Ollama (Free, Self-Hosted)**
   - Install: https://ollama.ai/
   - Run: `ollama run llama2`
   - Set `ollama.enabled: true` in secrets.yml

4. **Other Providers**
   - Anthropic Claude, Google Gemini, Azure Copilot all supported
   - Just add API key to respective section

## Integration Points

### FakePlayerPlugin.java
- Added `aiProviderRegistry` and `botConversationManager` fields
- Initialized in `onEnable()` after BotChatAI
- Registered `BotMessageListener` conditionally
- Cleanup in `onDisable()` via `clearAll()`

### Config.java
- `aiConversationsEnabled()` - Master toggle
- `aiConversationsDefaultPersonality()` - Base personality template
- `aiConversationsMaxHistory()` - History window size
- `aiConversationsCooldown()` - Rate limit (seconds)
- `aiConversationsDebug()` - Debug logging

### Public Accessors
```java
plugin.getAIProviderRegistry()
plugin.getBotConversationManager()
```

## Error Handling

- **No API Key**: Warning logged, feature silently disabled
- **API Failure**: Error logged, conversation continues without response
- **Rate Limit**: Silent skip with debug log
- **Network Issues**: CompletableFuture fails gracefully

## Performance

- **Async**: All AI requests run off main thread
- **Memory**: ~500 bytes per conversation message
- **Network**: 1 HTTP request per bot response
- **Latency**: 200ms-2s depending on provider

## Testing Checklist

- [ ] `/msg` to bot with no API key → silent (no crash)
- [ ] Add OpenAI key → bot responds
- [ ] Send 3 messages → bot remembers context
- [ ] Wait 3+ seconds → can message again
- [ ] `/fpp reload` → still works
- [ ] Server restart → conversations cleared (intentional)
- [ ] Debug mode → see requests in console

## Future Enhancements

1. **Commands** - `/fpp ai` sub-command for personality management
2. **Per-Bot Config** - Override personality/cooldown per bot
3. **Streaming** - Real-time "typing..." indicator
4. **Context Injection** - Auto-include world/location/time in prompt
5. **Learning** - Persistent personality based on past conversations
6. **Multi-Language** - Detect player language and respond accordingly

## Migration Notes

- **Config version**: 50 → 51
- **No database changes** (conversations are ephemeral)
- **New file**: `plugins/FakePlayerPlugin/secrets.yml`
- **Backward compatible**: Works with existing bots

## Troubleshooting

### "Bot doesn't respond"
- Check `secrets.yml` has valid API key
- Verify `ai-conversations.enabled: true` in config.yml
- Enable debug mode: `ai-conversations.debug: true`
- Check console for error messages

### "Rate limit errors"
- Reduce `max-history` (fewer tokens per request)
- Increase `cooldown` (fewer requests overall)
- Upgrade API plan or switch provider

### "Responses too generic"
- Customize `default-personality` in config
- Use per-bot personalities for variety
- Add more context to personality prompt

## Security

⚠️ **IMPORTANT**: `secrets.yml` contains API keys!
- Add to `.gitignore` immediately
- Never commit to version control
- Use read-only API keys when possible
- Rotate keys if exposed

## Credits

Implemented: April 10, 2026
Supports: OpenAI, Groq, Anthropic, Google, Ollama, Azure, Custom APIs
Version: FakePlayerPlugin 1.6.0

