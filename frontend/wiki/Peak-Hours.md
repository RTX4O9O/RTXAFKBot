# ⏰ Peak Hours

Peak-hours automatically adjusts how many AFK bots are online based on real-world time windows.

It is designed to mimic:
- quiet overnight periods
- daytime medium activity
- evening peaks
- weekend surges

---

## Requirements

Peak-hours depends on the swap system.

```yaml
swap:
  enabled: true

peak-hours:
  enabled: true
```

Without `swap.enabled: true`, peak-hours cannot manage the bot pool correctly.

---

## Commands

```text
/fpp peaks
/fpp peaks on
/fpp peaks off
/fpp peaks status
/fpp peaks next
/fpp peaks force
/fpp peaks list
/fpp peaks wake [name]
/fpp peaks sleep <name>
```

Permission: `fpp.peaks`

---

## Core Configuration

```yaml
peak-hours:
  enabled: false
  timezone: "UTC"
  stagger-seconds: 30
  min-online: 0
  notify-transitions: false
  schedule:
    - { start: "06:00", end: "09:00", fraction: 0.30 }
    - { start: "09:00", end: "18:00", fraction: 0.75 }
    - { start: "18:00", end: "22:00", fraction: 1.00 }
    - { start: "22:00", end: "06:00", fraction: 0.05 }
```

### Meaning of `fraction`

- `1.0` → all bots online
- `0.5` → half the pool online
- `0.0` → no AFK bots online

---

## How It Works

Every 60 seconds, FPP:
1. checks the configured timezone
2. finds the matching window
3. computes a target fraction of the AFK bot pool
4. gradually wakes or sleeps bots to match that target

Changes are staggered over `stagger-seconds` so joins/leaves do not happen all at once.

---

## Schedule Rules

### Daily schedule

Entries are checked top-to-bottom.

The first matching window wins.

### Midnight-crossing windows

Windows like `22:00 -> 06:00` work automatically.

### Day overrides

Use uppercase day keys:
- `MONDAY`
- `TUESDAY`
- `WEDNESDAY`
- `THURSDAY`
- `FRIDAY`
- `SATURDAY`
- `SUNDAY`

A day override completely replaces the normal `schedule` for that day.

---

## Settings Reference

### `timezone`

```yaml
timezone: "UTC"
```

Any valid Java `ZoneId`, for example:
- `UTC`
- `America/New_York`
- `Europe/London`
- `Asia/Tokyo`

### `stagger-seconds`

```yaml
stagger-seconds: 30
```

Spreads the transition over this many seconds.

### `min-online`

```yaml
min-online: 0
```

Hard floor: at least this many AFK bots remain online even if the active fraction would otherwise go lower.

### `notify-transitions`

```yaml
notify-transitions: false
```

When enabled, players with `fpp.peaks` are notified when the active window changes.

---

## Sleeping Bots

When peak-hours needs fewer bots online:
- selected AFK bots are put to sleep
- they are quietly removed from the active world/tab state
- their saved location is kept for re-wake

When the active fraction rises:
- sleeping bots are re-spawned
- wake order follows the saved queue behavior

---

## Crash-Safe Persistence

This is an important current behavior.

Sleeping bots **are persisted separately** when DB is enabled.

They are stored in:
- `fpp_sleeping_bots`

That means if the server crashes or restarts unexpectedly, peak-hours can rebuild the sleeping queue instead of losing track of which bots were intentionally asleep.

---

## Shutdown / Reload Behavior

### On shutdown

Peak-hours wakes sleeping bots **before** normal persistence save so the full bot pool is captured correctly.

### On reload

`/fpp reload` causes peak-hours to:
1. wake all sleeping bots
2. reset transition state
3. start evaluating again using the new config

This prevents stale sleep-state issues after config changes.

---

## Interaction with Swap

Peak-hours works alongside the normal swap system.

Important behavior:
- swapped-out bots still count toward the overall bot pool
- this prevents short swap absences from tricking peak-hours into overcompensating
- if swap is disabled, peak-hours cannot keep running properly

Newer swap settings that often matter here:
- `swap.min-online`
- `swap.retry-rejoin`
- `swap.retry-delay`

---

## What Bots Are Managed?

Peak-hours only manages:
- **AFK bots**

It does **not** manage:
- PvP bots

---

## Diagnostics

### `/fpp peaks status`

Shows information like:
- current window
- current target fraction
- sleeping count
- total pool size
- time zone

### `/fpp peaks next`

Shows the next window change and how long until it applies.

### `/fpp peaks list`

Lists currently sleeping bots and their saved locations.

---

## Example Configuration

```yaml
swap:
  enabled: true
  session:
    min: 120
    max: 600
  absence:
    min: 15
    max: 60

peak-hours:
  enabled: true
  timezone: "America/New_York"
  stagger-seconds: 45
  min-online: 2
  notify-transitions: false
  schedule:
    - { start: "07:00", end: "12:00", fraction: 0.40 }
    - { start: "12:00", end: "17:00", fraction: 0.65 }
    - { start: "17:00", end: "22:00", fraction: 1.00 }
    - { start: "22:00", end: "01:00", fraction: 0.50 }
    - { start: "01:00", end: "07:00", fraction: 0.10 }
```

---

## Notes

- peak-hours does not bypass `limits.max-bots`
- it should be used for AFK population shaping, not PvP bot scheduling
- DB-backed sleeping persistence is strongly recommended for production servers

---

See also:
- [Swap-System](Swap-System.md)
- [Configuration](Configuration.md)
- [Database](Database.md)
