package fr.bloup.minecraftAnimationTool.commands.subs.matsubs;

import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import fr.bloup.minecraftAnimationTool.managers.EntityManager;
import fr.bloup.minecraftAnimationTool.pojos.EntityPOJO;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.List;

@RequiredArgsConstructor
public class ResumeSubCommand implements TabExecutor {
    private final MinecraftAnimationTool plugin;
    private final EntityManager entityManager;

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        if (strings.length == 0) {
            commandSender.sendMessage("Usage: /mat resume <name>");
            return true;
        }
        EntityPOJO entityPOJO = entityManager.getEntity(strings[0]);
        if (entityPOJO == null) {
            commandSender.sendMessage("Entity not found: " + strings[0]);
            return true;
        }
        if (entityManager.resume(entityPOJO)) {
            commandSender.sendMessage("Resumed animation on " + entityPOJO.getName());
        } else {
            commandSender.sendMessage(entityPOJO.getName() + " has no animation to resume.");
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
