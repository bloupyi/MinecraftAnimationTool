package fr.bloup.minecraftAnimationTool.api;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Public API of MinecraftAnimationTool for dependent plugins.
 *
 * <p>Obtain an instance with {@code MinecraftAnimationTool.getApi()} or through Bukkit's
 * {@code ServicesManager}:
 * <pre>{@code
 * MinecraftAnimationToolApi api = getServer().getServicesManager()
 *         .load(MinecraftAnimationToolApi.class);
 * AnimatedModel model = api.spawn("boss", "dragon", player.getLocation());
 * model.play("idle");
 * }</pre>
 *
 * <p>Declare {@code softdepend: [MinecraftAnimationTool]} (or {@code depend}) in your plugin.yml.
 */
public interface MinecraftAnimationToolApi {

    // ---- Caches / blueprints ----

    /** Names of every loaded cache (the models that can be spawned). */
    List<String> getCacheNames();

    /** Part names of a cache (one per cube; duplicate names get a {@code -N} suffix), or empty if unknown. */
    List<String> getPartNames(String cacheName);

    /** Animation names declared in a cache, or an empty list if unknown. */
    List<String> getAnimationNames(String cacheName);

    /** Rebuilds caches from the {@code blueprints/} folder and reloads them into memory. */
    void reloadCaches();

    /**
     * Permanently changes the block material of a single part (cube) in a cache: writes the cache
     * file, reloads it and respawns every entity using it. The part name is a value from
     * {@link #getPartNames(String)} (a raw numeric index is also accepted). Returns false if the
     * cache, part or material is invalid.
     */
    boolean setPartMaterial(String cacheName, String partName, Material material);

    // ---- Models (spawned entities) ----

    /**
     * Spawns a rigged model from the named cache. The given name is made unique automatically.
     * Returns the handle, or null if the cache or location is invalid.
     */
    AnimatedModel spawn(String name, String cacheName, Location location);

    /** Returns the model with this name, or null. */
    AnimatedModel getModel(String name);

    /** Returns the model with this uuid, or null. */
    AnimatedModel getModel(UUID uuid);

    /** A handle for every currently spawned model. */
    Collection<AnimatedModel> getModels();
}
