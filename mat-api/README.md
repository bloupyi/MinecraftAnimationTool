# MinecraftAnimationTool API

Free, public API for the **MinecraftAnimationTool** plugin. Compile against this to control animated
models from your own plugin. You do **not** need the paid plugin jar to build against this API; you
only need it installed on the server at runtime.

## Add the dependency (JitPack)

Maven:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.YOUR_GITHUB_USER</groupId>
    <artifactId>MinecraftAnimationTool-API</artifactId>
    <version>TAG</version> <!-- a git tag or commit hash -->
    <scope>provided</scope>
</dependency>
```

Gradle:

```groovy
repositories { maven { url 'https://jitpack.io' } }
dependencies { compileOnly 'com.github.YOUR_GITHUB_USER:MinecraftAnimationTool-API:TAG' }
```

## Use it

Declare the soft dependency in your `plugin.yml`:

```yaml
softdepend: [ MinecraftAnimationTool ]
```

Resolve the API through Bukkit's service manager (works with only this API on your classpath; returns
`null` when the paid plugin is not installed, so you can degrade gracefully):

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
model.play("roar", () -> model.play("idle")); // chain on end
```

Listen to clicks on a model:

```java
import fr.bloup.minecraftAnimationTool.events.ModelInteractEvent;

@EventHandler
public void onModelClick(ModelInteractEvent e) {
    if (e.getClickType() == ModelInteractEvent.ClickType.RIGHT) {
        AnimatedModel m = api.getModel(e.getModelUuid());
        // ...
    }
}
```

See `MinecraftAnimationToolApi` and `AnimatedModel` for the full surface (play/pose/pause/resume/stop/
reset/speed/scale, teleport/lookAt, part materials, spawn with scale, model lookup).
