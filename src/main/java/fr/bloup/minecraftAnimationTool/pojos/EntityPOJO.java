package fr.bloup.minecraftAnimationTool.pojos;

import fr.bloup.minecraftAnimationTool.records.Cache;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;
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
    @Setter private BukkitTask animationTask;

    public EntityPOJO(String name, UUID uuid, Cache cache) {
        this.name = name;
        this.uuid = uuid;
        this.cache = cache;
    }
}
