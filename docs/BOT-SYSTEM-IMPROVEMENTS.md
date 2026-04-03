# Bot System Improvements - Based on hello09x/fakeplayer

## Overview
This update recodes the bot system's network layer, physics handling, and movement architecture by incorporating proven patterns from the hello09x/fakeplayer plugin while maintaining compatibility with our existing codebase.

## What Was Changed

### 1. New Network Layer (`fakeplayer/network/`)

#### `FakeChannel.java`
- **Purpose**: A minimal Netty channel implementation that pretends to be connected but discards all I/O
- **Key Features**:
  - Extends `AbstractChannel` for full Netty compatibility
  - Returns `true` for `isActive()` and `isOpen()` checks
  - `doWrite()` drains the buffer without sending anything
  - Uses a shared `DefaultEventLoop` for all fake channels (efficient)
  - Provides proper `ChannelMetadata` and socket address information

#### `FakeChannelPipeline.java`
- **Purpose**: No-op pipeline that prevents any packet processing
- **Key Features**:
  - All pipeline operations return silently (no-op)
  - Add/remove handler methods do nothing
  - Fire* methods return `this` for chaining
  - All futures immediately succeed
  - Empty iterator implementation

#### `FakeConnection.java`
- **Purpose**: Minecraft `Connection` wrapper that uses our FakeChannel
- **Key Features**:
  - Extends NMS `Connection` class
  - Always returns `isConnected() = true`
  - All `send()` methods discard packets
  - Properly configures packet serialization pipeline
  - Uses localhost IP address for connection metadata

#### `FakeServerGamePacketListener.java`
- **Purpose**: Enhanced packet listener that handles physics packets bots can't process
- **Key Features**:
  - Extends `ServerGamePacketListenerImpl`
  - Intercepts `ClientboundSetEntityMotionPacket` (knockback/motion)
  - Manually applies knockback when bots are hit (real clients do this automatically)
  - Uses `player.lerpMotion()` for smooth motion application
  - Schedules physics updates on main thread (Bukkit requirement)
  - Includes debug logging for troubleshooting

### 2. Bot Physics Wrapper (`BotPhysics.java`)

- **Purpose**: Clean API for controlling bot movement and physics
- **Inspired By**: hello09x/fakeplayer's `NMSServerPlayer` interface
- **Features**:
  - Position getters: `getX()`, `getY()`, `getZ()`
  - Previous position setters: `setXo()`, `setYo()`, `setZo()` (for smooth interpolation)
  - Rotation: `getYRot()`/`setYRot()` (yaw), `getXRot()`/`setXRot()` (pitch)
  - Movement input: `getZza()`/`setZza()` (forward/back), `getXxa()`/`setXxa()` (strafe)
  - Velocity: `setDeltaMovement(Vector)`, `getDeltaMovement()`
  - Physics simulation: `doTick()` - runs one tick of movement/gravity/collisions
  - Ground state: `onGround()`, `jumpFromGround()`, `setJumping()`
  - Riding: `startRiding()`, `stopRiding()`
  - Actions: `isUsingItem()`, `resetLastActionTime()`, `drop()`

### 3. Improved NmsPlayerSpawner

#### New Methods
- `trySetupImprovedConnection()`: Attempts to use the new FakeConnection/FakeServerGamePacketListener system
- `setupLegacyConnection()`: Original reflection-based connection setup (fallback)

#### Updated Methods  
- `setupFakeConnection()`: Now tries improved network layer first, falls back to legacy if it fails

#### Benefits
- Better knockback/motion handling (bots respond correctly when hit)
- Cleaner code (less reflection, more type-safe)
- Backward compatible (falls back to legacy method if new system fails)
- Easier to debug (typed classes instead of reflection proxies)

## How It Works

### Knockback Physics (Main Improvement)

**Problem**: When a player is hit, the server sends `ClientboundSetEntityMotionPacket` telling the client to apply knockback. Real clients handle this automatically, but fake players have no client.

**Old Behavior**: Bots didn't respond to knockback, looked unnatural

**New Behavior**: 
1. `FakeServerGamePacketListener.send()` intercepts motion packets
2. Checks if packet is for our bot (`packet.getId() == this.player.getId()`)
3. Checks if bot is hurt (`this.player.hurtMarked`)
4. Schedules motion application on main thread (Bukkit requirement)
5. Re-marks bot as hurt and applies motion via `player.lerpMotion()`
6. Bot smoothly moves in response to damage

### Network Layer Flow

1. **Spawn Request** → `NmsPlayerSpawner.spawnFakePlayer()`
2. **Create Player** → NMS `ServerPlayer` constructor
3. **Setup Connection** → `setupFakeConnection()`:
   - First tries `trySetupImprovedConnection()`:
     - Creates `FakeConnection` (uses `FakeChannel` + `FakeChannelPipeline`)
     - Creates `FakeServerGamePacketListener` (handles physics packets)
     - Constructor auto-sets `player.connection = packetListener`
   - Falls back to `setupLegacyConnection()` if that fails:
     - Uses reflection and proxies (original method)
4. **Add to World** → `PlayerList.placeNewPlayer()`
5. **Physics Tick** → `FakeServerGamePacketListener` intercepts packets

## Testing Recommendations

1. **Knockback Test**: Hit a bot with a sword/punch enchant - should be pushed back
2. **Motion Test**: Spawn bot on edge, push it off - should fall naturally
3. **Compatibility Test**: Test on different Paper versions to ensure fallback works
4. **Stress Test**: Spawn many bots, ensure no memory leaks from channel objects

## Debug Tips

Enable debug logging to see which connection method is used:
```yaml
debug: true
# or
logging:
  debug:
    network: true
    packets: true
```

Look for these messages:
- `Using improved FakeConnection network layer` - New system active
- `Falling back to legacy connection setup` - Old system active
- `Applied knockback to bot X` - Physics packet handled

## Future Improvements

Potential enhancements based on hello09x/fakeplayer patterns:

1. **Bot Actions**: Add mining, attacking, item use (see `fakeplayer-v1_21_9/action/` classes)
2. **Better Movement**: Add pathfinding, jumping, swimming (see `ActionTicker` implementations)
3. **Client Settings**: More realistic client info (see `ClientInformation` setup)
4. **Advancement Handling**: Disable advancement triggers (see `FakePlayerAdvancements`)

## Credits

Network layer and physics handling inspired by:
- **hello09x/fakeplayer** - https://github.com/hello09x/fakeplayer
- Specifically: v1_21_9 implementation for latest Paper versions

## Compatibility

- **Minimum**: Paper 1.21.9+ (uses Mojang mappings)
- **Fallback**: Reflection-based legacy system for older versions
- **Tested**: Paper 1.21.11 (latest as of 2026-04-02)

