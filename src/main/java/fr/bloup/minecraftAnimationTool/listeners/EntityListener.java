package fr.bloup.minecraftAnimationTool.listeners;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import fr.bloup.minecraftAnimationTool.managers.EntityManager;
import fr.bloup.minecraftAnimationTool.pojos.EntityPOJO;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

@RequiredArgsConstructor
public class EntityListener implements Listener {
    private final MinecraftAnimationTool plugin;
    private final EntityManager entityManager;

    @EventHandler
    public void onEntityRemove(EntityRemoveFromWorldEvent event) {
        UUID removedId = event.getEntity().getUniqueId();
        for (EntityPOJO pojo : plugin.getEntityPOJOS()) {
            if (pojo.getRoot() != null && pojo.getRoot().getUniqueId().equals(removedId)) {
                entityManager.removeEntity(pojo);
                break;
            }
        }
    }
}
