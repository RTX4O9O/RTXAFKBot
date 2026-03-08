# Language & Messages

FPP uses **MiniMessage** formatting for all in-game text.  
The active language file is set in `config.yml` → `language: en`.

The default language file lives at:
```
plugins/FakePlayerPlugin/language/en.yml
```

Apply changes with `/fpp reload` — no restart needed.

---

## MiniMessage Colour Format

FPP uses [MiniMessage](https://docs.advntr.dev/minimessage/format.html) tags throughout.

| Syntax | Example | Result |
|--------|---------|--------|
| Named colour | `<red>text</red>` | Red text |
| Hex colour | `<#0079FF>text</#0079FF>` | Brand-blue text |
| Gray | `<gray>text</gray>` | Gray text |
| White | `<white>text</white>` | White text |
| Dark gray | `<dark_gray>text</dark_gray>` | Dark gray text |
| Yellow | `<yellow>text</yellow>` | Yellow text (join/leave) |
| Bold | `<bold>text</bold>` | **Bold text** |
| Italic | `<italic>text</italic>` | *Italic text* |
| Underline | `<underlined>text</underlined>` | Underlined text |
| Strikethrough | `<st>text</st>` | ~~Strikethrough~~ |
| Reset | `<reset>` | Clears all formatting |

> **Tip:** The plugin's brand colour is `#0079FF` (blue).

---

## Placeholders

Each language key supports specific placeholders in `{curly}` braces:

| Placeholder | Used in | Value |
|-------------|---------|-------|
| `{prefix}` | Most messages | The formatted plugin prefix (e.g. `[ꜰᴘᴘ]`) |
| `{count}` | spawn-success, delete-all | Number of bots |
| `{total}` | spawn-success | Total active bots |
| `{name}` | delete-*, bot-join, bot-leave | Bot display name |
| `{killer}` | bot-kill | Name of the player who killed the bot |
| `{max}` | spawn-max-reached | Global bot cap |
| `{limit}` | spawn-user-limit-reached | Player's personal bot limit |
| `{ms}` | reload-success | Milliseconds the reload took |
| `{0}`, `{1}` | unknown-command | Positional args (e.g. command name) |

---

## All Message Keys

### Prefix

```yaml
prefix: "<dark_gray>[<#0079FF>ꜰᴘᴘ</#0079FF><dark_gray>] "
```

Prepended to messages that include `{prefix}`. Edit to rebrand the plugin prefix.

---

### Generic Errors

| Key | Default Message |
|-----|----------------|
| `no-permission` | You don't have permission to do that. |
| `unknown-command` | Unknown sub-command. Try `/fpp help`. |
| `player-only` | Only a player can run this command. |
| `invalid-number` | Invalid number. Please enter a positive integer. |

---

### Help Menu

| Key | Description |
|-----|-------------|
| `help-header` | Top border of the help menu |
| `help-entry` | Format for each command line: `/{cmd} {args} — {desc}` |
| `help-footer` | Bottom border of the help menu |

---

### Reload

| Key | Placeholders | Description |
|-----|-------------|-------------|
| `reload-success` | `{ms}` | Shown when `/fpp reload` completes |

---

### Spawn

| Key | Placeholders | Description |
|-----|-------------|-------------|
| `spawn-success` | `{count}`, `{total}` | Bots spawned successfully |
| `spawn-max-reached` | `{max}` | Global max-bots cap hit |
| `spawn-user-limit-reached` | `{limit}` | Personal bot limit hit |
| `spawn-invalid` | — | Wrong usage of `/fpp spawn` |
| `spawn-invalid-name` | — | Bot name contains invalid characters |
| `spawn-name-taken` | `{name}` | A bot with that name is already active |

---

### Delete

| Key | Placeholders | Description |
|-----|-------------|-------------|
| `delete-success` | `{name}` | Bot successfully removed |
| `delete-all` | `{count}` | All bots removed |
| `delete-none` | — | No bots to delete |
| `delete-not-found` | `{name}` | No bot with that name found |

---

### List

| Key | Description |
|-----|-------------|
| `list-none` | Shown in `/fpp list` when no bots are active |

---

### Bot Broadcasts

| Key | Placeholders | Description |
|-----|-------------|-------------|
| `bot-join` | `{name}` | Broadcast when a bot is spawned (e.g. `Herobrine joined the game`) |
| `bot-leave` | `{name}` | Broadcast when a bot is removed |
| `bot-kill` | `{killer}`, `{name}` | Broadcast when a player kills a bot |

---

### Chat Toggle

| Key | Description |
|-----|-------------|
| `chat-enabled` | Fake chat was turned on |
| `chat-disabled` | Fake chat was turned off |
| `chat-status-on` | Showing current status (on) |
| `chat-status-off` | Showing current status (off) |
| `chat-invalid` | Invalid argument for `/fpp chat` |

---

### Swap Toggle

| Key | Description |
|-----|-------------|
| `swap-enabled` | Swap system was turned on |
| `swap-disabled` | Swap system was turned off |
| `swap-status-on` | Showing current status (on) |
| `swap-status-off` | Showing current status (off) |
| `swap-invalid` | Invalid argument for `/fpp swap` |

---

### Info / Database

| Key | Placeholders | Description |
|-----|-------------|-------------|
| `info-no-records` | `{name}` | No database records for the queried name |
| `info-db-unavailable` | — | Database is not available |

---

### Teleport

| Key | Placeholders | Description |
|-----|-------------|-------------|
| `tph-no-bots` | — | Player has no active bots |
| `tph-specify-name` | — | Player has multiple bots and must specify one |
| `tph-not-yours` | `{name}` | Player doesn't own the named bot |
| `tph-not-found` | `{name}` | No active bot with that name |
| `tph-failed` | `{name}` | Teleport failed (entity invalid) |
| `tp-specify-name` | — | Multiple bots active, must specify name |

---

## Example Customisation

### Change the prefix

```yaml
prefix: "<dark_gray>[<yellow>ꜰᴘᴘ</yellow><dark_gray>] "
```

### Make join/leave messages match vanilla exactly

```yaml
bot-join:  "<yellow>{name} joined the game"
bot-leave: "<yellow>{name} left the game"
```

### Add an emoji to spawn success

```yaml
spawn-success: "{prefix}🤖 <#0079FF>{count}</#0079FF> <gray>bot(s) summoned. <dark_gray>({total} active)"
```

---

## Adding a New Language

1. Copy `plugins/FakePlayerPlugin/language/en.yml`  
2. Rename it (e.g. `de.yml` for German)  
3. Translate all values (keep the keys and placeholders unchanged)  
4. Set `language: de` in `config.yml`  
5. Run `/fpp reload`

