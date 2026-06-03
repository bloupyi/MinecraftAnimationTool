package fr.bloup.minecraftAnimationTool.listeners;

import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import fr.bloup.minecraftAnimationTool.managers.EntityManager;
import fr.bloup.minecraftAnimationTool.pojos.EntityPOJO;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;

import java.util.UUID;

@RequiredArgsConstructor
public class EntityListener implements Listener {
    private final MinecraftAnimationTool plugin;
    private final EntityManager entityManager;

    @EventHandler
    public void onEntityRemove(EntityRemoveEvent event) {
        // A chunk unload removes the entity from the world without deleting it (it is saved with the
        // chunk and a plugin chunk ticket normally keeps it loaded anyway). Tearing the rig down on
        // UNLOAD is what made rigs vanish on disconnect, so ignore that cause and keep the rig.
        if (event.getCause() == EntityRemoveEvent.Cause.UNLOAD) return;

        UUID removedId = event.getEntity().getUniqueId();
        for (EntityPOJO pojo : plugin.getEntityPOJOS()) {
            if (pojo.getRoot() != null && pojo.getRoot().getUniqueId().equals(removedId)) {
                UUID rigUuid = pojo.getUuid();
                // This event can fire from inside Paper's section/chunk status processing, during
                // which removing the sibling block displays synchronously is refused (logs a warning).
                // Defer the cascade by a tick; re-look up the rig so our own removal does not re-enter.
                // During shutdown the scheduler rejects new tasks; onDisable already tears rigs down.
                if (plugin.isEnabled()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        EntityPOJO live = entityManager.getEntity(rigUuid);
                        if (live != null) entityManager.removeEntity(live);
                    });
                }
                break;
            }
        }
    }
}
