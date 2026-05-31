package fr.bloup.minecraftAnimationTool.api;

import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import fr.bloup.minecraftAnimationTool.managers.EntityManager;
import fr.bloup.minecraftAnimationTool.pojos.EntityPOJO;
import fr.bloup.minecraftAnimationTool.records.Cache;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class AnimatedModelImpl implements AnimatedModel {
    private final MinecraftAnimationTool plugin;
    private final EntityManager entityManager;
    private final UUID uuid;

    AnimatedModelImpl(MinecraftAnimationTool plugin, EntityManager entityManager, UUID uuid) {
        this.plugin = plugin;
        this.entityManager = entityManager;
        this.uuid = uuid;
    }

    /** Resolves the live entity; survives respawns since the uuid is preserved. */
    private EntityPOJO pojo() {
        return entityManager.getEntity(uuid);
    }

    @Override
    public String getName() {
        EntityPOJO p = pojo();
        return p == null ? null : p.getName();
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    @Override
    public String getCacheName() {
        EntityPOJO p = pojo();
        return p == null ? null : p.getCache().name();
    }

    @Override
    public Location getLocation() {
        EntityPOJO p = pojo();
        if (p == null || p.getRoot() == null) return null;
        return p.getRoot().getLocation();
    }

    @Override
    public boolean isAlive() {
        EntityPOJO p = pojo();
        return p != null && p.getRoot() != null && p.getRoot().isValid();
    }

    @Override
    public List<String> getAnimationNames() {
        EntityPOJO p = pojo();
        if (p == null) return List.of();
        List<String> names = new ArrayList<>();
        for (Cache.Animation a : p.getCache().animations()) names.add(a.name());
        return names;
    }

    @Override
    public List<String> getPartNames() {
        EntityPOJO p = pojo();
        return p == null ? List.of() : entityManager.elementLabelList(p.getCache());
    }

    @Override
    public boolean play(String animationName) {
        EntityPOJO p = pojo();
        return p != null && entityManager.playAnimation(p, animationName);
    }

    @Override
    public boolean play(String animationName, Runnable onEnd) {
        EntityPOJO p = pojo();
        return p != null && entityManager.playAnimation(p, animationName,
                Math.max(0, plugin.getConfig().getInt("animation-blend-ticks", 4)), onEnd);
    }

    @Override
    public boolean pose(String animationName, double time) {
        EntityPOJO p = pojo();
        return p != null && entityManager.poseAt(p, animationName, time);
    }

    @Override
    public void pause() {
        EntityPOJO p = pojo();
        if (p != null) entityManager.pause(p);
    }

    @Override
    public void resume() {
        EntityPOJO p = pojo();
        if (p != null) entityManager.resume(p);
    }

    @Override
    public boolean setSpeed(double speed) {
        EntityPOJO p = pojo();
        return p != null && entityManager.setSpeed(p, speed);
    }

    @Override
    public String getCurrentAnimation() {
        EntityPOJO p = pojo();
        return p == null ? null : entityManager.getCurrentAnimationName(p);
    }

    @Override
    public boolean isPlaying() {
        EntityPOJO p = pojo();
        return p != null && entityManager.isPlaying(p);
    }

    @Override
    public float getScale() {
        EntityPOJO p = pojo();
        return p == null ? 1.0f : p.getScale();
    }

    @Override
    public boolean setScale(float scale) {
        EntityPOJO p = pojo();
        return p != null && entityManager.setScale(p, scale);
    }

    @Override
    public void stop() {
        EntityPOJO p = pojo();
        if (p != null) entityManager.stopAnimation(p);
    }

    @Override
    public void reset() {
        EntityPOJO p = pojo();
        if (p != null) entityManager.resetAnimation(p);
    }

    @Override
    public void teleport(Location location) {
        EntityPOJO p = pojo();
        if (p != null && location != null) entityManager.teleport(p, location);
    }

    @Override
    public void setRotation(float yaw, float pitch) {
        EntityPOJO p = pojo();
        if (p != null) entityManager.setRotation(p, yaw, pitch);
    }

    @Override
    public void lookAt(Location target) {
        EntityPOJO p = pojo();
        if (p == null || target == null || p.getRoot() == null) return;
        Location loc = p.getRoot().getLocation();
        Vector dir = target.toVector().subtract(loc.toVector());
        if (dir.lengthSquared() < 1.0e-6) return;
        Location facing = loc.setDirection(dir);
        entityManager.setRotation(p, facing.getYaw(), facing.getPitch());
    }

    @Override
    public boolean setPartMaterial(String partName, Material material) {
        EntityPOJO p = pojo();
        if (p == null || material == null || !material.isBlock()) return false;
        int index = entityManager.elementIndexByName(p.getCache(), partName);
        if (index < 0) return false;
        return entityManager.editElementMaterial(p.getCache().name(), index, material);
    }

    @Override
    public void remove() {
        EntityPOJO p = pojo();
        if (p != null) entityManager.removeEntity(p);
    }
}
