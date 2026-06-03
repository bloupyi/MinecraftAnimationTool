package fr.bloup.minecraftAnimationTool.gui;

import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import fr.bloup.minecraftAnimationTool.managers.EditModeManager;
import fr.bloup.minecraftAnimationTool.managers.EntityManager;
import fr.bloup.minecraftAnimationTool.pojos.AnimationState;
import fr.bloup.minecraftAnimationTool.pojos.EntityPOJO;
import fr.bloup.minecraftAnimationTool.records.Cache;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chest-menu front end for managing spawned rigs.
 *
 * <p>{@code /mat} with no argument opens the {@link MatMenuHolder.Type#MAIN} menu, listing every
 * spawned rig with its cache, location, scale and current animation. Clicking a rig opens its
 * {@link MatMenuHolder.Type#RIG} config menu (play / stop / pause / reset / pose / scale / teleport
 * / rotate / rename / paint / remove). A spawn button opens the {@link MatMenuHolder.Type#SPAWN}
 * menu of available caches; spawning one drops it at the player and jumps straight to its config
 * page. Free-form values (scale, name, coordinates, ...) are collected via a one-shot chat prompt.
 */
public class GuiManager implements Listener {

    private final MinecraftAnimationTool plugin;
    private final EntityManager entityManager;
    private final EditModeManager editModeManager;

    /** A pending chat prompt keyed by player: what value we asked for and which rig it targets. */
    private enum InputKind { SCALE, RENAME, TP, ROTATE, POSE }

    private record Pending(InputKind kind, UUID rigUuid) {}

    private final Map<UUID, Pending> awaitingInput = new HashMap<>();

    // ---- RIG menu fixed slots ----
    private static final int RIG_INFO = 4;
    private static final int RIG_PLAY = 19;
    private static final int RIG_STOP = 20;
    private static final int RIG_PAUSE = 21;
    private static final int RIG_RESET = 22;
    private static final int RIG_POSE = 23;
    private static final int RIG_SCALE = 28;
    private static final int RIG_TP = 29;
    private static final int RIG_ROTATE = 30;
    private static final int RIG_RENAME = 31;
    private static final int RIG_PAINT = 32;
    private static final int RIG_REMOVE = 34;
    private static final int BACK_SLOT = 49;

    /** First 45 slots hold list entries; the bottom row carries navigation. */
    private static final int LIST_CAPACITY = 45;

    public GuiManager(MinecraftAnimationTool plugin, EntityManager entityManager, EditModeManager editModeManager) {
        this.plugin = plugin;
        this.entityManager = entityManager;
        this.editModeManager = editModeManager;
    }

    // ---- Opening menus ----

    public void openMain(Player player) {
        MatMenuHolder holder = new MatMenuHolder(MatMenuHolder.Type.MAIN, null);
        Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.DARK_AQUA + "Spawned models");
        holder.setInventory(inv);

        List<EntityPOJO> rigs = plugin.getEntityPOJOS();
        int slot = 0;
        for (EntityPOJO rig : rigs) {
            if (slot >= LIST_CAPACITY) break;
            inv.setItem(slot, rigIcon(rig));
            holder.getSlotData().put(slot, rig.getUuid().toString());
            slot++;
        }
        if (rigs.size() > LIST_CAPACITY) {
            inv.setItem(45, item(Material.PAPER, ChatColor.GRAY + "Showing first " + LIST_CAPACITY + " of " + rigs.size(),
                    List.of(ChatColor.DARK_GRAY + "Use /mat list for the full list.")));
        }
        if (rigs.isEmpty()) {
            inv.setItem(22, item(Material.BARRIER, ChatColor.RED + "No models spawned",
                    List.of(ChatColor.GRAY + "Click the star below to spawn one.")));
        }

        inv.setItem(53, item(Material.NETHER_STAR, ChatColor.GREEN + "Spawn a model",
                List.of(ChatColor.GRAY + "Pick a cache to spawn here.")));
        player.openInventory(inv);
    }

    public void openSpawn(Player player) {
        MatMenuHolder holder = new MatMenuHolder(MatMenuHolder.Type.SPAWN, null);
        Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.DARK_AQUA + "Spawn a model");
        holder.setInventory(inv);

        List<Cache> caches = plugin.getAllCaches();
        int slot = 0;
        for (Cache cache : caches) {
            if (slot >= LIST_CAPACITY) break;
            int parts = cache.elements() == null ? 0 : cache.elements().size();
            int anims = cache.animations() == null ? 0 : cache.animations().size();
            inv.setItem(slot, item(Material.ARMOR_STAND, ChatColor.YELLOW + cache.name(), List.of(
                    ChatColor.GRAY + "Parts: " + ChatColor.WHITE + parts,
                    ChatColor.GRAY + "Animations: " + ChatColor.WHITE + anims,
                    "",
                    ChatColor.GREEN + "Click to spawn at your location.")));
            holder.getSlotData().put(slot, cache.name());
            slot++;
        }
        if (caches.isEmpty()) {
            inv.setItem(22, item(Material.BARRIER, ChatColor.RED + "No caches available",
                    List.of(ChatColor.GRAY + "Drop .bbmodel files in blueprints/ then /mat reload.")));
        }

        inv.setItem(BACK_SLOT, item(Material.ARROW, ChatColor.YELLOW + "Back", List.of()));
        player.openInventory(inv);
    }

    public void openRig(Player player, UUID rigUuid) {
        EntityPOJO rig = entityManager.getEntity(rigUuid);
        if (rig == null) {
            player.sendMessage(ChatColor.RED + "That model no longer exists.");
            openMain(player);
            return;
        }
        MatMenuHolder holder = new MatMenuHolder(MatMenuHolder.Type.RIG, rigUuid);
        Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.DARK_AQUA + "Model: "
                + ChatColor.stripColor(rig.getName()));
        holder.setInventory(inv);

        inv.setItem(RIG_INFO, rigIcon(rig));

        boolean playing = entityManager.isPlaying(rig);
        boolean hasState = rig.getAnimationState() != null;

        inv.setItem(RIG_PLAY, item(Material.LIME_DYE, ChatColor.GREEN + "Play animation",
                List.of(ChatColor.GRAY + "Choose an animation to play.")));
        inv.setItem(RIG_STOP, item(Material.RED_DYE, ChatColor.RED + "Stop",
                List.of(ChatColor.GRAY + "Stop, keeping the current pose.")));
        inv.setItem(RIG_PAUSE, item(playing ? Material.YELLOW_DYE : Material.GRAY_DYE,
                (playing ? ChatColor.YELLOW + "Pause" : ChatColor.GREEN + "Resume"),
                List.of(ChatColor.GRAY + (hasState ? (playing ? "Hold the current frame." : "Continue playback.")
                        : "No animation active."))));
        inv.setItem(RIG_RESET, item(Material.WATER_BUCKET, ChatColor.AQUA + "Reset",
                List.of(ChatColor.GRAY + "Stop and restore the bind pose.")));
        inv.setItem(RIG_POSE, item(Material.CLOCK, ChatColor.GOLD + "Pose at frame",
                List.of(ChatColor.GRAY + "Freeze on a single frame.",
                        ChatColor.DARK_GRAY + "Chat: <animation> <seconds>")));
        inv.setItem(RIG_SCALE, item(Material.ANVIL, ChatColor.LIGHT_PURPLE + "Set scale",
                List.of(ChatColor.GRAY + "Current: " + ChatColor.WHITE + fmt(rig.getScale()),
                        ChatColor.DARK_GRAY + "Chat: a positive number")));
        inv.setItem(RIG_TP, item(Material.ENDER_PEARL, ChatColor.AQUA + "Teleport to me",
                List.of(ChatColor.GRAY + "Move the model to your location.")));
        inv.setItem(RIG_ROTATE, item(Material.COMPASS, ChatColor.AQUA + "Set rotation",
                List.of(ChatColor.GRAY + "Face a yaw/pitch.",
                        ChatColor.DARK_GRAY + "Chat: <yaw> [pitch]")));
        inv.setItem(RIG_RENAME, item(Material.NAME_TAG, ChatColor.YELLOW + "Rename",
                List.of(ChatColor.GRAY + "Current: " + ChatColor.WHITE + rig.getName(),
                        ChatColor.DARK_GRAY + "Chat: the new name")));
        inv.setItem(RIG_PAINT, item(Material.PAINTING, ChatColor.LIGHT_PURPLE + "Edit materials",
                List.of(ChatColor.GRAY + "Toggle paint mode and close this menu.",
                        ChatColor.GRAY + "Right-click parts holding a block.")));
        inv.setItem(RIG_REMOVE, item(Material.BARRIER, ChatColor.RED + "Remove model",
                List.of(ChatColor.GRAY + "Despawn this model.",
                        ChatColor.DARK_RED + "Shift-click to confirm.")));

        inv.setItem(BACK_SLOT, item(Material.ARROW, ChatColor.YELLOW + "Back", List.of()));
        player.openInventory(inv);
    }

    public void openAnim(Player player, UUID rigUuid) {
        EntityPOJO rig = entityManager.getEntity(rigUuid);
        if (rig == null) {
            player.sendMessage(ChatColor.RED + "That model no longer exists.");
            openMain(player);
            return;
        }
        MatMenuHolder holder = new MatMenuHolder(MatMenuHolder.Type.ANIM, rigUuid);
        Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.DARK_AQUA + "Animations: "
                + ChatColor.stripColor(rig.getName()));
        holder.setInventory(inv);

        List<Cache.Animation> animations = rig.getCache().animations();
        String current = entityManager.getCurrentAnimationName(rig);
        int slot = 0;
        if (animations != null) {
            for (Cache.Animation animation : animations) {
                if (slot >= LIST_CAPACITY) break;
                boolean active = animation.name().equals(current);
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Loop: " + ChatColor.WHITE + ("loop".equals(animation.loop()) ? "yes" : "no"));
                if (animation.length() > 0) lore.add(ChatColor.GRAY + "Length: " + ChatColor.WHITE + fmt((float) animation.length()) + "s");
                if (active) lore.add(ChatColor.GREEN + "Currently playing");
                lore.add(ChatColor.GREEN + "Click to play.");
                inv.setItem(slot, item(active ? Material.MUSIC_DISC_CAT : Material.MUSIC_DISC_13,
                        ChatColor.YELLOW + animation.name(), lore));
                holder.getSlotData().put(slot, animation.name());
                slot++;
            }
        }
        if (slot == 0) {
            inv.setItem(22, item(Material.BARRIER, ChatColor.RED + "No animations",
                    List.of(ChatColor.GRAY + "This model has no animations.")));
        }

        inv.setItem(BACK_SLOT, item(Material.ARROW, ChatColor.YELLOW + "Back", List.of()));
        player.openInventory(inv);
    }

    // ---- Click handling ----

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MatMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return; // a click in the player's own inventory
        }

        int slot = event.getSlot();
        switch (holder.getType()) {
            case MAIN -> handleMainClick(player, holder, slot);
            case SPAWN -> handleSpawnClick(player, holder, slot);
            case RIG -> handleRigClick(player, holder, slot, event.isShiftClick());
            case ANIM -> handleAnimClick(player, holder, slot);
        }
    }

    private void handleMainClick(Player player, MatMenuHolder holder, int slot) {
        if (slot == 53) {
            openSpawn(player);
            return;
        }
        String id = holder.getSlotData().get(slot);
        if (id == null) return;
        openRig(player, UUID.fromString(id));
    }

    private void handleSpawnClick(Player player, MatMenuHolder holder, int slot) {
        if (slot == BACK_SLOT) {
            openMain(player);
            return;
        }
        String cacheName = holder.getSlotData().get(slot);
        if (cacheName == null) return;
        Cache cache = plugin.getAllCaches().stream()
                .filter(c -> c.name().equals(cacheName))
                .findFirst()
                .orElse(null);
        if (cache == null) {
            player.sendMessage(ChatColor.RED + "Cache not found: " + cacheName);
            openSpawn(player);
            return;
        }
        String name = entityManager.uniqueName(cache.name());
        EntityPOJO rig = entityManager.createEntity(name, cache);
        entityManager.spawnEntity(rig, player.getLocation());
        player.sendMessage(ChatColor.GREEN + "Spawned '" + name + "'. Configure it below.");
        openRig(player, rig.getUuid());
    }

    private void handleRigClick(Player player, MatMenuHolder holder, int slot, boolean shift) {
        UUID rigUuid = holder.getRigUuid();
        EntityPOJO rig = entityManager.getEntity(rigUuid);
        if (rig == null) {
            player.sendMessage(ChatColor.RED + "That model no longer exists.");
            openMain(player);
            return;
        }
        switch (slot) {
            case BACK_SLOT -> openMain(player);
            case RIG_PLAY -> openAnim(player, rigUuid);
            case RIG_STOP -> {
                entityManager.stopAnimation(rig);
                player.sendMessage(ChatColor.GRAY + "Stopped '" + rig.getName() + "'.");
                openRig(player, rigUuid);
            }
            case RIG_PAUSE -> {
                if (rig.getAnimationState() == null) {
                    player.sendMessage(ChatColor.RED + "No animation active.");
                } else if (entityManager.isPlaying(rig)) {
                    entityManager.pause(rig);
                    player.sendMessage(ChatColor.GRAY + "Paused '" + rig.getName() + "'.");
                } else {
                    entityManager.resume(rig);
                    player.sendMessage(ChatColor.GRAY + "Resumed '" + rig.getName() + "'.");
                }
                openRig(player, rigUuid);
            }
            case RIG_RESET -> {
                entityManager.resetAnimation(rig);
                player.sendMessage(ChatColor.GRAY + "Reset '" + rig.getName() + "' to its bind pose.");
                openRig(player, rigUuid);
            }
            case RIG_POSE -> prompt(player, rigUuid, InputKind.POSE,
                    "Type " + ChatColor.YELLOW + "<animation> <seconds>" + ChatColor.GRAY
                            + " in chat to pose the model, or " + ChatColor.YELLOW + "cancel" + ChatColor.GRAY + ".");
            case RIG_SCALE -> prompt(player, rigUuid, InputKind.SCALE,
                    "Type a " + ChatColor.YELLOW + "positive number" + ChatColor.GRAY + " in chat to set the scale, or "
                            + ChatColor.YELLOW + "cancel" + ChatColor.GRAY + ".");
            case RIG_TP -> {
                entityManager.teleport(rig, player.getLocation());
                player.sendMessage(ChatColor.GRAY + "Moved '" + rig.getName() + "' to you.");
                openRig(player, rigUuid);
            }
            case RIG_ROTATE -> prompt(player, rigUuid, InputKind.ROTATE,
                    "Type " + ChatColor.YELLOW + "<yaw> [pitch]" + ChatColor.GRAY + " in chat to rotate the model, or "
                            + ChatColor.YELLOW + "cancel" + ChatColor.GRAY + ".");
            case RIG_RENAME -> prompt(player, rigUuid, InputKind.RENAME,
                    "Type the " + ChatColor.YELLOW + "new name" + ChatColor.GRAY + " in chat, or "
                            + ChatColor.YELLOW + "cancel" + ChatColor.GRAY + ".");
            case RIG_PAINT -> {
                player.closeInventory();
                if (!editModeManager.isEditing(player)) {
                    editModeManager.toggle(player);
                } else {
                    player.sendMessage(ChatColor.GRAY + "Paint mode already active. Run /mat edit to stop.");
                }
            }
            case RIG_REMOVE -> {
                if (!shift) {
                    player.sendMessage(ChatColor.RED + "Shift-click the remove button to confirm.");
                    return;
                }
                String name = rig.getName();
                entityManager.removeEntity(rig);
                player.sendMessage(ChatColor.GRAY + "Removed '" + name + "'.");
                openMain(player);
            }
            default -> { /* decorative / info slot */ }
        }
    }

    private void handleAnimClick(Player player, MatMenuHolder holder, int slot) {
        UUID rigUuid = holder.getRigUuid();
        if (slot == BACK_SLOT) {
            openRig(player, rigUuid);
            return;
        }
        String animName = holder.getSlotData().get(slot);
        if (animName == null) return;
        EntityPOJO rig = entityManager.getEntity(rigUuid);
        if (rig == null) {
            player.sendMessage(ChatColor.RED + "That model no longer exists.");
            openMain(player);
            return;
        }
        if (entityManager.playAnimation(rig, animName)) {
            player.sendMessage(ChatColor.GREEN + "Playing '" + animName + "' on " + rig.getName() + ".");
        } else {
            player.sendMessage(ChatColor.RED + "Animation not found: " + animName);
        }
        openRig(player, rigUuid);
    }

    // ---- Chat input ----

    private void prompt(Player player, UUID rigUuid, InputKind kind, String message) {
        awaitingInput.put(player.getUniqueId(), new Pending(kind, rigUuid));
        player.closeInventory();
        player.sendMessage(ChatColor.GRAY + message);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        awaitingInput.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Pending pending = awaitingInput.remove(player.getUniqueId());
        if (pending == null) return;
        event.setCancelled(true);
        String input = event.getMessage().trim();

        // Mutating entities and opening menus must happen on the main thread.
        Bukkit.getScheduler().runTask(plugin, () -> applyInput(player, pending, input));
    }

    private void applyInput(Player player, Pending pending, String input) {
        if (input.equalsIgnoreCase("cancel")) {
            player.sendMessage(ChatColor.GRAY + "Cancelled.");
            openRig(player, pending.rigUuid());
            return;
        }
        EntityPOJO rig = entityManager.getEntity(pending.rigUuid());
        if (rig == null) {
            player.sendMessage(ChatColor.RED + "That model no longer exists.");
            openMain(player);
            return;
        }
        switch (pending.kind()) {
            case SCALE -> {
                Float scale = parseFloat(input);
                if (scale == null || scale <= 0) {
                    player.sendMessage(ChatColor.RED + "Invalid scale: " + input);
                } else if (entityManager.setScale(rig, scale)) {
                    player.sendMessage(ChatColor.GRAY + "Scale of '" + rig.getName() + "' set to " + fmt(scale) + ".");
                } else {
                    player.sendMessage(ChatColor.RED + "Could not set scale.");
                }
            }
            case RENAME -> {
                String newName = input.split("\\s+")[0];
                if (entityManager.getEntity(newName) != null && !newName.equals(rig.getName())) {
                    player.sendMessage(ChatColor.RED + "Name '" + newName + "' is already in use.");
                } else {
                    rig.setName(newName);
                    plugin.markRigsDirty();
                    player.sendMessage(ChatColor.GRAY + "Renamed to '" + newName + "'.");
                }
            }
            case TP -> {
                String[] parts = input.split("\\s+");
                if (parts.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: <x> <y> <z>");
                } else {
                    Float x = parseFloat(parts[0]);
                    Float y = parseFloat(parts[1]);
                    Float z = parseFloat(parts[2]);
                    if (x == null || y == null || z == null) {
                        player.sendMessage(ChatColor.RED + "Invalid coordinates: " + input);
                    } else {
                        Location loc = rig.getRoot() != null ? rig.getRoot().getLocation() : player.getLocation();
                        loc = new Location(loc.getWorld(), x, y, z, loc.getYaw(), loc.getPitch());
                        entityManager.teleport(rig, loc);
                        player.sendMessage(ChatColor.GRAY + "Moved '" + rig.getName() + "'.");
                    }
                }
            }
            case ROTATE -> {
                String[] parts = input.split("\\s+");
                Float yaw = parseFloat(parts[0]);
                Float pitch = parts.length > 1 ? parseFloat(parts[1]) : 0f;
                if (yaw == null || pitch == null) {
                    player.sendMessage(ChatColor.RED + "Invalid rotation: " + input);
                } else {
                    entityManager.setRotation(rig, yaw, pitch);
                    player.sendMessage(ChatColor.GRAY + "Rotated '" + rig.getName() + "'.");
                }
            }
            case POSE -> {
                String[] parts = input.split("\\s+");
                if (parts.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: <animation> <seconds>");
                } else {
                    Float time = parseFloat(parts[1]);
                    if (time == null) {
                        player.sendMessage(ChatColor.RED + "Invalid time: " + parts[1]);
                    } else if (entityManager.poseAt(rig, parts[0], time)) {
                        player.sendMessage(ChatColor.GRAY + "Posed '" + rig.getName() + "' at " + fmt(time) + "s of '" + parts[0] + "'.");
                    } else {
                        player.sendMessage(ChatColor.RED + "Animation not found: " + parts[0]);
                    }
                }
            }
        }
        openRig(player, pending.rigUuid());
    }

    // ---- Helpers ----

    private ItemStack rigIcon(EntityPOJO rig) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Cache: " + ChatColor.WHITE + rig.getCache().name());
        Location loc = rig.getRoot() != null ? rig.getRoot().getLocation() : null;
        if (loc != null && loc.getWorld() != null) {
            lore.add(ChatColor.GRAY + "World: " + ChatColor.WHITE + loc.getWorld().getName());
            lore.add(ChatColor.GRAY + "Pos: " + ChatColor.WHITE
                    + fmt((float) loc.getX()) + ", " + fmt((float) loc.getY()) + ", " + fmt((float) loc.getZ()));
            lore.add(ChatColor.GRAY + "Facing: " + ChatColor.WHITE
                    + fmt(loc.getYaw()) + " / " + fmt(loc.getPitch()));
        }
        lore.add(ChatColor.GRAY + "Scale: " + ChatColor.WHITE + fmt(rig.getScale()));
        AnimationState st = rig.getAnimationState();
        if (st != null) {
            lore.add(ChatColor.GRAY + "Animation: " + ChatColor.WHITE + st.getAnimationName()
                    + (st.isPaused() ? ChatColor.YELLOW + " (paused)" : ChatColor.GREEN + " (playing)"));
        } else if (rig.getHeldAnimation() != null) {
            lore.add(ChatColor.GRAY + "Holding: " + ChatColor.WHITE + rig.getHeldAnimation());
        } else {
            lore.add(ChatColor.GRAY + "Animation: " + ChatColor.WHITE + "none");
        }
        return item(Material.ARMOR_STAND, ChatColor.AQUA + rig.getName(), lore);
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static Float parseFloat(String s) {
        if (s == null) return null;
        try {
            return Float.parseFloat(s.replace(",", ".").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String fmt(float v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.format(java.util.Locale.US, "%.2f", v);
    }
}
