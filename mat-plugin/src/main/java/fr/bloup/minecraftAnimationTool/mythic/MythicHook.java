package fr.bloup.minecraftAnimationTool.mythic;

import fr.bloup.minecraftAnimationTool.api.MinecraftAnimationToolApi;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Registers MinecraftAnimationTool's custom MythicMobs mechanics. Only instantiated when MythicMobs
 * is installed (see MinecraftAnimationTool#onEnable), so the io.lumine classes are never loaded
 * otherwise.
 *
 * <p>Mechanics (rig resolved by {@code name=}, with {@code <caster.uuid>}/{@code <caster.name>}
 * placeholders, or owned by the casting mob when {@code name} is omitted):
 * <ul>
 *   <li>{@code matspawn{model=;name=;scale=;anim=;replace=} @origin} - spawn a rig at the target location</li>
 *   <li>{@code matplay{name=;anim=}} - play an animation (cross-fades)</li>
 *   <li>{@code matpose{name=;anim=;time=}} - freeze on a frame</li>
 *   <li>{@code matpause{name=}} / {@code matresume{name=}}</li>
 *   <li>{@code matstop{name=}} / {@code matreset{name=}}</li>
 *   <li>{@code matspeed{name=;speed=}} / {@code matscale{name=;scale=}}</li>
 *   <li>{@code matremove{name=}}</li>
 *   <li>{@code matmaterial{name=;part=;material=}}</li>
 *   <li>{@code mattp{name=} @target} - move a rig / {@code matlookat{name=} @target} - orient a rig</li>
 * </ul>
 */
public class MythicHook implements Listener {
    private final MinecraftAnimationToolApi api;

    public MythicHook(MinecraftAnimationToolApi api) {
        this.api = api;
    }

    @EventHandler
    public void onMythicMechanicLoad(MythicMechanicLoadEvent event) {
        String mechanic = event.getMechanicName().toLowerCase();

        switch (mechanic) {
            case "matspawn":
                event.register(new LocationMechanic(api, LocationMechanic.Action.SPAWN, event.getConfig()));
                break;
            case "mattp":
                event.register(new LocationMechanic(api, LocationMechanic.Action.TP, event.getConfig()));
                break;
            case "matlookat":
                event.register(new LocationMechanic(api, LocationMechanic.Action.LOOKAT, event.getConfig()));
                break;
            case "matplay":
                event.register(new RigMechanic(api, RigMechanic.Action.PLAY, event.getConfig()));
                break;
            case "matpose":
                event.register(new RigMechanic(api, RigMechanic.Action.POSE, event.getConfig()));
                break;
            case "matpause":
                event.register(new RigMechanic(api, RigMechanic.Action.PAUSE, event.getConfig()));
                break;
            case "matresume":
                event.register(new RigMechanic(api, RigMechanic.Action.RESUME, event.getConfig()));
                break;
            case "matstop":
                event.register(new RigMechanic(api, RigMechanic.Action.STOP, event.getConfig()));
                break;
            case "matreset":
                event.register(new RigMechanic(api, RigMechanic.Action.RESET, event.getConfig()));
                break;
            case "matspeed":
                event.register(new RigMechanic(api, RigMechanic.Action.SPEED, event.getConfig()));
                break;
            case "matscale":
                event.register(new RigMechanic(api, RigMechanic.Action.SCALE, event.getConfig()));
                break;
            case "matremove":
                event.register(new RigMechanic(api, RigMechanic.Action.REMOVE, event.getConfig()));
                break;
            case "matmaterial":
                event.register(new RigMechanic(api, RigMechanic.Action.MATERIAL, event.getConfig()));
                break;
            default:
                // not one of ours
        }
    }
}
