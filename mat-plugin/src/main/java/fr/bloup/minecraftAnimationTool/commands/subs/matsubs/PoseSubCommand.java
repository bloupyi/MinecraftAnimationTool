package fr.bloup.minecraftAnimationTool.commands.subs.matsubs;

import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import fr.bloup.minecraftAnimationTool.managers.EntityManager;
import fr.bloup.minecraftAnimationTool.pojos.EntityPOJO;
import fr.bloup.minecraftAnimationTool.records.Cache;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.List;

@RequiredArgsConstructor
public class PoseSubCommand implements TabExecutor {
    private final MinecraftAnimationTool plugin;
    private final EntityManager entityManager;

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        if (strings.length < 3) {
            commandSender.sendMessage("Usage: /mat pose <name> <animation> <time>");
            return true;
        }

        EntityPOJO entityPOJO = entityManager.getEntity(strings[0]);
        if (entityPOJO == null) {
            commandSender.sendMessage("Entity not found: " + strings[0]);
            return true;
        }

        double time;
        try {
            time = Double.parseDouble(strings[2].replace(",", "."));
        } catch (NumberFormatException e) {
            commandSender.sendMessage("Invalid time (seconds): " + strings[2]);
            return true;
        }

        if (entityManager.poseAt(entityPOJO, strings[1], time)) {
            commandSender.sendMessage("Posed " + entityPOJO.getName() + " at " + time + "s of '" + strings[1] + "'");
        } else {
            commandSender.sendMessage("Animation not found: " + strings[1]);
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
