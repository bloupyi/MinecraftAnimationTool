package fr.bloup.minecraftAnimationTool.commands.subs.matsubs;

import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import fr.bloup.minecraftAnimationTool.readers.BlueprintCache;
import fr.bloup.minecraftAnimationTool.readers.CacheReader;
import fr.bloup.minecraftAnimationTool.records.Cache;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
public class ReloadSubCommand implements TabExecutor {
    private final MinecraftAnimationTool plugin;
    private final BlueprintCache blueprintCache;
    private final CacheReader cacheReader;

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        Player player = (Player) commandSender;

        plugin.reloadConfig();
        plugin.getEntityManager().restartTicker();
        blueprintCache.cacheBlueprint();

        try {
            plugin.getAllCaches().clear();

            List<Cache> allCaches = cacheReader.readAllCaches();
            plugin.getAllCaches().addAll(allCaches);

            player.sendMessage("Loaded caches: " + allCaches.size());
            plugin.log.info("Loaded caches: " + allCaches.size());
        } catch (IOException e) {
            player.sendMessage("Error while loading caches");
            plugin.log.severe("Error while loading caches: " + e.getMessage());
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] strings) {
        return Collections.emptyList();
    }
}
