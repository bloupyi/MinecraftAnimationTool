package fr.bloup.minecraftAnimationTool.gui;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Inventory holder that identifies a MAT menu and carries its context. {@link #slotData} maps a
 * clickable slot to an opaque identifier (rig uuid, cache name or animation name) so the click
 * handler can resolve what the player clicked without re-deriving it from the item.
 */
@Getter
public class MatMenuHolder implements InventoryHolder {

    public enum Type { MAIN, SPAWN, RIG, ANIM }

    private final Type type;
    /** The rig this menu acts on (RIG / ANIM menus); null for MAIN / SPAWN. */
    private final UUID rigUuid;
    private final Map<Integer, String> slotData = new HashMap<>();
    @Setter private Inventory inventory;

    public MatMenuHolder(Type type, UUID rigUuid) {
        this.type = type;
        this.rigUuid = rigUuid;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
