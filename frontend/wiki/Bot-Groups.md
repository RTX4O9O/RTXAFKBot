# 👥 Bot Groups

> **Personal bot groups with GUI management — v1.6.6.7**

Bot groups let players organize their bots into named collections. Once a group is created, you can issue bulk commands to every bot in the group at once.

---

## ⌨️ Command Syntax

```text
/fpp groups
/fpp groups create <name>
/fpp groups delete <name>
/fpp groups add <group> <bot>
/fpp groups remove <group> <bot>
/fpp groups list
/fpp groups info <group>
/fpp groups cmd <group> <command>
```

| Subcommand | Description |
|------------|-------------|
| `create <name>` | Create a new empty group |
| `delete <name>` | Delete a group (bots are not despawned) |
| `add <group> <bot>` | Add a bot to a group |
| `remove <group> <bot>` | Remove a bot from a group |
| `list` | Show all your groups and member counts |
| `info <group>` | Show members and stored bulk command of a group |
| `cmd <group> <command>` | Store a bulk command string for the group |

---

## 🖥️ GUI Management

Running `/fpp groups` without arguments opens the group management GUI:

- Click a group to view its members.
- Click a member to remove it.
- Use navigation buttons to create or delete groups.
- Bulk-command strings can be edited in the GUI as well.

---

## 🎯 Bulk Commands

Once a group has a stored command, you can run it on all members:

```text
/fpp groups cmd miners "fpp mine %bot%"
```

The `%bot%` placeholder is replaced with each member's name. You can then trigger execution through integrations or external plugins, or simply use the group as an organizational tool.

---

## 📝 Example Workflow

```text
/fpp groups create miners
/fpp groups add miners SteveBot
/fpp groups add miners AlexBot
/fpp groups list
/fpp groups info miners
/fpp groups cmd miners "fpp mine %bot%"
```

---

## 🔐 Permission

| Permission | Description |
|------------|-------------|
| `fpp.groups` | Use `/fpp groups` and the group GUI |

---

## 💾 Persistence

Groups are stored in the FPP database (`fpp_bot_groups` table) and survive restarts. If a bot is despawned, it is automatically removed from any groups it belonged to.

---

## 🔗 Related Pages

- [Commands](Commands)
- [Permissions](Permissions)
- [Configuration](Configuration)
