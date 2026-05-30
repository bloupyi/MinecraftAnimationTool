package fr.bloup.minecraftAnimationTool.commands.subs.matsubs;

import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import fr.bloup.minecraftAnimationTool.managers.EntityManager;
import fr.bloup.minecraftAnimationTool.pojos.EntityPOJO;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RequiredArgsConstructor
public class TpSubCommand implements TabExecutor {
    private final MinecraftAnimationTool plugin;
    private final EntityManager entityManager;

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        if (strings.length == 0) {
            commandSender.sendMessage("Usage: /mat tp <name> [x y z] [yaw pitch] [world]");
            return true;
        }

        EntityPOJO entityPOJO = entityManager.getEntity(strings[0]);
        if (entityPOJO == null) {
            commandSender.sendMessage("Entity not found: " + strings[0]);
            return true;
        }

        Location target;
        if (strings.length == 1) {
            if (commandSender instanceof Player player) {
                target = player.getLocation();
            } else {
                commandSender.sendMessage("From console give coordinates: /mat tp <name> <x> <y> <z> [yaw pitch] [world]");
                return true;
            }
        } else {
            List<String> tokens = new ArrayList<>(Arrays.asList(strings).subList(1, strings.length));

            // An optional trailing non-numeric token is the world name.
            World world = null;
            if (!isNumber(tokens.get(tokens.size() - 1))) {
                world = Bukkit.getWorld(tokens.get(tokens.size() - 1));
                if (world == null) {
                    commandSender.sendMessage("World not found: " + tokens.get(tokens.size() - 1));
                    return true;
                }
                tokens.remove(tokens.size() - 1);
            }
            if (world == null) {
                if (commandSender instanceof Player player) world = player.getWorld();
                else if (entityPOJO.getRoot() != null) world = entityPOJO.getRoot().getWorld();
            }
            if (world == null) {
                commandSender.sendMessage("Specify a world: /mat tp <name> <x> <y> <z> [yaw pitch] <world>");
                return true;
            }

            if (tokens.size() != 3 && tokens.size() != 5) {
                commandSender.sendMessage("Usage: /mat tp <name> <x> <y> <z> [yaw pitch] [world]");
                return true;
            }

            double x, y, z;
            float yaw = 0f, pitch = 0f;
            try {
                x = Double.parseDouble(tokens.get(0));
                y = Double.parseDouble(tokens.get(1));
                z = Double.parseDouble(tokens.get(2));
                if (tokens.size() == 5) {
                    yaw = Float.parseFloat(tokens.get(3));
                    pitch = Float.parseFloat(tokens.get(4));
                }
            } catch (NumberFormatException e) {
                commandSender.sendMessage("Coordinates and yaw/pitch must be numbers.");
                return true;
            }
            target = new Location(world, x, y, z, yaw, pitch);
        }

        entityManager.teleport(entityPOJO, target);
        commandSender.sendMessage("Teleported " + entityPOJO.getName() + ".");
        return true;
    }

    private static boolean isNumber(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] strings) {
        if (strings.length == 1) {
            return plugin.getEntityPOJOS().stream()
                    .map(EntityPOJO::getName)
                    .filter(n -> n.toLowerCase().startsWith(strings[0].toLowerCase()))
                    .toList();
        }
        // Suggest the sender's own position/orientation for the x/y/z/yaw/pitch arguments.
        if (commandSender instanceof Player player) {
            Location loc = player.getLocation();
            switch (strings.length) {
                case 2:
                    return List.of(String.valueOf(loc.getBlockX()));
                case 3:
                    return List.of(String.valueOf(loc.getBlockY()));
                case 4:
                    return List.of(String.valueOf(loc.getBlockZ()));
                case 5:
                    return List.of(String.valueOf(Math.round(loc.getYaw())));
                case 6:
                    return List.of(String.valueOf(Math.round(loc.getPitch())));
                default:
                    return List.of();
            }
        }
        return List.of();
    }
}
