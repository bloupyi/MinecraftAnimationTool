package fr.bloup.minecraftAnimationTool.commands.subs.matsubs;

import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import fr.bloup.minecraftAnimationTool.managers.EntityManager;
import fr.bloup.minecraftAnimationTool.records.Cache;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

@RequiredArgsConstructor
public class SpawnSubCommand implements TabExecutor {
    private final MinecraftAnimationTool plugin;
    private final EntityManager entityManager;

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        Player player = (Player) commandSender;

        if (strings.length < 2) {
            player.sendMessage("Usage: /mat spawn <name> <cache>");
            return true;
        }

        Cache cache = plugin.getAllCaches().stream()
                .filter(c -> c.name().equals(strings[1]))
                .findFirst()
                .orElse(null);
        if (cache == null) {
            player.sendMessage("Cache not found: " + strings[1]);
            return true;
        }

        String name = entityManager.uniqueName(strings[0]);
        entityManager.spawnEntity(entityManager.createEntity(name, cache), player.getLocation());
        if (name.equals(strings[0])) {
            player.sendMessage("Spawned entity: " + name);
        } else {
            player.sendMessage("Name '" + strings[0] + "' is already in use; spawned as '" + name + "'.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] strings) {
        if (strings.length == 2) {
            return plugin.getAllCaches().stream()
                    .map(Cache::name)
                    .filter(n -> n.toLowerCase().startsWith(strings[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
