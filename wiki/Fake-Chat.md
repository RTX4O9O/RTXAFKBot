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
```

| Setting | Description |
|---------|-------------|
| `enabled` | Master toggle for the entire fake-chat system |
| `require-player-online` | Suppress chat when the server is empty of real players |
| `chance` | Roll probability per interval tick (`0.75` = 75% chance) |
| `interval.min` | Minimum seconds between a single bot's messages |
| `interval.max` | Maximum seconds between a single bot's messages |

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

