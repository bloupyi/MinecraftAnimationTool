package fr.bloup.minecraftAnimationTool.commands;

import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import fr.bloup.minecraftAnimationTool.commands.subs.matsubs.*;
import fr.bloup.minecraftAnimationTool.managers.EditModeManager;
import fr.bloup.minecraftAnimationTool.managers.EntityManager;
import fr.bloup.minecraftAnimationTool.readers.BlueprintCache;
import fr.bloup.minecraftAnimationTool.readers.CacheReader;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public class MATCommand extends AbstractCommand {
    private final HelpSubCommand help = new HelpSubCommand();

    public MATCommand(MinecraftAnimationTool plugin, EntityManager entityManager, BlueprintCache blueprintCache, CacheReader cacheReader, EditModeManager editModeManager) {
        super(plugin);
        registerSubCommand("help", help);
        registerSubCommand("reload", new ReloadSubCommand(plugin, blueprintCache, cacheReader));
        registerSubCommand("info", new InfoSubCommand(plugin, entityManager));
        registerSubCommand("kill", new KillSubCommand(plugin, entityManager));
        registerSubCommand("spawn", new SpawnSubCommand(plugin, entityManager));
        registerSubCommand("play", new PlaySubCommand(plugin, entityManager));
        registerSubCommand("pose", new PoseSubCommand(plugin, entityManager));
        registerSubCommand("pause", new PauseSubCommand(plugin, entityManager));
        registerSubCommand("resume", new ResumeSubCommand(plugin, entityManager));
        registerSubCommand("stop", new StopSubCommand(plugin, entityManager));
        registerSubCommand("reset", new ResetSubCommand(plugin, entityManager));
        registerSubCommand("tp", new TpSubCommand(plugin, entityManager));
        registerSubCommand("list", new ListSubCommand(plugin));
        registerSubCommand("edit", new EditSubCommand(plugin, entityManager, editModeManager));
    }

    @Override
    public String getPermission() {
        return "mat.command";
    }

    @Override
    public boolean runCommand(CommandSender sender, Command rootCommand, String label, String[] args) {
        return help.onCommand(sender, rootCommand, label, args);
    }
}
