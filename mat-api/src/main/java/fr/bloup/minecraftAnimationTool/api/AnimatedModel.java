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

    /** Plays an animation, cross-fading from the current pose. Returns false if it does not exist. */
    boolean play(String animationName);

    /**
     * Plays an animation and runs {@code onEnd} once when a non-looping animation finishes.
     * The callback runs on the main thread. Returns false if the animation does not exist.
     */
    boolean play(String animationName, Runnable onEnd);

    /** Freezes the rig on a single frame of an animation at {@code time} seconds. */
    boolean pose(String animationName, double time);

    /** Pauses the current animation, holding the current frame. */
    void pause();

    /** Resumes a paused animation. */
    void resume();

    /** Sets the playback speed multiplier (1.0 = normal). Returns false if nothing is playing. */
    boolean setSpeed(double speed);

    /** Name of the animation currently loaded, or null if none. */
    String getCurrentAnimation();

    /** True if an animation is loaded and not paused. */
    boolean isPlaying();

    /** Uniform scale of the rig. */
    float getScale();

    /** Rescales the rig uniformly (1.0 = normal). Returns false if the scale is not positive. */
    boolean setScale(float scale);

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
