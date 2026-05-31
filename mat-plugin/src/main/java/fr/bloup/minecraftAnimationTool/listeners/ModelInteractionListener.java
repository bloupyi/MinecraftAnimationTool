package fr.bloup.minecraftAnimationTool.listeners;

import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import fr.bloup.minecraftAnimationTool.events.ModelInteractEvent;
import fr.bloup.minecraftAnimationTool.managers.EditModeManager;
import fr.bloup.minecraftAnimationTool.managers.EntityManager;
import fr.bloup.minecraftAnimationTool.pojos.EntityPOJO;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Turns left/right clicks aimed at a model part into a {@link ModelInteractEvent}. Skips players who
 * are in paint (edit) mode so the two features do not fight over the same click.
 */
@RequiredArgsConstructor
public class ModelInteractionListener implements Listener {
    private final MinecraftAnimationTool plugin;
    private final EntityManager entityManager;
    private final EditModeManager editModeManager;

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        ModelInteractEvent.ClickType type;
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            type = ModelInteractEvent.ClickType.LEFT;
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            type = ModelInteractEvent.ClickType.RIGHT;
        } else {
            return;
        }

        Player player = event.getPlayer();
        if (editModeManager.isEditing(player)) return;

        EntityManager.RayHit hit = entityManager.raytrace(player);
        if (hit == null) return;

        event.setCancelled(true); // clicked a model, don't place/break against the world

        EntityPOJO pojo = hit.pojo();
        String partName = entityManager.elementLabelOf(pojo.getCache(), hit.index());
        ModelInteractEvent modelEvent = new ModelInteractEvent(
                player, pojo.getUuid(), pojo.getName(), pojo.getCache().name(),
                hit.index(), partName, type);
        Bukkit.getPluginManager().callEvent(modelEvent);
    }
}
