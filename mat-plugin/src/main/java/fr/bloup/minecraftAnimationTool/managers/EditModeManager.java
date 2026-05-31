package fr.bloup.minecraftAnimationTool.managers;

import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class EditModeManager implements Listener {

    private final MinecraftAnimationTool plugin;
    private final EntityManager entityManager;

    private final Map<UUID, Session> sessions = new HashMap<>();

    private static class Session {
        BukkitTask task;
        BlockDisplay glow;
        UUID aimedEntity;
        int aimedIndex = -1;
    }

    public boolean isEditing(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public void toggle(Player player) {
        Session existing = sessions.remove(player.getUniqueId());
        if (existing != null) {
            stop(existing);
            player.sendMessage("Edit mode disabled.");
            return;
        }
        Session session = new Session();
        sessions.put(player.getUniqueId(), session);
        session.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> updateHighlight(player, session), 0L, 2L);
        player.sendMessage("Edit mode enabled. Right-click a model part while holding a block to paint it. Run /mat edit again to stop.");
    }

    private void stop(Session session) {
        if (session.task != null) session.task.cancel();
        clearGlow(session);
    }

    public void disableAll() {
        for (Session session : sessions.values()) stop(session);
        sessions.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Session session = sessions.remove(event.getPlayer().getUniqueId());
        if (session != null) stop(session);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;

        EntityManager.RayHit hit = entityManager.raytrace(player);
        if (hit == null) return;
        event.setCancelled(true); // don't place/use the held block on the world

        ItemStack item = player.getInventory().getItemInMainHand();
        Material material = item.getType();
        if (material.isAir() || !material.isBlock()) {
            player.sendMessage("Hold a block to paint this part.");
            return;
        }

        String cacheName = hit.pojo().getCache().name();
        String partName = entityManager.elementLabelOf(hit.pojo().getCache(), hit.index());
        boolean ok = entityManager.editElementMaterial(cacheName, hit.index(), material);
        if (ok) {
            player.sendMessage("Painted part " + partName + " of '" + cacheName + "' with " + material.name() + ".");
        } else {
            player.sendMessage("Failed to edit part " + partName + " of '" + cacheName + "'.");
        }
        clearGlow(session); // entities were respawned, drop the stale highlight; it rebuilds next tick
    }

    private void updateHighlight(Player player, Session session) {
        if (!player.isOnline()) return;
        EntityManager.RayHit hit = entityManager.raytrace(player);
        if (hit == null) {
            clearGlow(session);
            return;
        }
        BlockDisplay src = hit.pojo().getBlockDisplays().get(hit.index());
        boolean sameTarget = session.glow != null && !session.glow.isDead()
                && hit.pojo().getUuid().equals(session.aimedEntity) && hit.index() == session.aimedIndex;
        if (sameTarget) {
            applyGlowTransform(session.glow, src);
        } else {
            clearGlow(session);
            spawnGlow(player, session, hit, src);
        }
    }

    private void spawnGlow(Player viewer, Session session, EntityManager.RayHit hit, BlockDisplay src) {
        Location base = hit.pojo().getRoot().getLocation();
        BlockDisplay glow = base.getWorld().spawn(base, BlockDisplay.class, bd -> {
            bd.setBlock(src.getBlock());
            applyGlowTransform(bd, src);
            bd.setGlowing(true);
            bd.setGlowColorOverride(Color.LIME);
            bd.setBrightness(new Display.Brightness(15, 15));
            bd.setPersistent(false);
        });
        // Show only to the aiming player by hiding the glow from everyone else.
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(viewer.getUniqueId())) other.hideEntity(plugin, glow);
        }
        session.glow = glow;
        session.aimedEntity = hit.pojo().getUuid();
        session.aimedIndex = hit.index();
    }

    private void applyGlowTransform(BlockDisplay glow, BlockDisplay src) {
        Transformation t = src.getTransformation();
        // Enlarge slightly so the copy encloses the real block (cleaner outline, no z-fighting).
        Vector3f scale = new Vector3f(t.getScale()).mul(1.04f);
        Vector3f shift = new Vector3f(t.getScale()).mul(0.02f);
        Vector3f translation = new Vector3f(t.getTranslation()).sub(shift);
        glow.setTransformation(new Transformation(translation, t.getLeftRotation(), scale, t.getRightRotation()));
    }

    private void clearGlow(Session session) {
        if (session.glow != null && !session.glow.isDead()) session.glow.remove();
        session.glow = null;
        session.aimedEntity = null;
        session.aimedIndex = -1;
    }
}
