package fr.bloup.minecraftAnimationTool.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired when a player left- or right-clicks a part of a spawned model.
 *
 * <p>Dependent plugins listen to this to implement clickable rigs (NPCs, buttons, ...):
 * <pre>{@code
 * @EventHandler
 * public void onModelClick(ModelInteractEvent e) {
 *     if (e.getClickType() == ModelInteractEvent.ClickType.RIGHT) {
 *         AnimatedModel model = MinecraftAnimationTool.getApi().getModel(e.getModelUuid());
 *         // ...
 *     }
 * }
 * }</pre>
 */
public class ModelInteractEvent extends Event {

    public enum ClickType {LEFT, RIGHT}

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final UUID modelUuid;
    private final String modelName;
    private final String cacheName;
    private final int partIndex;
    private final String partName;
    private final ClickType clickType;

    public ModelInteractEvent(Player player, UUID modelUuid, String modelName, String cacheName,
                              int partIndex, String partName, ClickType clickType) {
        this.player = player;
        this.modelUuid = modelUuid;
        this.modelName = modelName;
        this.cacheName = cacheName;
        this.partIndex = partIndex;
        this.partName = partName;
        this.clickType = clickType;
    }

    /** The player who clicked. */
    public Player getPlayer() {
        return player;
    }

    /** Stable uuid of the clicked model; resolve a handle with {@code api.getModel(uuid)}. */
    public UUID getModelUuid() {
        return modelUuid;
    }

    /** Current name of the clicked model. */
    public String getModelName() {
        return modelName;
    }

    /** Cache (blueprint) the model was spawned from. */
    public String getCacheName() {
        return cacheName;
    }

    /** Index of the clicked element/part within the cache. */
    public int getPartIndex() {
        return partIndex;
    }

    /** Human-readable label of the clicked part (see {@code api.getPartNames}). */
    public String getPartName() {
        return partName;
    }

    /** Whether this was a left or right click. */
    public ClickType getClickType() {
        return clickType;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
