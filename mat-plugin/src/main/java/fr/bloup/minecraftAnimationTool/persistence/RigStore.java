package fr.bloup.minecraftAnimationTool.persistence;

import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import fr.bloup.minecraftAnimationTool.pojos.AnimationState;
import fr.bloup.minecraftAnimationTool.pojos.EntityPOJO;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persists the set of spawned rigs (and their current playback) to {@code rigs.yml} so they can be
 * restored after a server restart or plugin reload. Only metadata is stored; the actual entities are
 * despawned on disable and recreated on enable.
 */
public class RigStore {
    private final MinecraftAnimationTool plugin;
    private final File file;

    public RigStore(MinecraftAnimationTool plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "rigs.yml");
    }

    /** One persisted rig. {@code animName} is null when no animation is active. */
    public record SavedRig(UUID uuid, String name, String cacheName, float scale,
                           String worldName, double x, double y, double z, float yaw, float pitch,
                           String animName, double animTime, double animSpeed, boolean paused) {}

    /** Writes the current rigs to disk, replacing any previous contents. */
    public void save(List<EntityPOJO> pojos) {
        YamlConfiguration yml = new YamlConfiguration();
        ConfigurationSection root = yml.createSection("rigs");

        for (EntityPOJO pojo : pojos) {
            if (pojo.getRoot() == null || !pojo.getRoot().isValid()) continue;
            Location loc = pojo.getRoot().getLocation();
            if (loc.getWorld() == null) continue;

            ConfigurationSection s = root.createSection(pojo.getUuid().toString());
            s.set("name", pojo.getName());
            s.set("cache", pojo.getCache().name());
            s.set("scale", pojo.getScale());
            s.set("world", loc.getWorld().getName());
            s.set("x", loc.getX());
            s.set("y", loc.getY());
            s.set("z", loc.getZ());
            s.set("yaw", loc.getYaw());
            s.set("pitch", loc.getPitch());

            AnimationState st = pojo.getAnimationState();
            if (st != null) {
                s.set("anim", st.getAnimationName());
                s.set("time", st.getTime());
                s.set("speed", st.getSpeed());
                s.set("paused", st.isPaused());
            } else if (pojo.getHeldAnimation() != null) {
                // No live playback, but the rig is holding a finished / stopped frame: persist it as
                // a paused pose so the same frame is restored instead of snapping back to bind pose.
                s.set("anim", pojo.getHeldAnimation());
                s.set("time", pojo.getHeldTime());
                s.set("speed", 1.0);
                s.set("paused", true);
            }
        }

        try {
            yml.save(file);
        } catch (Exception e) {
            plugin.log.warning("persist: failed to write " + file.getName() + ": " + e.getMessage());
        }
    }

    /** Reads the persisted rigs, or an empty list when the file is absent or unreadable. */
    public List<SavedRig> load() {
        List<SavedRig> out = new ArrayList<>();
        if (!file.exists()) return out;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("rigs");
        if (root == null) return out;

        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            String world = s.getString("world");
            String cache = s.getString("cache");
            String name = s.getString("name", key);
            if (world == null || cache == null) continue;

            out.add(new SavedRig(
                    uuid, name, cache, (float) s.getDouble("scale", 1.0),
                    world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                    (float) s.getDouble("yaw"), (float) s.getDouble("pitch"),
                    s.getString("anim"), s.getDouble("time"), s.getDouble("speed", 1.0),
                    s.getBoolean("paused")));
        }
        return out;
    }
}
