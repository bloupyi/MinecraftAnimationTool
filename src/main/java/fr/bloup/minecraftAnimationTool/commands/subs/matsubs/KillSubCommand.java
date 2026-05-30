package fr.bloup.minecraftAnimationTool.commands.subs.matsubs;

import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import fr.bloup.minecraftAnimationTool.managers.EntityManager;
import fr.bloup.minecraftAnimationTool.pojos.EntityPOJO;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

@RequiredArgsConstructor
public class KillSubCommand implements TabExecutor {
    private final MinecraftAnimationTool plugin;
    private final EntityManager entityManager;

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        Player player = (Player) commandSender;

        if (strings.length > 0) {
            EntityPOJO entityPOJO = entityManager.getEntity(strings[0]);
            if (entityPOJO != null) {
                entityManager.removeEntity(entityPOJO);
                player.sendMessage("Entity removed: " + strings[0]);
            } else {
                player.sendMessage("Entity not found: " + strings[0]);
            }
        } else {
            player.sendMessage("Usage: /mat kill <name>");
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
        return List.of();
    }
}
