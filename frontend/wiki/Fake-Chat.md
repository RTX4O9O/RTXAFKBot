# Fake Chat

FPP bots can send random chat messages to make the server feel lively and populated.

---

## Enabling Fake Chat

```yaml
fake-chat:
  enabled: false
```

Set `enabled: true` in `config.yml`, or toggle live with:

```
/fpp chat on
/fpp chat off
/fpp chat status
```

The command writes the change to `config.yml` immediately — it survives `/fpp reload` and restarts.

**Required permission:** `fpp.chat`

---

## How It Works

- Each bot has its own **independent chat timer** with a random interval in `[interval.min, interval.max]` seconds
- When the timer fires, a random roll against `chance` determines if a message is actually sent
- If the roll passes, a random message is picked from `bot-messages.yml` with placeholders filled in
- The process repeats with a new random interval

No two bots share the same timer — they chat independently and asynchronously.

---

## Configuration

```yaml
fake-chat:
  enabled: false

  # Only send messages when at least one real player is online
  require-player-online: true

  # Probability (0.0–1.0) a message fires each timer interval
  chance: 0.75

  # Random interval between each bot's own messages (seconds)
  interval:
    min: 5
    max: 10

  # Simulate a typing pause (0–2.5 s) before each message
  typing-delay: true

  # Chance a bot sends a quick follow-up message a few seconds later
  burst-chance: 0.12
  burst-delay:
    min: 2
    max: 5

  # When a real player says a bot's name in chat, that bot may reply
  reply-to-mentions: true
  mention-reply-chance: 0.65
  reply-delay:
    min: 2
    max: 8

  # Minimum gap (seconds) between any two bots chatting (0 = disabled)
  stagger-interval: 3

  # Give each bot a random chat-frequency tier (quiet/normal/active/very-active)
  activity-variation: true

  # How many of a bot's own recent messages to remember and avoid repeating
  history-size: 5

  # Chat line format. Supports MiniMessage tags and legacy & codes.
  # Placeholders: {prefix}  {bot_name}  {suffix}  {message}
  chat-format: "&7{bot_name}: {message}"

  # Format for bodyless or proxy-remote bot broadcasts
  remote-format: "<yellow>{name}<dark_gray>: <white>{message}"
```

| Setting | Description |
|---------|-------------|
| `enabled` | Master toggle for the entire fake-chat system |
| `require-player-online` | Suppress chat when the server is empty of real players |
| `chance` | Roll probability per interval tick (`0.75` = 75% chance) |
| `interval.min` | Minimum seconds between a single bot's messages |
| `interval.max` | Maximum seconds between a single bot's messages |
| `typing-delay` | Simulate a 0–2.5 s typing pause before each message |
| `burst-chance` | Probability a bot sends a quick follow-up message |
| `burst-delay` | Seconds before the follow-up fires (min/max) |
| `reply-to-mentions` | Bots may reply when a player says their name in chat |
| `mention-reply-chance` | Probability a named bot actually replies |
| `reply-delay` | Seconds before the mention reply fires (min/max) |
| `stagger-interval` | Minimum gap (s) between any two bots chatting. 0 = disabled |
| `activity-variation` | Random per-bot chat frequency tier (quiet/normal/active/very-active) |
| `history-size` | Recent messages to remember and avoid repeating |
| `chat-format` | Format of every chat line. Supports MiniMessage and `&` codes. Placeholders: `{prefix}`, `{bot_name}`, `{suffix}`, `{message}`. |
| `remote-format` | MiniMessage format for bodyless / proxy-remote bot broadcasts. Placeholders: `{name}`, `{message}`. |

> **Hot Reload:** Changes to `interval`, `chance`, and `stagger-interval` take effect immediately when you run `/fpp reload` — all bot chat loops are restarted with the new values. (Fixed in v1.5.10)

### `chat-format` placeholders

| Placeholder | Value |
|-------------|-------|
| `{prefix}` | LuckPerms group prefix for this bot (empty when LuckPerms is not installed or no prefix is set) |
| `{bot_name}` | Bot display name |
| `{suffix}` | LuckPerms group suffix for this bot (empty when LuckPerms is not installed or no suffix is set) |
| `{message}` | Message text drawn from `bot-messages.yml` |

### `chat-format` examples

| Format string | Output style |
|---------------|-------------|
| `"&7{bot_name}: {message}"` | Gray name with colon (default) |
| `"&7{prefix}{bot_name}&7: {message}"` | LP prefix before name |
| `"{prefix}{bot_name}{suffix}&7: {message}"` | Full prefix + suffix wrap |
| `"<{bot_name}> {message}"` | IRC-style `<BotName> hello` |
| `"<gray>[Bot]</gray> {bot_name}: {message}"` | Gray `[Bot]` label |
| `"<gradient:#ff0000:#0000ff>{bot_name}</gradient>: {message}"` | Gradient-colored name |

---

## Event Triggers

Bots can react to server events by sending messages from pools defined in `bot-messages.yml`.

```yaml
fake-chat:
  event-triggers:
    enabled: true

    on-player-join:
      enabled: true
      chance: 0.40
      delay: { min: 2, max: 6 }

    on-death:
      enabled: true
      players-only: false
      chance: 0.30
      delay: { min: 1, max: 4 }

    on-player-leave:
      enabled: true
      chance: 0.30
      delay: { min: 1, max: 4 }
```

| Setting | Description |
|---------|-------------|
| `event-triggers.enabled` | Master switch — disabling also turns off all sub-triggers below |
| `on-player-join` | A bot greets real players when they join. Uses `join-reactions` pool in `bot-messages.yml`. |
| `on-death` | A bot reacts when an entity dies. `players-only: true` ignores mob/animal deaths. Uses `death-reactions` pool. |
| `on-player-leave` | A bot says goodbye when a real player leaves. Uses `leave-reactions` pool. |

---

## Keyword Reactions

Bots reply when a real player's message contains a configured keyword.

```yaml
fake-chat:
  keyword-reactions:
    enabled: false
    keywords:
      trade: "trade-reactions"   # pool key in bot-messages.yml
      help:  "help-reactions"
```

When `enabled: true`, a random bot replies to the player's message using the configured pool from `bot-messages.yml`.

---

## Message Pool

Messages come from `plugins/FakePlayerPlugin/bot-messages.yml`:

```yaml
messages:
  - "hey everyone"
  - "what's up {random_player}"
  - "lol"
  - "gg"
  - "this server is awesome"
  # ...
```

### Placeholders

| Placeholder | Value |
|-------------|-------|
| `{name}` | The bot's own name |
| `{random_player}` | A random real online player's name |

---

## Swap Integration

When the [Swap System](Swap-System.md) is enabled, bots can also send:

- **Farewell messages** before leaving (`farewell-chat: true`)
- **Greeting messages** shortly after joining (`greeting-chat: true`)

These messages come from the same `bot-messages.yml` pool.

---

## Recommended Settings

### Casual / light activity

```yaml
fake-chat:
  enabled: true
  require-player-online: true
  chance: 0.5
  interval:
    min: 60
    max: 180
```

### Busy / active feel

```yaml
fake-chat:
  enabled: true
  require-player-online: false
  chance: 0.8
  interval:
    min: 10
    max: 30
```

---

## Tips

- **Keep messages short** — single-line, casual, conversational
- **Avoid all-caps** — it looks spammy
- **Mix question types and reactions** — "lol", "anyone wanna trade?", "what did I miss?"
- **Use `{random_player}` sparingly** — only 10–20% of messages should address real players
- **Longer intervals feel more natural** — 30–120 seconds is a realistic human chat pace
- Set `require-player-online: true` if you don't want bots chatting into an empty server
