# Swap System

The swap system automatically rotates bots — they periodically leave the server and rejoin with a new name, simulating organic player activity without any manual intervention.

---

## Overview

When enabled, each bot is assigned a **personality archetype** and a **session duration**. After its session ends, the bot:

1. *(Optional)* Sends a farewell chat message (e.g. "gtg", "brb")
2. Leaves the server with a leave message
3. Waits a configurable rejoin delay
4. Rejoins with a new name drawn from the pool (or the same name with a small probability)
5. *(Optional)* Sends a greeting message (e.g. "hey", "back")

This loop repeats indefinitely, creating natural-looking join/leave activity.

---

## Enabling the Swap System

```yaml
swap:
  enabled: false
```

Set to `true` in `config.yml`, or control live with:

```
/fpp swap              ← bare command toggles on/off (like /fpp chat)
/fpp swap on
/fpp swap off
/fpp swap status
/fpp swap now <bot>    ← immediately trigger a single bot's swap
/fpp swap list         ← list scheduled bots and their personalities
```

The command writes the change to `config.yml` immediately — it survives restarts.

> **Toggle behaviour (v1.5.10+):** Running `/fpp swap` with no arguments flips the current state exactly like `/fpp chat` — no need to type `on` or `off`.

**Required permission:** `fpp.swap`

---

## Configuration

```yaml
swap:
  enabled: false              # Master toggle — false = bots never swap out

  session:
    min: 60                   # Minimum session duration in seconds (1 min)
    max: 300                  # Maximum session duration in seconds (5 min)

  absence:
    min: 30                   # Minimum offline time in seconds (30 s)
    max: 120                  # Maximum offline time in seconds (2 min)

  max-swapped-out: 0          # Max bots offline simultaneously (0 = unlimited)
  farewell-chat: true         # Bots say goodbye before leaving
  greeting-chat: true         # Bots say hi when rejoining
  same-name-on-rejoin: true   # Reuse the same name if available on rejoin
```

---

## Settings Reference

### Session Duration

| Setting | Description |
|---------|-------------|
| `session.min` | Minimum time (seconds) a bot stays before swapping. |
| `session.max` | Maximum time (seconds) a bot stays before swapping. |

The actual session length is a random value in `[session.min, session.max]`, then scaled by the bot's personality multiplier.

---

### Personality Archetypes

Each bot is randomly assigned one of five personality types when swap starts:

| Archetype | Session Modifier | Description |
|-----------|-----------------|-------------|
| **quiet** | 2.0× | Long-term sitter — stays extended periods |
| **passive** | 1.4× | Below-average activity |
| **normal** | 1.0× | Typical player session |
| **active** | 0.7× | Chats and leaves more often |
| **chatty** | 0.5× | Quick popper — joins briefly, leaves fast |

---

### Absence (Offline) Delay

| Setting | Description |
|---------|-------------|
| `absence.min` | Minimum seconds between the bot leaving and rejoining. |
| `absence.max` | Maximum seconds between the bot leaving and rejoining. |
| `max-swapped-out` | Cap on how many bots can be offline at the same time (`0` = no cap). |

---

### Chat Integration

```yaml
farewell-chat: true    # requires fake-chat.enabled: true
greeting-chat: true    # requires fake-chat.enabled: true
```

When `farewell-chat: true`, the bot sends a natural farewell message from `bot-messages.yml` before leaving (e.g. "gtg", "bbl", "bye").

When `greeting-chat: true`, the bot sends a greeting shortly after rejoining (e.g. "hey", "back", "what did I miss?").

Both require `fake-chat.enabled: true` in config.

---

## Interaction with Persistence

When `persistence.enabled: true`, bots that were mid-session when the server shut down **rejoin after restart** at their last position, and their session timer restarts fresh. They are not considered "swapped" by the restart.

---

## Example Setup — Active Server Simulation

```yaml
swap:
  enabled: true
  session:
    min: 300      # 5 minutes minimum
    max: 1800     # 30 minutes maximum
  absence:
    min: 30
    max: 120
  max-swapped-out: 0
  farewell-chat: true
  greeting-chat: true
  same-name-on-rejoin: true

fake-chat:
  enabled: true
  require-player-online: false
  chance: 0.6
  interval:
    min: 30
    max: 120
```
