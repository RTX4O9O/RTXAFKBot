# 🔤 Random Name Generator

> **Generate realistic Minecraft-style usernames on the fly — v1.6.6.7**

When `bot-name.mode` is set to `random`, FPP uses an internal `RandomNameGenerator` to produce believable Minecraft usernames instead of picking from `bot-names.yml`.

---

## ⚙️ Enabling Random Names

```yaml
# config.yml
bot-name:
  mode: random
  admin-format: '{bot_name}'
  user-format: 'bot-{spawner}-{num}'
```

| Mode | Behavior |
|------|----------|
| `random` | Generate a realistic username on the fly |
| `pool` | Pick from the list in `bot-names.yml` |

---

## 🎲 How Names Are Generated

The generator assembles names from realistic Minecraft-style syllables and patterns:

- Vowel-consonant blending for pronounceable handles
- Common gaming prefixes/suffixes
- Random numbers appended when needed for uniqueness
- Duplicate detection against existing bots and online players

Because names are generated procedurally, the pool is effectively infinite and you will rarely see collisions.

---

## 🔒 Uniqueness Guarantees

Before a generated name is accepted, FPP checks:

1. No active bot already has this name.
2. No real online player currently uses this name.
3. The name passes the badword filter (if enabled).

If a collision occurs, the generator retries with a new seed up to a capped number of attempts, then falls back to a numbered suffix.

---

## 📝 Format Placeholders

Even in `random` mode, `admin-format` and `user-format` still apply. The `{bot_name}` placeholder is replaced by the generated random name.

Example:
- `admin-format: '{bot_name}'` → `CraftyFox_42`
- `user-format: '{spawner}-{bot_name}'` → `Steve-CraftyFox_42`

---

## 🔄 Comparison: `pool` vs `random`

| Feature | `pool` mode | `random` mode |
|---------|-------------|---------------|
| Source | `bot-names.yml` | Procedural generator |
| Pool size | Finite (your list) | Effectively infinite |
| Thematic control | Full (you define names) | Medium (built-in syllable sets) |
| Setup effort | Must maintain a list | Zero maintenance |
| Collision risk | Higher with many bots | Very low |
| Best for | Themed servers, lore names | Quick scaling, generic bots |

---

## 🛠️ Admin Commands

Random names respect the same spawn commands as always:

```text
/fpp spawn 5
```

With `mode: random`, all 5 bots receive unique generated names.

---

## 🔗 Related Pages

- [Bot-Names](Bot-Names)
- [Configuration](Configuration)
- [Commands](Commands)
