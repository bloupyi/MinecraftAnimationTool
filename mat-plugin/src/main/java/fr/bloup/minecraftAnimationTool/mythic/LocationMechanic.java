package fr.bloup.minecraftAnimationTool.mythic;

import fr.bloup.minecraftAnimationTool.api.AnimatedModel;
import fr.bloup.minecraftAnimationTool.api.MinecraftAnimationToolApi;
import io.lumine.mythic.api.adapters.AbstractLocation;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.ITargetedLocationSkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.bukkit.BukkitAdapter;
import org.bukkit.Location;

/**
 * MythicMobs mechanic that needs a location target ({@code @origin}, {@code @self}, {@code @target}...).
 * Spawns a rig at the location, or moves / orients an existing rig toward it.
 */
public class LocationMechanic implements ITargetedLocationSkill {

    public enum Action {SPAWN, TP, LOOKAT}

    private final MinecraftAnimationToolApi api;
    private final Action action;

    private final String name;
    private final String cache;
    private final String animation;
    private final float scale;
    private final boolean replace;

    public LocationMechanic(MinecraftAnimationToolApi api, Action action, MythicLineConfig mlc) {
        this.api = api;
        this.action = action;
        this.name = mlc.getString(new String[]{"name", "n"}, "");
        this.cache = mlc.getString(new String[]{"model", "cache", "m"}, "");
        this.animation = mlc.getString(new String[]{"animation", "anim", "a"}, "");
        this.scale = mlc.getFloat(new String[]{"scale", "sc"}, 1.0f);
        this.replace = mlc.getBoolean(new String[]{"replace", "r"}, true);
    }

    @Override
    public SkillResult castAtLocation(SkillMetadata data, AbstractLocation target) {
        Location loc = BukkitAdapter.adapt(target);
        if (loc == null || loc.getWorld() == null) return SkillResult.CONDITION_FAILED;

        switch (action) {
            case SPAWN: {
                String resolved = MatMythicSupport.resolveName(data, name);
                if (resolved == null || cache.isEmpty()) return SkillResult.CONDITION_FAILED;
                if (replace) {
                    AnimatedModel existing = api.getModel(resolved);
                    if (existing != null) existing.remove();
                }
                AnimatedModel model = api.spawn(resolved, cache, loc, scale);
                if (model == null) return SkillResult.CONDITION_FAILED;
                if (!animation.isEmpty()) model.play(animation);
                return SkillResult.SUCCESS;
            }
            case TP: {
                AnimatedModel model = MatMythicSupport.model(api, data, name);
                if (model == null) return SkillResult.CONDITION_FAILED;
                model.teleport(loc);
                return SkillResult.SUCCESS;
            }
            case LOOKAT: {
                AnimatedModel model = MatMythicSupport.model(api, data, name);
                if (model == null) return SkillResult.CONDITION_FAILED;
                model.lookAt(loc);
                return SkillResult.SUCCESS;
            }
            default:
                return SkillResult.CONDITION_FAILED;
        }
    }
}
