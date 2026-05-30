package fr.bloup.minecraftAnimationTool.managers;

import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import fr.bloup.minecraftAnimationTool.pojos.EntityPOJO;
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
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
public class EditModeManager implements Listener {
    private static final double REACH = 6.0;

    private final MinecraftAnimationTool plugin;
    private final EntityManager entityManager;

    private final Map<UUID, Session> sessions = new HashMap<>();

    private static class Session {
        BukkitTask task;
        BlockDisplay glow;
        UUID aimedEntity;
        int aimedIndex = -1;
    }

    private record Hit(EntityPOJO pojo, int index) {}

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

        Hit hit = raytrace(player);
        if (hit == null) return;
        event.setCancelled(true); // don't place/use the held block on the world

        ItemStack item = player.getInventory().getItemInMainHand();
        Material material = item.getType();
        if (material.isAir() || !material.isBlock()) {
            player.sendMessage("Hold a block to paint this part.");
            return;
        }

        String cacheName = hit.pojo().getCache().name();
        String boneName = entityManager.boneNameOf(hit.pojo().getCache(), hit.index());
        boolean ok = entityManager.editElementMaterial(cacheName, hit.index(), material);
        if (ok) {
            player.sendMessage("Painted bone " + boneName + " of '" + cacheName + "' with " + material.name() + ".");
        } else {
            player.sendMessage("Failed to edit bone " + boneName + " of '" + cacheName + "'.");
        }
        clearGlow(session); // entities were respawned, drop the stale highlight; it rebuilds next tick
    }

    private void updateHighlight(Player player, Session session) {
        if (!player.isOnline()) return;
        Hit hit = raytrace(player);
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

    private void spawnGlow(Player viewer, Session session, Hit hit, BlockDisplay src) {
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

    /** Returns the nearest model element the player is looking at within reach, or null. */
    private Hit raytrace(Player player) {
        Location eye = player.getEyeLocation();
        Vector vEye = eye.toVector();
        Vector vDir = eye.getDirection();
        Vector3f origin = new Vector3f((float) vEye.getX(), (float) vEye.getY(), (float) vEye.getZ());
        Vector3f dir = new Vector3f((float) vDir.getX(), (float) vDir.getY(), (float) vDir.getZ());

        Hit best = null;
        double bestT = REACH;

        for (EntityPOJO pojo : plugin.getEntityPOJOS()) {
            if (pojo.getRoot() == null || !pojo.getRoot().isValid()) continue;
            Location base = pojo.getRoot().getLocation();
            if (base.getWorld() == null || !base.getWorld().equals(player.getWorld())) continue;

            List<BlockDisplay> displays = pojo.getBlockDisplays();
            for (int i = 0; i < displays.size(); i++) {
                BlockDisplay display = displays.get(i);
                if (display == null || display.isDead()) continue;
                Transformation t = display.getTransformation();

                // World matrix of this block's unit cube: T(base+translation) * leftRot * scale * rightRot.
                Matrix4f m = new Matrix4f()
                        .translate((float) base.getX() + t.getTranslation().x,
                                (float) base.getY() + t.getTranslation().y,
                                (float) base.getZ() + t.getTranslation().z)
                        .rotate(t.getLeftRotation())
                        .scale(t.getScale())
                        .rotate(t.getRightRotation());
                Matrix4f inv = m.invert(new Matrix4f());

                // Transform the world ray into the cube's local [0,1] space; t along the ray is preserved.
                Vector3f localOrigin = inv.transformPosition(origin, new Vector3f());
                Vector3f localDir = inv.transformDirection(dir, new Vector3f());

                Double tHit = rayUnitBox(localOrigin, localDir);
                if (tHit != null && tHit >= 0 && tHit < bestT) {
                    bestT = tHit;
                    best = new Hit(pojo, i);
                }
            }
        }
        return best;
    }

    /** Slab test of a ray against the axis-aligned unit cube [0,1]^3. Returns the near hit distance. */
    private Double rayUnitBox(Vector3f o, Vector3f d) {
        double tmin = 0.0;
        double tmax = Double.MAX_VALUE;
        float[] oo = {o.x, o.y, o.z};
        float[] dd = {d.x, d.y, d.z};
        for (int a = 0; a < 3; a++) {
            if (Math.abs(dd[a]) < 1e-8) {
                if (oo[a] < 0 || oo[a] > 1) return null;
            } else {
                double inv = 1.0 / dd[a];
                double t1 = (0 - oo[a]) * inv;
                double t2 = (1 - oo[a]) * inv;
                if (t1 > t2) {
                    double tmp = t1;
                    t1 = t2;
                    t2 = tmp;
                }
                tmin = Math.max(tmin, t1);
                tmax = Math.min(tmax, t2);
                if (tmin > tmax) return null;
            }
        }
        return tmin;
    }
}
