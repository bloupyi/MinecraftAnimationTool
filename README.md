# MinecraftAnimationTool

Turn **Blockbench** models into fully animated, in-world rigs on a Paper server, then drive them with
commands, a developer API, **MythicMobs** mechanics and **PlaceholderAPI** placeholders.

This page is the complete guide: from building a model in Blockbench to playing animations in game and
controlling rigs from your own plugin.

> **How rigs are rendered.** Each cube of your model becomes a Minecraft `BlockDisplay`, i.e. a real
> block. The plugin does **not** use Blockbench textures/UVs: you assign a Minecraft block material to
> each cube (default `STONE`). Design "blocky" models and pick a block per part. Bones, pivots,
> rotations, scaling and animations are fully respected.

---

## Table of contents

1. [Requirements](#requirements)
2. [Installation](#installation)
3. [Build a model in Blockbench](#build-a-model-in-blockbench)
4. [Import the model](#import-the-model)
5. [Spawn and animate](#spawn-and-animate)
6. [Give cubes their blocks (materials)](#give-cubes-their-blocks-materials)
7. [Command reference](#command-reference)
8. [Configuration](#configuration)
9. [Permissions](#permissions)
10. [Developer API](#developer-api)
11. [MythicMobs integration](#mythicmobs-integration)
12. [PlaceholderAPI](#placeholderapi)
13. [Troubleshooting](#troubleshooting)

---

## Requirements

- **Paper** 1.20.4+ (or a fork). Built against 1.20.4; tested only up to 1.21.4.
- **Java 17+** on the server.
- Optional: **MythicMobs** 5.x and **PlaceholderAPI** (auto-detected, no config needed).

## Installation

1. Drop `MinecraftAnimationTool-1.0.jar` into your server's `plugins/` folder.
2. Start the server once. The plugin creates:
   - `plugins/MinecraftAnimationTool/blueprints/` — put your `.bbmodel` files here.
   - `plugins/MinecraftAnimationTool/cache/` — generated, one slim `.json` per model (don't edit by hand).
   - `plugins/MinecraftAnimationTool/config.yml`.
3. That's it. Continue below to add your first model.

---

## Build a model in Blockbench

[Blockbench](https://www.blockbench.net/) is free. The plugin reads the model's **cubes**, **bone tree
(outliner)** and **animations** from the raw `.bbmodel` file.

### 1. Create the project
- `File > New > Generic Model` (or **Free Model**). Any format that lets you make cubes, group them and
  animate works. Avoid formats that forbid animations.

### 2. Model with cubes
- Build your model out of cubes. Scale matters: **16 Blockbench units = 1 block** in game.
- Keep it reasonably "blocky" — each cube is rendered as a single Minecraft block.

### 3. Group cubes into bones
- In the **Outliner**, put cubes into **groups**. Each group is a *bone* you can animate (e.g. `head`,
  `arm_left`, `leg_right`).
- Set each group's **pivot point** (the origin) where it should rotate from. This is essential: rotations
  happen around the bone pivot.

### 4. Animate
- Switch to the **Animate** tab and create an animation. **The animation name is what you'll use in
  `/mat play`** (e.g. `idle`, `walk`, `attack`).
- Add keyframes on bones for the **rotation**, **position** and **scale** channels.
- Set looping (`Loop` / `Once`) in the animation properties. Looping animations repeat automatically.
- **Easing**: per-keyframe easing and interpolation modes are supported (`linear`, `step`, smooth
  /catmullrom, and the full ease-in/out set). Set them on keyframes for smoother motion.
- **Math expressions**: keyframe values may use MoLang-style math, e.g. `math.sin(query.anim_time*120)*25`.
  Supported: `+ - * /`, parentheses, `sin cos tan abs sqrt log` (degrees), and the `query.anim_time` variable.

### 5. Sounds (optional)
- Add a **sound effect** keyframe on the animation timeline (effects/sound channel). Use a Minecraft sound
  name such as `ambient.cave` or `minecraft:ambient.cave` (both work). It plays at the rig's location when
  the playhead crosses that keyframe.

### 6. Save the `.bbmodel`
- Use **`File > Save Model`** to produce a `.bbmodel` file. (Do **not** use "Export" — the plugin reads the
  native `.bbmodel`.)

---

## Import the model

1. Copy your file, e.g. `dragon.bbmodel`, into `plugins/MinecraftAnimationTool/blueprints/`.
2. In game (or console) run:
   ```
   /mat reload
   ```
   This parses every `.bbmodel`, writes a cache, and loads it. **The cache name is the file name without
   extension** — `dragon.bbmodel` becomes the cache `dragon`.
3. Check it loaded: `/mat reload` prints `Loaded caches: N`.

---

## Spawn and animate

```
/mat spawn boss dragon            # spawn cache "dragon" as a rig named "boss" at your feet
/mat spawn boss dragon 2          # ... at 2x scale
/mat play boss idle               # play the "idle" animation (cross-fades from the current pose)
/mat play boss walk               # switch animation, smoothly blended
/mat pose boss attack 0.5         # freeze on the frame at 0.5s of "attack"
/mat pause boss                   # pause / hold the current frame
/mat resume boss
/mat stop boss                    # stop, keep current pose
/mat reset boss                   # stop, snap back to the bind pose
/mat tp boss ~ ~ ~ 90 0           # move/orient the rig (here: face yaw 90)
/mat info boss                    # show details
/mat list                         # list spawned rigs
/mat kill boss                    # remove the rig
```

Names are made unique automatically: spawning `boss` twice gives `boss` and `boss-2`.

---

## Give cubes their blocks (materials)

Fresh rigs are made of `STONE`. Two ways to assign a block per cube:

**Paint mode (visual, recommended):**
```
/mat edit
```
Toggles paint mode. Hold a block in your hand and **right-click** a cube of any rig to paint it. The cube
you're aiming at is highlighted. Run `/mat edit` again to stop. Changes are saved to the cache and applied
to every rig using that model.

**By command:**
```
/mat edit <cache> <part> <material>
# e.g.
/mat edit dragon head_1 RED_WOOL
```
`<part>` is a cube label (tab-completed) or a raw index. Material must be a placeable block.

Material assignments persist across `/mat reload`.

---

## Command reference

Base command: `/mat` (alias `/minecraftanimationtool`). Permission: `mat.command` (default: op).

| Command | Description |
|---|---|
| `/mat help` | List commands. |
| `/mat reload` | Reload `config.yml`, rebuild caches from `blueprints/`, reload them. |
| `/mat spawn <name> <cache> [scale]` | Spawn a rig at your location, optional uniform scale. |
| `/mat list` | List spawned rigs. |
| `/mat info <name>` | Show info about a rig. |
| `/mat play <name> <animation>` | Play an animation (cross-fades from the current pose). |
| `/mat pose <name> <animation> <time>` | Freeze on a single frame at `time` seconds. |
| `/mat pause <name>` / `/mat resume <name>` | Pause / resume the current animation. |
| `/mat stop <name>` | Stop the animation, keep the current pose. |
| `/mat reset <name>` | Stop and restore the initial (bind) pose. |
| `/mat tp <name> [x y z] [yaw pitch] [world]` | Move / orient a rig. |
| `/mat edit` | Toggle paint mode (right-click a cube with a block to paint it). |
| `/mat edit <cache> <part> <material>` | Set one cube's block material. |
| `/mat kill <name>` | Remove a rig. |

---

## Configuration

`plugins/MinecraftAnimationTool/config.yml`:

```yaml
# Ticks between each interpolation step the plugin computes (minimum 1). Also the period of the
# single global animation ticker.
interpolation-updates: 1

# When true, also use the client's native block-display tweening between the plugin's steps so motion
# looks smooth; when false, each step snaps.
use-minecraft-interpolation: true

# Ticks to cross-fade from the current pose into a newly played animation. 0 disables blending.
animation-blend-ticks: 4

# When no player is within this many blocks of a rig, the plugin stops pushing transform updates but
# keeps advancing animation time (so playback stays in sync and resumes seamlessly). 0 disables culling.
cull-distance: 0
```

Changing these and running `/mat reload` applies them live.

---

## Permissions

| Node | Default | Grants |
|---|---|---|
| `mat.command` | op | All `/mat` subcommands. |

---

## Developer API

Other plugins can spawn and control rigs **without bundling the plugin itself** — compile against the free API
below. At runtime the plugin must be installed on the server, otherwise the service is absent and
your code degrades gracefully.

### Add the dependency (JitPack)

Maven:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.bloupyi.MinecraftAnimationTool</groupId>
    <artifactId>MinecraftAnimationTool-API</artifactId>
    <version>v1.2</version> <!-- a git tag or commit hash -->
    <scope>provided</scope>
</dependency>
```

Gradle:

```groovy
repositories { maven { url 'https://jitpack.io' } }
dependencies { compileOnly 'com.github.bloupyi.MinecraftAnimationTool:MinecraftAnimationTool-API:v1.2' }
```

> The API lives in the `mat-api/` submodule of this repo, so the JitPack group is
> `com.github.bloupyi.MinecraftAnimationTool` (i.e. `com.github.<user>.<repo>`), not just
> `com.github.bloupyi`. JitPack builds only this module (see `jitpack.yml`); the full plugin is never published.

### Use it

Declare the soft dependency in your `plugin.yml`:

```yaml
softdepend: [ MinecraftAnimationTool ]
```

Resolve the API through Bukkit's service manager (works with only this API on your classpath; returns
`null` when the plugin is not installed):

```java
import fr.bloup.minecraftAnimationTool.api.MinecraftAnimationToolApi;
import fr.bloup.minecraftAnimationTool.api.AnimatedModel;

MinecraftAnimationToolApi api = getServer().getServicesManager()
        .load(MinecraftAnimationToolApi.class);
if (api == null) {
    getLogger().warning("MinecraftAnimationTool not installed; animation features disabled.");
    return;
}

AnimatedModel model = api.spawn("boss", "dragon", player.getLocation(), 2.0f);
model.play("idle");
model.play("roar", () -> model.play("idle")); // run a callback when a non-looping animation ends
```

`AnimatedModel` exposes: `play`, `play(anim, onEnd)`, `pose`, `pause`, `resume`, `stop`, `reset`,
`setSpeed`, `getCurrentAnimation`, `isPlaying`, `getScale`, `setScale`, `teleport`, `setRotation`,
`lookAt`, `setPartMaterial`, `getLocation`, `getAnimationNames`, `getPartNames`, `remove`.
`MinecraftAnimationToolApi` adds cache queries, `reloadCaches`, `setPartMaterial`, `spawn(..., scale)`
and model lookup (`getModel(name|uuid)`, `getModels`).

### Listen to clicks on a model

```java
import fr.bloup.minecraftAnimationTool.events.ModelInteractEvent;

@EventHandler
public void onModelClick(ModelInteractEvent e) {
    if (e.getClickType() == ModelInteractEvent.ClickType.RIGHT) {
        AnimatedModel m = api.getModel(e.getModelUuid());
        // e.getPartName(), e.getPartIndex(), e.getPlayer(), e.getCacheName()...
    }
}
```

---

## MythicMobs integration

When MythicMobs 5.x is installed, custom mechanics are registered automatically. The targeted rig is
resolved by `name=` (with `<caster.uuid>` / `<caster.name>` substitution), or, when `name` is omitted, a
deterministic rig **owned by the casting mob** (`mm_<caster uuid>`).

| Mechanic | Targeter | Args |
|---|---|---|
| `matspawn` | `@location` | `model=` (cache), `name=`, `scale=`, `anim=`, `replace=` (default true) |
| `mattp` | `@location` | `name=` |
| `matlookat` | `@location` | `name=` |
| `matplay` | — | `name=`, `anim=` |
| `matpose` | — | `name=`, `anim=`, `time=` |
| `matpause` / `matresume` | — | `name=` |
| `matstop` / `matreset` | — | `name=` |
| `matspeed` | — | `name=`, `speed=` |
| `matscale` | — | `name=`, `scale=` |
| `matremove` | — | `name=` |
| `matmaterial` | — | `name=`, `part=`, `material=` |

Example — a mob that wears a rig and roars on attack:

```yaml
Dragon:
  Type: ZOMBIE
  Options:
    Invisible: true
  Skills:
  - skill{s=DragonSpawn} ~onSpawn
  - skill{s=DragonRoar} ~onAttack
  - matremove{} ~onDeath

DragonSpawn:
  Skills:
  # no name -> rig owned by the mob (mm_<uuid>)
  - matspawn{model=dragon;scale=2;anim=idle} @origin

DragonRoar:
  Skills:
  - matplay{anim=roar} @self
  - delay 40
  - matplay{anim=idle} @self
```

Naming explicitly (placeholder) when you want several rigs or external control:

```yaml
- matspawn{model=dragon;name=boss_<caster.uuid>} @origin
- matplay{name=boss_<caster.uuid>;anim=fly} @self
```

---

## PlaceholderAPI

When PlaceholderAPI is installed, the `mat` expansion is registered:

| Placeholder | Returns |
|---|---|
| `%mat_count%` | Number of spawned rigs. |
| `%mat_animation_<name>%` | Current animation of the rig, or `none`. |
| `%mat_playing_<name>%` | `true` / `false`. |
| `%mat_cache_<name>%` | Cache the rig was spawned from. |
| `%mat_scale_<name>%` | The rig's uniform scale. |

---

## Troubleshooting

- **`Cache not found`** when spawning: run `/mat reload` after adding the `.bbmodel`; the cache name is the
  file name without `.bbmodel`.
- **Rig is all stone:** that's expected — assign blocks with `/mat edit` (paint mode) or
  `/mat edit <cache> <part> <material>`.
- **Animation does nothing / wrong rotations:** make sure cubes are inside **groups (bones)** with correct
  **pivot points**, and that the animation has keyframes on those bones. Re-save, delete the previous cache and `/mat reload`.
- **Animation name not found:** the name in `/mat play` must match the animation name in Blockbench exactly.
- **No sound:** the sound must be a valid Minecraft sound name (resource-pack sounds need the pack installed
  on clients). Both `ambient.cave` and `minecraft:ambient.cave` are accepted.
- **Rig stutters far away:** set `cull-distance` to skip rendering for distant rigs (time keeps advancing,
  so it stays in sync).
- **Model too big/small:** 16 Blockbench units = 1 block. Re-scale in Blockbench, or spawn with a `[scale]`.
