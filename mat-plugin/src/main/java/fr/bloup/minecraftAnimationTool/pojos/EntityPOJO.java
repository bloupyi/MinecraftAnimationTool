package fr.bloup.minecraftAnimationTool.pojos;

import fr.bloup.minecraftAnimationTool.records.Cache;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.util.Transformation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class EntityPOJO {
    @Setter private String name;
    private final UUID uuid;
    private final Cache cache;
    @Setter private Entity root;
    @Setter private List<BlockDisplay> blockDisplays = new ArrayList<>();
    private final List<Transformation> baseTransformations = new ArrayList<>();

    /** Uniform scale of the whole rig, applied to bind pose and animation pose. */
    @Setter private float scale = 1.0f;

    /** Current playback state, or null when no animation is active. Driven by the global ticker. */
    @Setter private AnimationState animationState;

    public EntityPOJO(String name, UUID uuid, Cache cache) {
        this.name = name;
        this.uuid = uuid;
        this.cache = cache;
    }

    public EntityPOJO(String name, UUID uuid, Cache cache, float scale) {
        this(name, uuid, cache);
        this.scale = scale;
    }
}
