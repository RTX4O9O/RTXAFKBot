# Bot Messages (Fake Chat)

FPP bots can send chat messages to make the server feel alive.  
Messages are stored in:

```
plugins/FakePlayerPlugin/bot-messages.yml
```

This file is loaded at startup and reloaded on `/fpp reload`.

---

## File Format

`bot-messages.yml` is a plain YAML list:

```yaml
messages:
  - "hey everyone"
  - "what's up"
  - "anyone online?"
  - "good morning {random_player}"
  - "lol"
  - "this server is great"
  - "gg"
  # ...1000 messages total in the default file
```

---

## Placeholders

| Placeholder | Replaced with |
|-------------|--------------|
| `{name}` | The bot's own name |
| `{random_player}` | A random real online player's name |

### Examples

```yaml
- "hey {random_player}, what are you up to?"
- "anyone wanna trade?"
- "{name} is bored"
- "just logged on, what did I miss?"
```

---

## How Messages Are Sent

Each bot has its own **independent timer** — they don't all chat at the same time.  
The timing and probability are controlled by `fake-chat` settings in `config.yml`:

```yaml
fake-chat:
  enabled: false
  require-player-online: true
  chance: 0.75
  interval:
    min: 5    # seconds
    max: 10   # seconds
```

| Setting | Description |
|---------|-------------|
| `enabled` | Master toggle. Also toggled with `/fpp chat [on|off]`. |
| `require-player-online` | Only send messages when at least one real player is online. |
| `chance` | Probability (0.0–1.0) that a message fires each timer interval. `0.75` = 75% chance. |
| `interval.min` | Minimum seconds between a bot's own messages. |
| `interval.max` | Maximum seconds between a bot's own messages. |

A bot's interval is randomised independently within `[min, max]` each time it fires.

---

## Farewell & Greeting Messages (Swap System)

When the [Swap System](Swap-System.md) is active with `farewell-chat: true` or `greeting-chat: true`, bots automatically send a farewell message before leaving and/or a greeting after rejoining.

These messages come from the same `bot-messages.yml` pool — FPP selects naturally fitting messages (short, social phrases like "gtg", "brb", "hey", "back").

---

## Toggling Fake Chat

```
/fpp chat on       ← enable
/fpp chat off      ← disable
/fpp chat status   ← show current state
```

The state is saved to `config.yml` and persists across restarts.

**Required permission:** `fpp.chat`

---

## Tips

- Keep messages **short and natural** — long paragraphs look fake.  
- Mix questions, reactions, and greetings for variety.  
- Use `{random_player}` sparingly — overuse looks robotic.  
- Set `interval.min` and `interval.max` to at least 5–10 seconds to avoid spam.  
- A `chance` of `0.5`–`0.75` gives a realistic sporadic feel.

