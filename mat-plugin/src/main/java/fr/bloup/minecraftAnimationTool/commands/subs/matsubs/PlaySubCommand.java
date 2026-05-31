package fr.bloup.minecraftAnimationTool.commands.subs.matsubs;

import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import fr.bloup.minecraftAnimationTool.managers.EntityManager;
import fr.bloup.minecraftAnimationTool.pojos.EntityPOJO;
import fr.bloup.minecraftAnimationTool.records.Cache;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

@RequiredArgsConstructor
public class PlaySubCommand implements TabExecutor {
    private final MinecraftAnimationTool plugin;
    private final EntityManager entityManager;

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        Player player = (Player) commandSender;

        if (strings.length < 2) {
            player.sendMessage("Usage: /mat play <name> <animation>");
            return true;
        }

        EntityPOJO entityPOJO = entityManager.getEntity(strings[0]);
        if (entityPOJO == null) {
            player.sendMessage("Entity not found: " + strings[0]);
            return true;
        }

        if (entityManager.playAnimation(entityPOJO, strings[1])) {
            player.sendMessage("Playing animation '" + strings[1] + "' on " + entityPOJO.getName());
        } else {
            player.sendMessage("Animation not found: " + strings[1]);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] strings) {
        if (strings.length == 1) {
            return plugin.getEntityPOJOS().stream()
                    .map(EntityPOJO::getName)
                    .filter(n -> n.toLowerCase().startsWith(strings[0].toLowerCase()))
                    .toList();
        }
        if (strings.length == 2) {
            EntityPOJO entityPOJO = entityManager.getEntity(strings[0]);
            if (entityPOJO == null) return List.of();
            return entityPOJO.getCache().animations().stream()
                    .map(Cache.Animation::name)
                    .filter(n -> n.toLowerCase().startsWith(strings[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
