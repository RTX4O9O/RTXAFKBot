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
fake-player:
  swap:
    enabled: false
```

Set to `true` to enable, or toggle live with:
```
/fpp swap on
/fpp swap off
/fpp swap status
```

The command writes the change to `config.yml` immediately.

**Required permission:** `fpp.swap`

---

## Configuration

```yaml
fake-player:
  swap:
    enabled: false

    session-min: 120      # seconds — minimum session length before a bot swaps
    session-max: 600      # seconds — maximum session length

    rejoin-delay-min: 5   # seconds between a bot leaving and rejoining
    rejoin-delay-max: 45

    jitter: 30            # ±seconds of extra randomness per bot (prevents simultaneous swaps)

    reconnect-chance: 0.15   # 0.0–1.0 — chance the bot keeps its same name on rejoin
    afk-kick-chance: 5       # 0–100 — % chance the rejoin gap is extended by 1–3 extra minutes

    farewell-chat: true    # send a chat message before leaving (requires fake-chat.enabled)
    greeting-chat: true    # send a chat message after rejoining (requires fake-chat.enabled)

    time-of-day-bias: true  # scale session lengths by server time of day
```

---

## Settings Reference

### Session Duration

| Setting | Description |
|---------|-------------|
| `session-min` | Minimum time (seconds) a bot stays before swapping. |
| `session-max` | Maximum time (seconds) a bot stays before swapping. |
| `jitter` | ±Seconds of extra randomness added per-bot. Prevents all bots swapping at the same moment. |

The actual session length = random value in `[min, max]` × personality modifier ± jitter.

---

### Personality Archetypes

Each bot is randomly assigned one of three personality types when it spawns:

| Archetype | Probability | Session Modifier | Description |
|-----------|------------|-----------------|-------------|
| **VISITOR** | 25% | 30–60% of range | Quick popper — joins briefly then leaves |
| **REGULAR** | 50% | 80–120% of range | Typical player — stays a normal session |
| **LURKER** | 25% | 150–250% of range | Long-term sitter — stays for extended periods |

Example with `session-min: 120` and `session-max: 600`:

| Archetype | Effective Range |
|-----------|---------------|
| VISITOR | ~36–360 seconds |
| REGULAR | ~96–720 seconds |
| LURKER | ~180–1500 seconds |

---

### Rejoin Delay

| Setting | Description |
|---------|-------------|
| `rejoin-delay-min` | Minimum gap in seconds between the bot leaving and rejoining. |
| `rejoin-delay-max` | Maximum gap in seconds between the bot leaving and rejoining. |
| `afk-kick-chance` | Percent chance (0–100) the rejoin gap is extended by 1–3 extra minutes. Simulates an AFK kick. |

---

### Reconnect Chance

```yaml
reconnect-chance: 0.15
```

Probability (0.0–1.0) that the rejoining bot **keeps the same name** instead of getting a new one.  
`0.15` = 15% chance of a same-name reconnect — simulates a brief disconnect and reconnect.

---

### Time-of-Day Bias

```yaml
time-of-day-bias: true
```

Scales session durations based on the server's real-world local time:

| Time of Day | Modifier | Rationale |
|-------------|---------|-----------|
| Peak (18:00–22:00) | Up to 1.4× longer | Evening — more players online |
| Off-peak (01:00–05:00) | Down to 0.5× shorter | Late night — fewer active players |
| Other hours | 1.0× (no change) | Normal behaviour |

---

### Chat Integration

```yaml
farewell-chat: true    # requires fake-chat.enabled: true
greeting-chat: true    # requires fake-chat.enabled: true
```

When `farewell-chat: true`, the bot sends a natural farewell message from `bot-messages.yml` before leaving (e.g. "gtg", "bbl", "bye").

When `greeting-chat: true`, the replacement bot sends a greeting shortly after joining (e.g. "hey", "back", "what did I miss?").

Both require `fake-chat.enabled: true` in config.

---

## Interaction with Persistence

When `persist-on-restart: true`, bots that were mid-session when the server shut down **rejoin after restart** at their last position, and their session timer restarts fresh. They are not considered "swapped" by the restart.

---

## Example Setup — Active Server Simulation

```yaml
fake-player:
  swap:
    enabled: true
    session-min: 300      # 5 minutes minimum
    session-max: 1800     # 30 minutes maximum
    rejoin-delay-min: 30
    rejoin-delay-max: 120
    jitter: 60
    reconnect-chance: 0.10
    afk-kick-chance: 10
    farewell-chat: true
    greeting-chat: true
    time-of-day-bias: true

fake-chat:
  enabled: true
  require-player-online: false
  chance: 0.6
  interval:
    min: 30
    max: 120
```

