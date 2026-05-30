package fr.bloup.minecraftAnimationTool.commands.subs.matsubs;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.List;

public class HelpSubCommand implements TabExecutor {

    private static final List<String> LINES = List.of(
            "§e/mat help §7- show this help",
            "§e/mat reload §7- rebuild and reload caches from blueprints/",
            "§e/mat spawn <name> <cache> §7- spawn a model",
            "§e/mat list §7- list spawned models",
            "§e/mat info <name> §7- show info about a model",
            "§e/mat play <name> <animation> §7- play an animation",
            "§e/mat stop <name> §7- stop the animation (keeps current pose)",
            "§e/mat reset <name> §7- stop and reset to the initial pose",
            "§e/mat tp <name> [x y z] [yaw pitch] [world] §7- move / orient a model",
            "§e/mat edit §7- toggle paint mode (right-click a part holding a block)",
            "§e/mat edit <cache> <part> <material> §7- set a part's block material",
            "§e/mat kill <name> §7- remove a model"
    );

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        commandSender.sendMessage("§6MinecraftAnimationTool §7commands:");
        for (String line : LINES) commandSender.sendMessage(line);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender commandSender, Command command, String s, String[] strings) {
        return List.of();
    }
}
