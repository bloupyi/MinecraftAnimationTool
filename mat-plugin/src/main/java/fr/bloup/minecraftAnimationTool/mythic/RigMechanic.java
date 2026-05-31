package fr.bloup.minecraftAnimationTool.mythic;

import fr.bloup.minecraftAnimationTool.api.AnimatedModel;
import fr.bloup.minecraftAnimationTool.api.MinecraftAnimationToolApi;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.INoTargetSkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import org.bukkit.Material;

/**
 * MythicMobs mechanic operating on an already-spawned rig (resolved by name / caster). One instance
 * is registered per mechanic name; the {@link Action} selects the behaviour.
 */
public class RigMechanic implements INoTargetSkill {

    public enum Action {PLAY, POSE, PAUSE, RESUME, STOP, RESET, SPEED, SCALE, REMOVE, MATERIAL}

    private final MinecraftAnimationToolApi api;
    private final Action action;

    private final String name;
    private final String animation;
    private final double time;
    private final double speed;
    private final float scale;
    private final String part;
    private final String material;

    public RigMechanic(MinecraftAnimationToolApi api, Action action, MythicLineConfig mlc) {
        this.api = api;
        this.action = action;
        this.name = mlc.getString(new String[]{"name", "n", "model", "m"}, "");
        this.animation = mlc.getString(new String[]{"animation", "anim", "a"}, "");
        this.time = mlc.getDouble(new String[]{"time", "t"}, 0.0);
        this.speed = mlc.getDouble(new String[]{"speed", "s"}, 1.0);
        this.scale = mlc.getFloat(new String[]{"scale", "sc"}, 1.0f);
        this.part = mlc.getString(new String[]{"part", "p"}, "");
        this.material = mlc.getString(new String[]{"material", "mat"}, "");
    }

    @Override
    public SkillResult cast(SkillMetadata data) {
        AnimatedModel model = MatMythicSupport.model(api, data, name);
        if (model == null) return SkillResult.CONDITION_FAILED;

        boolean ok;
        switch (action) {
            case PLAY:
                ok = model.play(animation);
                break;
            case POSE:
                ok = model.pose(animation, time);
                break;
            case PAUSE:
                model.pause();
                ok = true;
                break;
            case RESUME:
                model.resume();
                ok = true;
                break;
            case STOP:
                model.stop();
                ok = true;
                break;
            case RESET:
                model.reset();
                ok = true;
                break;
            case SPEED:
                ok = model.setSpeed(speed);
                break;
            case SCALE:
                ok = model.setScale(scale);
                break;
            case REMOVE:
                model.remove();
                ok = true;
                break;
            case MATERIAL: {
                Material mat = Material.matchMaterial(material);
                ok = mat != null && model.setPartMaterial(part, mat);
                break;
            }
            default:
                ok = false;
        }
        return ok ? SkillResult.SUCCESS : SkillResult.CONDITION_FAILED;
    }
}
