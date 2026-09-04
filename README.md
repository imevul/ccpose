# ccpose

A [CC: Tweaked](https://tweaked.cc/) API that exposes live player and computer poses to Lua.

Free to include in modpacks (MIT).

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.x
- CC: Tweaked 1.116+ (tested with 1.120.0)

## Install

Put `ccpose-1.21.1-*.jar` in the `mods/` folder of the instance that loads the world (the server, or your client for singleplayer). Build with:

```bash
./gradlew build
```

The artifact is `build/libs/ccpose-1.21.1-<version>.jar`.

Players joining a server that already has ccpose do **not** need the jar. Install it on the client only if you want the API in singleplayer worlds.

## Lua API

The API is injected as the global `pose`.

### Players

```lua
local me = pose.self()
-- { name, x, y, z, yaw, pitch, dimension }

local online = pose.online("Steve")
local steve = pose.get("Steve")
local players = pose.list()
```

`self` is the player wearing or holding this computer (pocket computers and similar). It returns `nil` when that cannot be determined. `get` and `online` take a player name (case-insensitive). Missing or offline players yield `nil` / `false`.

### Computers

```lua
local here = pose.here()
-- { id, label?, type, x, y, z, facing?, yaw?, dimension? }

local other = pose.computer(7)
local named = pose.computer("storage")
local all = pose.computers()
```

`type` is `"turtle"`, `"pocket"`, or `"computer"`. `computer` accepts a numeric id or a label (case-insensitive). Only computers that are currently running and have a known position are included. Pocket computers have no `facing` / `yaw`.
