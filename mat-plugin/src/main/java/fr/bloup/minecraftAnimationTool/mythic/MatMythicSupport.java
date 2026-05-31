package fr.bloup.minecraftAnimationTool.mythic;

import fr.bloup.minecraftAnimationTool.api.AnimatedModel;
import fr.bloup.minecraftAnimationTool.api.MinecraftAnimationToolApi;
import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.skills.SkillCaster;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.bukkit.BukkitAdapter;

/**
 * Helpers shared by the MythicMobs mechanics. Resolves the rig name a mechanic targets: an explicit
 * {@code name=} (with {@code <caster.uuid>} / {@code <caster.name>} substitution), or, when omitted,
 * a deterministic name owned by the casting mob ({@code mm_<caster uuid>}).
 */
final class MatMythicSupport {
    static final String CASTER_PREFIX = "mm_";

    private MatMythicSupport() {}

    static String resolveName(SkillMetadata data, String rawName) {
        AbstractEntity caster = casterEntity(data);
        String name = rawName == null ? "" : rawName.trim();

        if (name.isEmpty()) {
            return caster == null ? null : CASTER_PREFIX + caster.getUniqueId();
        }
        if (caster != null) {
            name = name.replace("<caster.uuid>", caster.getUniqueId().toString());
            String casterName = bukkitName(caster);
            if (casterName != null) name = name.replace("<caster.name>", casterName);
        }
        return name;
    }

    static AnimatedModel model(MinecraftAnimationToolApi api, SkillMetadata data, String rawName) {
        String name = resolveName(data, rawName);
        return name == null ? null : api.getModel(name);
    }

    private static AbstractEntity casterEntity(SkillMetadata data) {
        SkillCaster caster = data.getCaster();
        return caster == null ? null : caster.getEntity();
    }

    private static String bukkitName(AbstractEntity entity) {
        try {
            return BukkitAdapter.adapt(entity).getName();
        } catch (Exception e) {
            return null;
        }
    }
}
