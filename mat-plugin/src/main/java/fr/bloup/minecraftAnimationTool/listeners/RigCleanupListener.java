package fr.bloup.minecraftAnimationTool.listeners;

import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import fr.bloup.minecraftAnimationTool.managers.EntityManager;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Removes orphaned rig entities (tagged with {@link MinecraftAnimationTool#getRigKey()}) that load in
 * from disk but are not part of any rig spawned this session - e.g. leftovers in a chunk that loads
 * after a crash where {@code onDisable} never ran. Entities we spawned are tracked as live and kept.
 */
@RequiredArgsConstructor
public class RigCleanupListener implements Listener {
    private final MinecraftAnimationTool plugin;
    private final EntityManager entityManager;

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!plugin.isPersistEnabled() || plugin.getRigKey() == null) return;
        for (Entity entity : event.getEntities()) {
            if (entity.getPersistentDataContainer().has(plugin.getRigKey(), PersistentDataType.STRING)
                    && !entityManager.isLiveEntity(entity.getUniqueId())) {
                entity.remove();
            }
        }
    }
}
