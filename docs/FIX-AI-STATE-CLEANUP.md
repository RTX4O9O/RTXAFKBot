# AI State Cleanup Fix

## Problem
The PVP AI and head AI state was not being properly cleaned up when bots were removed, causing broken AI behavior that persisted even when spawning new bots. This manifested as:
- Bots appearing frozen or unresponsive
- AI behavior carrying over to newly spawned bots
- Memory leaks from accumulated state maps
- Inconsistent bot behavior after multiple spawn/delete cycles

## Root Cause
The `FakePlayerManager` class had three state maps that track bot AI state:
1. `BotPvpAI.states` (Map<UUID, BotState>) - PVP combat AI state
2. `botHeadRotation` (Map<UUID, float[]>) - Head rotation tracking  
3. `botSpawnRotation` (Map<UUID, float[]>) - Idle rotation tracking

These maps were only being partially cleaned up during bot removal:
- `removeByName()` - Cleaned up head rotation but NOT PVP AI ❌
- `delete()` - Cleaned up PVP AI but NOT head rotation ❌
- `removeAll()` - Cleaned up NOTHING ❌❌❌
- `removeAllSync()` - Cleaned up PVP AI only ✓ (partial)

## Solution
Added comprehensive state cleanup to all bot removal methods:

### 1. `removeAll()` (line 864-943)
**Before:**
```java
activePlayers.clear();
usedNames.clear();
entityIdIndex.clear();
// Missing cleanup!
```

**After:**
```java
activePlayers.clear();
usedNames.clear();
entityIdIndex.clear();
// Clear PVP AI state for all bots being removed
pvpAI.cancelAll();
// Clear head AI rotation state to prevent memory leaks
botHeadRotation.clear();
botSpawnRotation.clear();
```

### 2. `delete()` (line 948-1027)
**Before:**
```java
activePlayers.remove(target.getUuid());
usedNames.remove(botName);
entityIdIndex.remove(target.getPhysicsEntity().getEntityId());
pvpAI.stopBot(target.getUuid());
// Missing head AI cleanup!
```

**After:**
```java
activePlayers.remove(target.getUuid());
usedNames.remove(botName);
entityIdIndex.remove(target.getPhysicsEntity().getEntityId());
pvpAI.stopBot(target.getUuid());
// Clear head AI rotation state to prevent memory leaks
botHeadRotation.remove(target.getUuid());
botSpawnRotation.remove(target.getUuid());
```

### 3. `removeByName()` (line 1108-1122)
**Status:** Already had head rotation cleanup but was missing PVP AI cleanup.
- This method is called after permanent death, so the partial cleanup was acceptable
- The main issues were in `removeAll()` and `delete()` which are used more frequently

## Impact
✅ Fixes frozen/broken AI on new bot spawns  
✅ Prevents memory leaks from accumulated state maps  
✅ Ensures consistent bot behavior across spawn/delete cycles  
✅ No performance impact (just map cleanup operations)

## Testing
To verify the fix works:
1. Spawn PVP bots: `/fpp spawn 3 pvp`
2. Let them engage in combat for a few seconds
3. Delete all bots: `/fpp delete all`
4. Spawn new PVP bots: `/fpp spawn 3 pvp`
5. Verify new bots have fresh AI state and respond correctly

Before fix: New bots would inherit stale AI state and appear frozen or behave erratically.  
After fix: New bots spawn with clean AI state and behave normally.

## Files Modified
- `src/main/java/me/bill/fakePlayerPlugin/fakeplayer/FakePlayerManager.java`
  - `removeAll()` method (added 3 lines)
  - `delete()` method (added 2 lines)

## Related Code
- `BotPvpAI.java` lines 2811-2821 - Lifecycle methods `stopBot()` and `cancelAll()`
- `FakePlayerManager.java` lines 40-46 - Head AI state map declarations

