package fr.bloup.minecraftAnimationTool;

import fr.bloup.minecraftAnimationTool.commands.MATCommand;
import fr.bloup.minecraftAnimationTool.listeners.EntityListener;
import fr.bloup.minecraftAnimationTool.api.MatApiImpl;
import fr.bloup.minecraftAnimationTool.api.MinecraftAnimationToolApi;
import fr.bloup.minecraftAnimationTool.managers.EditModeManager;
import fr.bloup.minecraftAnimationTool.managers.EntityManager;
import fr.bloup.minecraftAnimationTool.pojos.EntityPOJO;
import fr.bloup.minecraftAnimationTool.readers.BlueprintCache;
import fr.bloup.minecraftAnimationTool.readers.CacheReader;
import fr.bloup.minecraftAnimationTool.records.Cache;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

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

    private static MinecraftAnimationToolApi api;

    public static MinecraftAnimationToolApi getApi() {
        return api;
    }

    @Getter public List<EntityPOJO> entityPOJOS = new ArrayList<>();

    @Override
    public void onEnable() {
        log.info("Enabled " + this.getDescription().getName() + " " +  this.getDescription().getVersion());

        init();

        api = new MatApiImpl(this, entityManager);
        Bukkit.getServicesManager().register(MinecraftAnimationToolApi.class, api, this, ServicePriority.Normal);

        this.listeners.add(new EntityListener(this, entityManager));
        this.listeners.add(editModeManager);
        this.listeners.add(new fr.bloup.minecraftAnimationTool.listeners.ModelInteractionListener(this, entityManager, editModeManager));

        registerListeners();
        registerCommands();

        entityManager.startTicker();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new fr.bloup.minecraftAnimationTool.placeholder.MatPlaceholderExpansion(this, entityManager).register();
            log.info("Hooked into PlaceholderAPI.");
        }

        if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
            Bukkit.getPluginManager().registerEvents(
                    new fr.bloup.minecraftAnimationTool.mythic.MythicHook(api), this);
            log.info("Hooked into MythicMobs.");
        }
    }

    @Override
    public void onDisable() {
        log.info("Disabled " + this.getDescription().getName() + " " +  this.getDescription().getVersion());

        entityManager.stopTicker();
        editModeManager.disableAll();

        if (this.getEntityPOJOS() != null) {
            for (EntityPOJO entityPOJO : new ArrayList<>(this.getEntityPOJOS())) {
                entityManager.removeEntity(entityPOJO);
            }
        }
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
        this.getCommand("minecraftanimationtool").setExecutor(new MATCommand(this,entityManager,blueprintCache,cacheReader,editModeManager));
        this.getCommand("mat").setExecutor(new MATCommand(this,entityManager,blueprintCache,cacheReader,editModeManager));
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
