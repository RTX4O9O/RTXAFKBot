# 💬 Fake Chat

FPP bots can send realistic chat messages to make your server feel active.

Fake chat is **separate** from AI DM conversations:

- **Fake chat** = autonomous public-chat messages and reactions
- **AI conversations** = bot replies to `/msg`, `/tell`, `/whisper`

---

## Enable Fake Chat

```yaml
fake-chat:
  enabled: false
```

Live controls:

```text
/fpp chat
/fpp chat on
/fpp chat off
/fpp chat status
```

Permission: `fpp.chat`

---

## Core Configuration

```yaml
fake-chat:
  enabled: false
  require-player-online: true
  chance: 0.75
  interval:
    min: 5
    max: 10
  typing-delay: true
  activity-variation: true
  history-size: 5
  stagger-interval: 3
  burst-chance: 0.12
  burst-delay:
    min: 2
    max: 5
  reply-to-mentions: true
  mention-reply-chance: 0.65
  reply-delay:
    min: 2
    max: 8
  remote-format: "<yellow>{name}<dark_gray>: <white>{message}"
```

| Key | Description |
|-----|-------------|
| `enabled` | Master toggle |
| `require-player-online` | Suppress bot chat if no real player is online |
| `chance` | Probability roll for each bot when its timer fires |
| `interval.min` / `max` | Seconds between a bot's own chat attempts |
| `typing-delay` | Simulate a short human-like pause before sending |
| `activity-variation` | Gives each bot a random activity tier |
| `history-size` | Prevents recent self-message repetition |
| `stagger-interval` | Minimum gap between any two bot messages |
| `burst-chance` | Chance of a quick follow-up message |
| `burst-delay.*` | Delay before the follow-up |
| `reply-to-mentions` | Allows mention replies in public chat |
| `mention-reply-chance` | Chance the named bot actually responds |
| `reply-delay.*` | Delay before mention reply |
| `remote-format` | Used for bodyless or proxy-remote bot messages |

---

## Bot Activity Tiers

Bots can have a random or forced activity tier.

Tiers:
- `quiet`
- `passive`
- `normal`
- `active`
- `chatty`

Per-bot control:

```text
/fpp chat <bot> tier quiet
/fpp chat <bot> tier active
/fpp chat <bot> tier reset
/fpp chat <bot> info
```

---

## Per-Bot Chat Controls

```text
/fpp chat <bot> on
/fpp chat <bot> off
/fpp chat <bot> say <message>
/fpp chat <bot> tier <tier>
/fpp chat <bot> mute [seconds]
/fpp chat <bot> info
/fpp chat all <on|off|tier <tier>|mute [seconds]> 
```

Useful behavior:
- `off` permanently silences that bot until turned back on
- `mute` is a temporary silence timer
- `say` forces an immediate message
- `tier` changes how often the bot speaks

---

## Bot-to-Bot Conversations

Bots can reply to each other's public chat messages.

```yaml
fake-chat:
  bot-to-bot:
    enabled: true
    reply-chance: 0.35
    chain-chance: 0.40
    max-chain: 3
    cooldown: 8
    delay:
      min: 4
      max: 14
```

| Key | Description |
|-----|-------------|
| `enabled` | Master switch |
| `reply-chance` | Chance another bot replies |
| `chain-chance` | Chance the conversation continues |
| `max-chain` | Max exchanges in one chain |
| `cooldown` | Min seconds between separate conversation starts |
| `delay.*` | Delay before bot-to-bot reply |

This helps chat feel less like isolated one-liners and more like a real public conversation.

---

## Event Triggers

Bots can react to server events using special message pools.

```yaml
fake-chat:
  event-triggers:
    enabled: true
    on-player-join:
      enabled: true
      chance: 0.40
    on-death:
      enabled: true
      players-only: false
      chance: 0.30
    on-player-leave:
      enabled: true
      chance: 0.30
    on-advancement:
      enabled: true
      chance: 0.45
    on-first-join:
      enabled: true
      chance: 0.70
    on-kill:
      enabled: true
      chance: 0.35
    on-high-level:
      enabled: true
      min-level: 30
      chance: 0.35
```

### Supported triggers

| Trigger | Description | Message pool |
|---------|-------------|--------------|
| `on-player-join` | React when a real player joins | `join-reactions` |
| `on-death` | React to entity/player deaths | `death-reactions` |
| `on-player-leave` | React when a real player leaves | `leave-reactions` |
| `on-advancement` | React to announced advancements | `advancement-reactions` |
| `on-first-join` | Welcome brand-new players | `first-join-reactions` |
| `on-kill` | React when a player kills another real player | `kill-reactions` |
| `on-high-level` | React to important XP levels | `high-level-reactions` |

---

## Player Chat Reactions

Bots can react directly when a real player sends a chat message.

```yaml
fake-chat:
  on-player-chat:
    enabled: false
    use-ai: true
    ai-cooldown: 30
    chance: 0.25
    max-bots: 1
    ignore-short: true
    ignore-commands: true
    mention-player: 0.50
    delay:
      min: 2
      max: 8
```

| Key | Description |
|-----|-------------|
| `enabled` | Master switch |
| `use-ai` | Use the AI provider for contextual public-chat replies |
| `ai-cooldown` | Cooldown before the same bot can react with AI again |
| `chance` | Chance any given player message gets a response |
| `max-bots` | Max number of bots that react to one player message |
| `ignore-short` | Skip tiny messages like `k` / `ok` |
| `ignore-commands` | Ignore lines starting with `/` |
| `mention-player` | Chance the bot mentions the player's name |
| `delay.*` | Delay before the reaction appears |

> This uses the **public-chat reaction** flow. It is still separate from private AI DM conversations.

---

## Keyword Reactions

Bots can react to keywords in real player chat.

```yaml
fake-chat:
  keyword-reactions:
    enabled: false
    keywords:
      trade: "trade-reactions"
      help: "help-reactions"
      pvp: "pvp-reactions"
```

If a player's message contains one of these keys, a bot can reply using the matching pool.

---

## Message Pools (`bot-messages.yml`)

FPP now supports multiple message pools, not just the base `messages:` list.

### Common pools

| Pool key | Used for |
|----------|----------|
| `messages` | Normal bot chat |
| `replies` | Mention replies |
| `burst-followups` | Quick second message |
| `join-reactions` | Player join reactions |
| `death-reactions` | Death reactions |
| `leave-reactions` | Leave reactions |
| `bot-to-bot-replies` | Bot-to-bot chat |
| `advancement-reactions` | Advancement reactions |
| `first-join-reactions` | First-join welcomes |
| `kill-reactions` | PvP kill reactions |
| `high-level-reactions` | XP level milestone reactions |
| `player-chat-reactions` | Public player-chat replies |
| `keyword-reactions.<key>` | Custom keyword-based pools |

### Placeholders

| Placeholder | Value |
|-------------|-------|
| `{name}` | Bot name |
| `{random_player}` | Random real online player |
| `{player_name}` | Triggering player's name (where supported) |
| `{message}` | Original message text (where supported) |

---

## Swap Integration

When the swap system is enabled, bots can also send:
- farewell messages before leaving
- greeting messages after rejoining

These are controlled by:

```yaml
swap:
  farewell-chat: true
  greeting-chat: true
```

---

## Fake Chat vs AI DM Conversations

### Fake Chat
- public messages in server chat
- autonomous chatter and reactions
- configured in `fake-chat.*`
- content mostly comes from `bot-messages.yml`

### AI Conversations
- private replies to `/msg`, `/tell`, `/whisper`
- configured in `ai-conversations.*`
- personality files live in `plugins/FakePlayerPlugin/personalities/`
- provider keys live in `plugins/FakePlayerPlugin/secrets.yml`

---

## Good Starter Config

### Quiet / natural

```yaml
fake-chat:
  enabled: true
  require-player-online: true
  chance: 0.45
  interval:
    min: 45
    max: 150
  bot-to-bot:
    enabled: true
    reply-chance: 0.20
```

### Busy / active feel

```yaml
fake-chat:
  enabled: true
  require-player-online: false
  chance: 0.75
  interval:
    min: 12
    max: 35
  on-player-chat:
    enabled: true
    max-bots: 1
```

---

## Tips

- keep messages short and casual
- avoid too many keyword pools at once
- use `require-player-online: true` if you do not want bots chatting into an empty server
- use per-bot tiers instead of globally shrinking intervals when only a few bots should be active speakers
- if public-chat AI feels too noisy, disable `fake-chat.on-player-chat.use-ai` and keep private AI DMs enabled

---

See also:
- [Commands](Commands.md)
- [Configuration](Configuration.md)
- [Bot-Messages](Bot-Messages.md)
- [Bot-Behaviour](Bot-Behaviour.md)
