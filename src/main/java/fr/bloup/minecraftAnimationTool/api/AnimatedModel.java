package fr.bloup.minecraftAnimationTool.api;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.List;
import java.util.UUID;

public interface AnimatedModel {

    String getName();

    UUID getUuid();

    String getCacheName();

    /** Current location of the rig, or null if it is no longer alive. */
    Location getLocation();

    boolean isAlive();

    List<String> getAnimationNames();

    List<String> getPartNames();

    /** Plays an animation. Returns false if the animation does not exist or the model is gone. */
    boolean play(String animationName);

    /** Stops the animation currently playing (if any). The rig stays in its current pose. */
    void stop();

    /** Stops the animation and restores the rig to its initial (bind) pose. */
    void reset();

    /** Moves the whole rig to the given location (the location's yaw/pitch set the facing). */
    void teleport(Location location);

    /** Sets the facing direction (yaw/pitch, in degrees) of the rig without moving it. */
    void setRotation(float yaw, float pitch);

    /** Orients the rig so it faces the given location. */
    void lookAt(Location target);

    /** Changes the block material of one part/cube (see {@link MinecraftAnimationToolApi#setPartMaterial}). */
    boolean setPartMaterial(String partName, Material material);

    /** Despawns the rig. */
    void remove();
}
