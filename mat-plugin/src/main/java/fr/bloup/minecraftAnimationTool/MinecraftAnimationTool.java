package fr.bloup.minecraftAnimationTool;

import fr.bloup.minecraftAnimationTool.commands.MATCommand;
import fr.bloup.minecraftAnimationTool.listeners.EntityListener;
import fr.bloup.minecraftAnimationTool.api.MatApiImpl;
import fr.bloup.minecraftAnimationTool.api.MinecraftAnimationToolApi;
import fr.bloup.minecraftAnimationTool.listeners.RigCleanupListener;
import fr.bloup.minecraftAnimationTool.gui.GuiManager;
import fr.bloup.minecraftAnimationTool.managers.EditModeManager;
import fr.bloup.minecraftAnimationTool.managers.EntityManager;
import fr.bloup.minecraftAnimationTool.persistence.RigStore;
import fr.bloup.minecraftAnimationTool.placeholder.MatPlaceholderExpansion;
import fr.bloup.minecraftAnimationTool.pojos.AnimationState;
import fr.bloup.minecraftAnimationTool.pojos.EntityPOJO;
import fr.bloup.minecraftAnimationTool.readers.BlueprintCache;
import fr.bloup.minecraftAnimationTool.readers.CacheReader;
import fr.bloup.minecraftAnimationTool.records.Cache;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class MinecraftAnimationTool extends JavaPlugin {
    public Logger log = getLogger();
    private final List<Listener> listeners = new ArrayList<>();

    public File blueprints;
    public File cache;
    @Getter private List<Cache> allCaches;

    @Getter private final EntityManager entityManager = new EntityManager(this);
    private final BlueprintCache blueprintCache = new BlueprintCache(this);
    private final CacheReader cacheReader = new CacheReader(this);
    private final EditModeManager editModeManager = new EditModeManager(this, entityManager);
    private final GuiManager guiManager = new GuiManager(this, entityManager, editModeManager);

    private static MinecraftAnimationToolApi api;

    public static MinecraftAnimationToolApi getApi() {
        return api;
    }

    @Getter public List<EntityPOJO> entityPOJOS = new ArrayList<>();

    /** PDC key tagging every rig entity, so leftover ones can be cleaned up after a restart. */
    @Getter private NamespacedKey rigKey;
    private RigStore rigStore;
    private BukkitTask autosaveTask;
    private boolean rigsDirty = false;
    /** While restoring, suppress dirty-marking so the on-disk file is not rewritten mid-load. */
    private boolean restoringRigs = false;

    public boolean isPersistEnabled() {
        return getConfig().getBoolean("persist-rigs", true);
    }

    /** Flags that the live rig set or playback changed and should be re-saved by the autosave task. */
    public void markRigsDirty() {
        if (restoringRigs || !isPersistEnabled()) return;
        rigsDirty = true;
    }

    /** Writes the current rigs to disk immediately (no-op when persistence is disabled). */
    public void saveRigsNow() {
        if (!isPersistEnabled() || rigStore == null) return;
        rigStore.save(entityPOJOS);
        rigsDirty = false;
    }

    @Override
    public void onEnable() {
        log.info("Enabled " + this.getDescription().getName() + " " +  this.getDescription().getVersion());

        rigKey = new NamespacedKey(this, "rig");
        rigStore = new RigStore(this);

        init();

        api = new MatApiImpl(this, entityManager);
        Bukkit.getServicesManager().register(MinecraftAnimationToolApi.class, api, this, ServicePriority.Normal);

        this.listeners.add(new EntityListener(this, entityManager));
        this.listeners.add(editModeManager);
        this.listeners.add(new fr.bloup.minecraftAnimationTool.listeners.ModelInteractionListener(this, entityManager, editModeManager));
        this.listeners.add(new RigCleanupListener(this, entityManager));
        this.listeners.add(guiManager);

        registerListeners();
        registerCommands();

        entityManager.startTicker();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new MatPlaceholderExpansion(this, entityManager).register();
            log.info("Hooked into PlaceholderAPI.");
        }

        if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
            Bukkit.getPluginManager().registerEvents(
                    new fr.bloup.minecraftAnimationTool.mythic.MythicHook(api), this);
            log.info("Hooked into MythicMobs.");
        }

        // Plugin chunk tickets persist across restarts; drop any left over from a previous run before
        // restoreRigs re-adds them, so we never keep chunks loaded for rigs that no longer exist.
        for (World world : Bukkit.getWorlds()) world.removePluginChunkTickets(this);

        if (isPersistEnabled()) {
            cleanupOrphanRigEntities();
            restoreRigs();
            // Debounced autosave so frequent changes (play/tp/scale...) do not write on every call.
            autosaveTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
                if (rigsDirty) saveRigsNow();
            }, 100L, 100L);
        }
    }

    @Override
    public void onDisable() {
        log.info("Disabled " + this.getDescription().getName() + " " +  this.getDescription().getVersion());

        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        // Persist before despawning so the rigs can be recreated on the next start.
        saveRigsNow();

        entityManager.stopTicker();
        editModeManager.disableAll();

        if (this.getEntityPOJOS() != null) {
            for (EntityPOJO entityPOJO : new ArrayList<>(this.getEntityPOJOS())) {
                entityManager.removeEntity(entityPOJO);
            }
        }
    }

    /** Removes leftover tagged rig entities in loaded worlds (orphans from a previous run). */
    private void cleanupOrphanRigEntities() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(rigKey, PersistentDataType.STRING)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) log.info("persist: removed " + removed + " leftover rig entit(ies).");
    }

    /** Recreates the rigs saved in rigs.yml (and resumes their animation) after a restart/reload. */
    private void restoreRigs() {
        List<RigStore.SavedRig> saved = rigStore.load();
        if (saved.isEmpty()) return;

        restoringRigs = true;
        int restored = 0;
        try {
            for (RigStore.SavedRig r : saved) {
                Cache c = allCaches.stream().filter(x -> x.name().equals(r.cacheName())).findFirst().orElse(null);
                if (c == null) {
                    log.warning("persist: cache '" + r.cacheName() + "' missing, skipping rig '" + r.name() + "'.");
                    continue;
                }
                World world = Bukkit.getWorld(r.worldName());
                if (world == null) {
                    log.warning("persist: world '" + r.worldName() + "' not loaded, skipping rig '" + r.name() + "'.");
                    continue;
                }
                Location loc = new Location(world, r.x(), r.y(), r.z(), r.yaw(), r.pitch());
                EntityPOJO pojo = entityManager.createEntity(r.name(), c, r.uuid(), r.scale());
                entityManager.spawnEntity(pojo, loc);
                entityManager.setRotation(pojo, r.yaw(), r.pitch());

                if (r.animName() != null && !r.animName().isBlank()) {
                    if (r.paused()) {
                        entityManager.poseAt(pojo, r.animName(), r.animTime());
                    } else if (entityManager.playAnimation(pojo, r.animName())) {
                        AnimationState st = pojo.getAnimationState();
                        if (st != null) {
                            st.setTime(r.animTime());
                            st.setSpeed(r.animSpeed());
                        }
                    }
                }
                restored++;
            }
        } finally {
            restoringRigs = false;
        }
        if (restored > 0) log.info("persist: restored " + restored + " rig(s).");
    }

    /** Rebuilds caches from the blueprints folder and reloads them into memory (used by the API). */
    public void rebuildCaches() {
        blueprintCache.cacheBlueprint();
        try {
            List<Cache> fresh = cacheReader.readAllCaches();
            allCaches.clear();
            allCaches.addAll(fresh);
        } catch (IOException e) {
            log.severe("Error while reloading caches: " + e.getMessage());
        }
    }

    private void registerCommands(){
        this.getCommand("minecraftanimationtool").setExecutor(new MATCommand(this,entityManager,blueprintCache,cacheReader,editModeManager,guiManager));
        this.getCommand("mat").setExecutor(new MATCommand(this,entityManager,blueprintCache,cacheReader,editModeManager,guiManager));
    }

    private void registerListeners(){
        this.listeners.forEach(listener -> Bukkit.getPluginManager().registerEvents(listener, this));
    }

    private void init() {
        saveDefaultConfig();

        blueprints = new File(this.getDataFolder(), "blueprints");
        blueprints.mkdirs();

        cache = new File(this.getDataFolder(), "cache");
        cache.mkdirs();

        blueprintCache.cacheBlueprint();

        try {
            allCaches = cacheReader.readAllCaches();
            getLogger().info("Loaded caches: " + allCaches.size());
        } catch (IOException e) {
            getLogger().severe("Error while loading caches: " + e.getMessage());
        }
    }

}
