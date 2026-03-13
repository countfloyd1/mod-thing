# PlayerWatch — Fabric 1.21.1

A mod inspired by **What Are They Up To (Watut)** — see what other players on the server are doing, right above their heads!

## ✨ Features

| State | Indicator | Color |
|-------|-----------|-------|
| Typing in chat | `✏ typing...` (animated dots) | Yellow |
| In any GUI/menu | `📦 in menu` | Light blue |
| Idle (10+ seconds no movement) | `💤 idle` | Grey |

## 📋 Requirements

- **Minecraft:** 1.21.1
- **Mod Loader:** Fabric Loader ≥ 0.15.0
- **Fabric API:** Required
- **Java:** 21

> ⚠️ Must be installed on **both client and server** to work!

## 🛠️ Building from Source

### Prerequisites
- JDK 21 (e.g., [Adoptium](https://adoptium.net/))
- Internet connection (Gradle downloads dependencies on first build)

### Steps

```bash
# 1. Clone / extract the project
cd playerwatch

# 2. Build the mod
./gradlew build     # Linux/macOS
gradlew.bat build   # Windows

# 3. Find your .jar in:
build/libs/playerwatch-1.0.0.jar
```

### Setting up Gradle Wrapper (if missing)
```bash
gradle wrapper --gradle-version 8.8
```

## 📁 Project Structure

```
playerwatch/
├── src/
│   ├── main/java/com/playerwatch/
│   │   ├── PlayerWatchMod.java          ← Server entry point + packet handling
│   │   ├── common/
│   │   │   └── PlayerState.java         ← Enum: NORMAL, TYPING, IN_GUI, IDLE
│   │   ├── network/
│   │   │   └── PlayerWatchPackets.java  ← Packet IDs
│   │   └── mixin/
│   │       └── ServerPlayerEntityMixin.java
│   ├── main/resources/
│   │   ├── fabric.mod.json
│   │   ├── playerwatch.mixins.json
│   │   └── playerwatch.client.mixins.json
│   └── client/java/com/playerwatch/
│       ├── client/
│       │   └── PlayerWatchClient.java   ← Client entry point + renderer
│       └── mixin/client/
│           ├── ChatScreenMixin.java
│           └── GameRendererMixin.java
├── build.gradle
├── gradle.properties
└── settings.gradle
```

## ⚙️ How It Works

1. **Client** detects its own state each tick (typing → chat screen open, GUI → any screen open, idle → no movement for 10s)
2. **Client** sends a `state_update_c2s` packet to the server when state changes
3. **Server** receives it and broadcasts a `state_broadcast_s2c` packet to all other players
4. **Other clients** render a floating label above that player's head using `WorldRenderEvents.AFTER_ENTITIES`

## 🔧 Customization Ideas

- Adjust `IDLE_THRESHOLD_TICKS` in `PlayerWatchClient.java` (default: 200 = ~10 seconds)
- Change indicator colors by modifying the hex values in `getIndicatorText()`
- Add more states (e.g., sleeping, swimming) by extending `PlayerState`

## 📜 License

MIT — do whatever u want w it fr
