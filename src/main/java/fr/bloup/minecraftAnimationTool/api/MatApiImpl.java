package fr.bloup.minecraftAnimationTool.api;

import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import fr.bloup.minecraftAnimationTool.managers.EntityManager;
import fr.bloup.minecraftAnimationTool.pojos.EntityPOJO;
import fr.bloup.minecraftAnimationTool.records.Cache;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class MatApiImpl implements MinecraftAnimationToolApi {
    private final MinecraftAnimationTool plugin;
    private final EntityManager entityManager;

    public MatApiImpl(MinecraftAnimationTool plugin, EntityManager entityManager) {
        this.plugin = plugin;
        this.entityManager = entityManager;
    }

    private Cache cache(String cacheName) {
        if (cacheName == null) return null;
        return plugin.getAllCaches().stream()
                .filter(c -> c.name().equals(cacheName))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<String> getCacheNames() {
        List<String> names = new ArrayList<>();
        for (Cache c : plugin.getAllCaches()) names.add(c.name());
        return names;
    }

    @Override
    public List<String> getPartNames(String cacheName) {
        Cache c = cache(cacheName);
        return c == null ? List.of() : entityManager.elementLabelList(c);
    }

    @Override
    public List<String> getAnimationNames(String cacheName) {
        Cache c = cache(cacheName);
        if (c == null) return List.of();
        List<String> names = new ArrayList<>();
        for (Cache.Animation a : c.animations()) names.add(a.name());
        return names;
    }

    @Override
    public void reloadCaches() {
        plugin.rebuildCaches();
    }

    @Override
    public boolean setPartMaterial(String cacheName, String partName, Material material) {
        Cache c = cache(cacheName);
        if (c == null || material == null || !material.isBlock()) return false;
        int index = entityManager.elementIndexByName(c, partName);
        if (index < 0) return false;
        return entityManager.editElementMaterial(cacheName, index, material);
    }

    @Override
    public AnimatedModel spawn(String name, String cacheName, Location location) {
        Cache c = cache(cacheName);
        if (c == null || name == null || location == null) return null;
        EntityPOJO pojo = entityManager.createEntity(entityManager.uniqueName(name), c);
        entityManager.spawnEntity(pojo, location);
        return new AnimatedModelImpl(plugin, entityManager, pojo.getUuid());
    }

    @Override
    public AnimatedModel getModel(String name) {
        EntityPOJO pojo = entityManager.getEntity(name);
        return pojo == null ? null : new AnimatedModelImpl(plugin, entityManager, pojo.getUuid());
    }

    @Override
    public AnimatedModel getModel(UUID uuid) {
        if (uuid == null) return null;
        EntityPOJO pojo = entityManager.getEntity(uuid);
        return pojo == null ? null : new AnimatedModelImpl(plugin, entityManager, uuid);
    }

    @Override
    public Collection<AnimatedModel> getModels() {
        List<AnimatedModel> models = new ArrayList<>();
        for (EntityPOJO pojo : plugin.getEntityPOJOS()) {
            models.add(new AnimatedModelImpl(plugin, entityManager, pojo.getUuid()));
        }
        return models;
    }
}
