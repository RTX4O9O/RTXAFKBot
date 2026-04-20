# 📛 Bot Names

FPP picks bot names from a pool stored in:
```
plugins/FakePlayerPlugin/bot-names.yml
```

---

## How the Name Pool Works

When a bot is spawned without a `--name` flag, FPP randomly selects a name from the list in `bot-names.yml`.  
The plugin **never assigns the same name to two active bots** simultaneously - if all names in the pool are in use, it falls back to generating names in the format `bot<number>`.

The pool is loaded at startup and refreshed on `/fpp reload`.

---

## File Format

`bot-names.yml` is a simple YAML list:

```yaml
names:
  - Herobrine
  - Notch
  - jeb_
  - Technoblade
  - Dream
  - Skeppy
  - BadBoyHalo
  - Philza
  - Wilbur
  - TommyInnit
  # ...add as many as you like
```

There are **1000 names** pre-populated in the default file - more than enough to avoid repeats in any practical scenario.

---

## Editing the Name Pool

1. Open `plugins/FakePlayerPlugin/bot-names.yml`
2. Add or remove names under the `names:` key - one name per line, prefixed with `- `
3. Run `/fpp reload` to apply changes immediately

### Name Rules

Bot names must follow Minecraft username validation:
- **1-16 characters**
- **Letters, digits, and underscores only** (`A-Z`, `a-z`, `0-9`, `_`)
- No spaces, special characters, or Unicode symbols

Invalid names are silently skipped when loading the file.

---

## User-Tier Bot Names

When a regular player (with `fpp.spawn.user` but not `fpp.spawn`) spawns a bot, the name is **always** auto-generated - they cannot use the `--name` flag.

The auto-name format is:

| Situation | Format |
|-----------|--------|
| Player's first bot | `bot-{PlayerName}` |
| Player's second bot | `bot-{PlayerName}-2` |
| Player's third bot | `bot-{PlayerName}-3` |
| … and so on | `bot-{PlayerName}-#` |

For example, if `El_Pepes` spawns 3 bots, they will be named:
```
bot-El_Pepes
bot-El_Pepes-2
bot-El_Pepes-3
```

---

## Fallback Naming

If the bot-name pool is exhausted (all names are currently in use), FPP generates sequential fallback names:
```
bot1, bot2, bot3, ...
```

To avoid this, keep your name pool larger than the maximum number of bots you expect to run simultaneously.

---

## Badword Filter

FPP includes a built-in profanity filter to prevent inappropriate bot names.

When enabled, every name is checked before spawning (and on startup restore). The filter applies:
1. **Raw detection** — case-insensitive substring scan
2. **Leet-speak normalization** — `0→o`, `1→i`, `3→e`, `4→a`, etc.
3. **Aggressive mode** (when `auto-detection.enabled: true`) — collapses duplicate characters before scanning
4. **Regex patterns** — custom patterns from `bad-words.yml` or auto-generated from word list

Word sources (merged at load time):
- Remote global baseline (`badword-filter.global-list-url` — the CMU bad-word list, fetched on startup)
- Inline `badword-filter.words` list in `config.yml`
- Local `plugins/FakePlayerPlugin/bad-words.yml` (custom words + `patterns:` block)

If `auto-rename: true` (default), names that fail the filter get a clean replacement from the pool instead of being hard-blocked.

Whitelist: add exact names to `badword-filter.whitelist` to allow them through unconditionally.

### Key config keys

```yaml
badword-filter:
  enabled: true
  use-global-list: true
  words: []
  whitelist: []
  auto-rename: true
  auto-detection:
    enabled: true
    mode: normal
```

### Command

```text
/fpp badword check     # list active bots whose names fail the filter
/fpp badword update    # rename flagged bots with clean names
/fpp badword status    # show filter config and word counts
```

Permission: `fpp.badword`

---

## Swap System & Names

When the [Swap System](Swap-System) is enabled, bots periodically leave and rejoin with a **new name** drawn from the pool.  
A small `reconnect-chance` (configurable) causes the bot to rejoin with its **same name** instead - simulating a brief disconnect/reconnect.
