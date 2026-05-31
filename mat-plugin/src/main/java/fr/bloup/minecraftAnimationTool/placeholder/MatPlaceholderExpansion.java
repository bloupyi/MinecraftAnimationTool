package fr.bloup.minecraftAnimationTool.placeholder;

import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import fr.bloup.minecraftAnimationTool.managers.EntityManager;
import fr.bloup.minecraftAnimationTool.pojos.EntityPOJO;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI expansion. Registered only when PlaceholderAPI is present.
 *
 * <p>Placeholders:
 * <ul>
 *   <li>{@code %mat_count%} - number of spawned models</li>
 *   <li>{@code %mat_animation_<name>%} - current animation of the model, or "none"</li>
 *   <li>{@code %mat_playing_<name>%} - "true"/"false" whether it is animating</li>
 *   <li>{@code %mat_cache_<name>%} - the cache the model was spawned from</li>
 *   <li>{@code %mat_scale_<name>%} - the model's uniform scale</li>
 * </ul>
 */
public class MatPlaceholderExpansion extends PlaceholderExpansion {
    private final MinecraftAnimationTool plugin;
    private final EntityManager entityManager;

    public MatPlaceholderExpansion(MinecraftAnimationTool plugin, EntityManager entityManager) {
        this.plugin = plugin;
        this.entityManager = entityManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "mat";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (params.equalsIgnoreCase("count")) {
            return String.valueOf(plugin.getEntityPOJOS().size());
        }

        int sep = params.indexOf('_');
        if (sep < 0) return null;
        String key = params.substring(0, sep).toLowerCase();
        String name = params.substring(sep + 1);

        EntityPOJO pojo = entityManager.getEntity(name);

        switch (key) {
            case "animation": {
                if (pojo == null) return "none";
                String anim = entityManager.getCurrentAnimationName(pojo);
                return anim == null ? "none" : anim;
            }
            case "playing":
                return String.valueOf(pojo != null && entityManager.isPlaying(pojo));
            case "cache":
                return pojo == null ? "" : pojo.getCache().name();
            case "scale":
                return pojo == null ? "" : String.valueOf(pojo.getScale());
            default:
                return null;
        }
    }
}
