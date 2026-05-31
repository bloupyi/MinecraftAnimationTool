package fr.bloup.minecraftAnimationTool.pojos;

import fr.bloup.minecraftAnimationTool.records.Cache;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-entity animation playback state, driven by the single global ticker in EntityManager.
 * Holds the resolved keyframe channels, the precomputed bone/element hierarchy of the cache,
 * playback cursor (time/speed/pause), an optional cross-fade from a previous pose, scheduled
 * sounds and an end callback.
 */
@Getter
public class AnimationState {
    private final Cache.Animation animation;
    private final String animationName;

    // Channels keyed by bone (outliner) uuid, already filtered + time-sorted.
    private final Map<String, List<Cache.Keyframe>> rotKf;
    private final Map<String, List<Cache.Keyframe>> posKf;
    private final Map<String, List<Cache.Keyframe>> scaleKf;

    // Cache hierarchy, indexed once at play time.
    private final Map<String, Cache.Outliner> outlinerByUuid;
    private final Map<String, String> outlinerParent;
    private final Map<String, String> elementParent;
    private final Map<String, Integer> elementIndex;

    // Sound cues sorted by time.
    private final List<SoundCue> sounds;

    // Precomputed once at play time: per element index, the chain of owning bone uuids (root first),
    // and the set of element indices that are actually driven by an animator.
    @Setter private Map<Integer, List<String>> chains;
    @Setter private java.util.Set<Integer> animatedIndices;

    private final double length;
    private final boolean loop;

    @Setter private double time = 0.0;
    @Setter private double speed = 1.0;
    @Setter private boolean paused = false;
    private boolean finished = false;

    /** Index of the next sound cue to fire (reset to 0 on loop wrap). */
    @Setter private int soundCursor = 0;

    /** Snapshot of every display's transformation when this animation started, for cross-fading. */
    @Setter private List<Transformation> blendFrom;
    @Setter private double blendDuration = 0.0;
    @Setter private double blendElapsed = 0.0;

    /** Last transformation pushed per display index, used to skip redundant updates. */
    private final Map<Integer, Transformation> applied = new HashMap<>();

    /** Optional callback invoked once when a non-looping animation reaches its end. */
    @Setter private Runnable onEnd;

    public AnimationState(Cache.Animation animation,
                          Map<String, List<Cache.Keyframe>> rotKf,
                          Map<String, List<Cache.Keyframe>> posKf,
                          Map<String, List<Cache.Keyframe>> scaleKf,
                          Map<String, Cache.Outliner> outlinerByUuid,
                          Map<String, String> outlinerParent,
                          Map<String, String> elementParent,
                          Map<String, Integer> elementIndex,
                          List<SoundCue> sounds,
                          double length,
                          boolean loop) {
        this.animation = animation;
        this.animationName = animation.name();
        this.rotKf = rotKf;
        this.posKf = posKf;
        this.scaleKf = scaleKf;
        this.outlinerByUuid = outlinerByUuid;
        this.outlinerParent = outlinerParent;
        this.elementParent = elementParent;
        this.elementIndex = elementIndex;
        this.sounds = sounds;
        this.length = length;
        this.loop = loop;
    }

    public void markFinished() {
        this.finished = true;
    }

    public boolean isBlending() {
        return blendFrom != null && blendElapsed < blendDuration;
    }

    /** A sound to play at a given animation time. */
    public record SoundCue(double time, String sound, float volume, float pitch) {}
}
