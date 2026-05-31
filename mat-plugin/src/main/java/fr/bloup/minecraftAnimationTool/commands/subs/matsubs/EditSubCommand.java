package fr.bloup.minecraftAnimationTool.commands.subs.matsubs;

import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import fr.bloup.minecraftAnimationTool.managers.EditModeManager;
import fr.bloup.minecraftAnimationTool.managers.EntityManager;
import fr.bloup.minecraftAnimationTool.records.Cache;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RequiredArgsConstructor
public class EditSubCommand implements TabExecutor {
    private final MinecraftAnimationTool plugin;
    private final EntityManager entityManager;
    private final EditModeManager editModeManager;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        // No arguments: toggle the interactive paint mode.
        if (args.length == 0) {
            editModeManager.toggle(player);
            return true;
        }

        if (args.length < 3) {
            player.sendMessage("Usage: /mat edit  (toggle paint mode)  |  /mat edit <cache> <part> <material>");
            return true;
        }

        String cacheName = args[0];

        Material material = Material.matchMaterial(args[2]);
        if (material == null || !material.isBlock()) {
            player.sendMessage("Unknown block material: " + args[2]);
            return true;
        }

        Cache cache = plugin.getAllCaches().stream()
                .filter(c -> c.name().equals(cacheName))
                .findFirst()
                .orElse(null);
        if (cache == null) {
            player.sendMessage("Cache not found: " + cacheName);
            return true;
        }

        int elementIndex = entityManager.elementIndexByName(cache, args[1]);
        if (elementIndex < 0) {
            player.sendMessage("Part not found in cache '" + cacheName + "': " + args[1]);
            return true;
        }
        String partName = entityManager.elementLabelOf(cache, elementIndex);

        if (entityManager.editElementMaterial(cacheName, elementIndex, material)) {
            player.sendMessage("Set part " + partName + " of cache '" + cacheName + "' to " + material.name() + ".");
        } else {
            player.sendMessage("Edit failed for cache '" + cacheName + "'.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return plugin.getAllCaches().stream()
                    .map(Cache::name)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .toList();
        }
        if (args.length == 2) {
            Cache cache = plugin.getAllCaches().stream()
                    .filter(c -> c.name().equals(args[0]))
                    .findFirst()
                    .orElse(null);
            if (cache == null) return List.of();
            String prefix = args[1].toLowerCase();
            return entityManager.elementLabelList(cache).stream()
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .toList();
        }
        if (args.length == 3) {
            String prefix = args[2].toUpperCase();
            List<String> matches = new ArrayList<>();
            for (Material m : Arrays.asList(Material.values())) {
                if (!m.isBlock()) continue;
                if (m.name().startsWith(prefix)) {
                    matches.add(m.name());
                    if (matches.size() >= 50) break;
                }
            }
            return matches;
        }
        return List.of();
    }
}
