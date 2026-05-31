package fr.bloup.minecraftAnimationTool.commands.subs.matsubs;

import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import fr.bloup.minecraftAnimationTool.pojos.EntityPOJO;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;

@RequiredArgsConstructor
public class ListSubCommand implements TabExecutor {
    private final MinecraftAnimationTool plugin;

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        Player player = (Player) commandSender;

        List<EntityPOJO> entityPOJOS = plugin.getEntityPOJOS();
        if (entityPOJOS.isEmpty()) {
            player.sendMessage("No entities spawned.");
        } else {
            player.sendMessage("Entities: ");
            for (EntityPOJO entityPOJO : entityPOJOS) {
                player.sendMessage(" - " + entityPOJO.getName() + " (" + entityPOJO.getCache().name() + ")");
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] strings) {
        return List.of();
    }
}
