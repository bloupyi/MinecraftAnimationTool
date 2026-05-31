package fr.bloup.minecraftAnimationTool.managers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.internal.LinkedTreeMap;
import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import fr.bloup.minecraftAnimationTool.pojos.AnimationState;
import fr.bloup.minecraftAnimationTool.pojos.EntityPOJO;
import fr.bloup.minecraftAnimationTool.readers.CacheReader;
import fr.bloup.minecraftAnimationTool.records.Cache;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;

@RequiredArgsConstructor
public class EntityManager {
    private final MinecraftAnimationTool plugin;
    private final Gson editGson = new Gson();

    /** Single global ticker that drives every active animation. */
    private BukkitTask globalTask;

    // ---- Lifecycle of the global ticker ----

    public void startTicker() {
        if (globalTask != null) return;
        int interval = Math.max(1, plugin.getConfig().getInt("interpolation-updates", 1));
        globalTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 1L, interval);
    }

    public void stopTicker() {
        if (globalTask != null) {
            globalTask.cancel();
            globalTask = null;
        }
    }

    /** Re-reads the tick interval from config and restarts the ticker (used on /mat reload). */
    public void restartTicker() {
        stopTicker();
        startTicker();
    }

    // ---- Entity bookkeeping ----

    public EntityPOJO createEntity(String name, Cache cache) {
        return createEntity(name, cache, UUID.randomUUID(), 1.0f);
    }

    public EntityPOJO createEntity(String name, Cache cache, UUID uuid) {
        return createEntity(name, cache, uuid, 1.0f);
    }

    public EntityPOJO createEntity(String name, Cache cache, float scale) {
        return createEntity(name, cache, UUID.randomUUID(), scale);
    }

    public EntityPOJO createEntity(String name, Cache cache, UUID uuid, float scale) {
        EntityPOJO entityPOJO = new EntityPOJO(name, uuid, cache, scale);
        plugin.getEntityPOJOS().add(entityPOJO);
        return entityPOJO;
    }

    public EntityPOJO getEntity(String name) {
        for (EntityPOJO entityPOJO : plugin.getEntityPOJOS()) {
            if (entityPOJO.getName().equals(name)) {
                return entityPOJO;
            }
        }
        return null;
    }

    public EntityPOJO getEntity(UUID uuid) {
        for (EntityPOJO entityPOJO : plugin.getEntityPOJOS()) {
            if (entityPOJO.getUuid().equals(uuid)) {
                return entityPOJO;
            }
        }
        return null;
    }

    /** Returns a name that is not yet used by a spawned entity, appending -2, -3, ... when needed. */
    public String uniqueName(String base) {
        if (getEntity(base) == null) return base;
        int i = 2;
        while (getEntity(base + "-" + i) != null) i++;
        return base + "-" + i;
    }

    // ---- Spawning ----

    public void spawnEntity(EntityPOJO entityPOJO, Location location) {
        Location spawnLoc = location.clone();
        spawnLoc.setYaw(0f);
        spawnLoc.setPitch(0f);
        ArmorStand root = location.getWorld().spawn(spawnLoc, ArmorStand.class, (armorStand) -> {
            armorStand.setVisible(false);
            armorStand.setGravity(false);
            armorStand.setInvulnerable(true);
            armorStand.setSmall(true);
            armorStand.setMarker(true);
        });
        entityPOJO.setRoot(root);

        for (Cache.Element element : entityPOJO.getCache().elements()) {

            double sizeX = Math.abs(element.to()[0] - element.from()[0]) / 16.0;
            double sizeY = Math.abs(element.to()[1] - element.from()[1]) / 16.0;
            double sizeZ = Math.abs(element.to()[2] - element.from()[2]) / 16.0;

            double posX = element.from()[0] / 16.0;
            double posY = element.from()[1] / 16.0;
            double posZ = element.from()[2] / 16.0;

            Quaternionf initialRotation = new Quaternionf().identity();
            if (element.rotation() != null) {
                initialRotation.rotateZ((float)Math.toRadians(element.rotation()[2]))
                        .rotateX((float)Math.toRadians(element.rotation()[0]))
                        .rotateY((float)Math.toRadians(element.rotation()[1]));
            }

            Material elementMaterial = Material.STONE;
            if (element.material() != null) {
                Material parsed = Material.matchMaterial(element.material());
                if (parsed != null && parsed.isBlock()) {
                    elementMaterial = parsed;
                } else {
                    plugin.log.warning("Unknown or non-block material '" + element.material() + "' on element " + element.uuid() + "; falling back to STONE.");
                }
            }
            final Material spawnMaterial = elementMaterial;
            BlockDisplay display = root.getWorld().spawn(root.getLocation(), BlockDisplay.class, (blockDisplay) -> {
                blockDisplay.setBlock(spawnMaterial.createBlockData());
                blockDisplay.setTransformation(new Transformation(
                        new Vector3f((float)posX, (float)posY, (float)posZ),
                        initialRotation,
                        new Vector3f((float)sizeX, (float)sizeY, (float)sizeZ),
                        new Quaternionf().identity()
                ));
            });

            entityPOJO.getBlockDisplays().add(display);
        }

        for (Cache.Outliner topLevelOutliner : entityPOJO.getCache().outliner()) {
            applyRotationRecursively(entityPOJO, topLevelOutliner, new Quaternionf().identity(), null);
        }

        // Bake the rig scale into the (already rotated) bind pose: scale offsets and sizes about the root.
        float rigScale = entityPOJO.getScale();
        if (rigScale != 1.0f) {
            for (BlockDisplay display : entityPOJO.getBlockDisplays()) {
                Transformation t = display.getTransformation();
                Vector3f tr = new Vector3f(t.getTranslation()).mul(rigScale);
                Vector3f sc = new Vector3f(t.getScale()).mul(rigScale);
                display.setTransformation(new Transformation(tr, t.getLeftRotation(), sc, t.getRightRotation()));
            }
        }

        // Snapshot the bind (rest) pose so the animation can be reset to / blended from it later.
        entityPOJO.getBaseTransformations().clear();
        for (BlockDisplay display : entityPOJO.getBlockDisplays()) {
            entityPOJO.getBaseTransformations().add(display.getTransformation());
        }
    }

    private void applyRotationRecursively(EntityPOJO entityPOJO, Cache.Outliner outliner, Quaternionf parentRotation, Vector3f parentOrigin) {
        if (outliner.children() == null) return;

        // Origine de l'outliner
        Vector3f outlinerOrigin = new Vector3f(
                outliner.origin() != null ? (float)outliner.origin()[0] / 16.0f : 0f,
                outliner.origin() != null ? (float)outliner.origin()[1] / 16.0f : 0f,
                outliner.origin() != null ? (float)outliner.origin()[2] / 16.0f : 0f
        );

        // Rotation de l'outliner (ordre Z, X, Y)
        Quaternionf outlinerRotation = new Quaternionf().identity();
        if (outliner.rotation() != null) {
            double[] rot = outliner.rotation();
            outlinerRotation.rotateZ((float)Math.toRadians(rot[2]))
                    .rotateX((float)Math.toRadians(rot[0]))
                    .rotateY((float)Math.toRadians(rot[1]));
        }

        Quaternionf combinedRotation = new Quaternionf(parentRotation).mul(outlinerRotation);

        for (Object childObj : outliner.children()) {
            String childUuid;
            Cache.Outliner childOutliner = null;

            if (childObj instanceof String) {
                childUuid = childObj.toString();
            } else if (childObj instanceof LinkedTreeMap) {
                LinkedTreeMap<?, ?> map = (LinkedTreeMap<?, ?>) childObj;
                childUuid = (String) map.get("uuid");
                String name = (String) map.get("name");
                double[] origin = map.get("origin") != null
                        ? ((ArrayList<?>) map.get("origin")).stream().mapToDouble(o -> ((Number)o).doubleValue()).toArray()
                        : null;
                boolean visibility = map.get("visibility") != null && (Boolean) map.get("visibility");
                List<Object> children = (List<Object>) map.get("children");
                double[] rotation = map.get("rotation") != null
                        ? ((ArrayList<?>) map.get("rotation")).stream().mapToDouble(o -> ((Number)o).doubleValue()).toArray()
                        : null;
                childOutliner = new Cache.Outliner(name, origin, childUuid, visibility, children, rotation);
            } else {
                String str = childObj.toString();
                if (str.contains("uuid=")) {
                    childUuid = str.split("uuid=")[1].split(",")[0].trim();
                } else {
                    childUuid = null;
                }
            }

            Cache.Element element = entityPOJO.getCache().elements().stream()
                    .filter(e -> e.uuid().equals(childUuid))
                    .findFirst()
                    .orElse(null);

            if (element != null) {
                int index = entityPOJO.getCache().elements().indexOf(element);
                BlockDisplay display = entityPOJO.getBlockDisplays().get(index);

                Transformation currentTransform = display.getTransformation();
                Vector3f pos = currentTransform.getTranslation();
                Vector3f scale = currentTransform.getScale();

                // Origine de l'élément
                Vector3f elementOrigin = new Vector3f(
                        element.origin() != null ? (float)element.origin()[0] / 16.0f : 0f,
                        element.origin() != null ? (float)element.origin()[1] / 16.0f : 0f,
                        element.origin() != null ? (float)element.origin()[2] / 16.0f : 0f
                );

                // Rotation de l'élément (ordre Z, X, Y)
                Quaternionf elementRotation = new Quaternionf().identity();
                if (element.rotation() != null) {
                    double[] rot = element.rotation();
                    elementRotation.rotateZ((float)Math.toRadians(rot[2]))
                            .rotateX((float)Math.toRadians(rot[0]))
                            .rotateY((float)Math.toRadians(rot[1]));
                }

                // Position après rotation outliner
                Vector3f newPos = new Vector3f(pos);

                newPos.sub(outlinerOrigin);
                newPos.rotate(outlinerRotation);
                newPos.add(outlinerOrigin);

                // Position après rotation élément
                newPos.sub(elementOrigin);
                newPos.rotate(elementRotation);
                newPos.add(elementOrigin);

                // Position après rotation parent
                if (parentOrigin != null) {
                    newPos.sub(parentOrigin);
                    newPos.rotate(parentRotation);
                    newPos.add(parentOrigin);
                }

                // Rotation totale
                Quaternionf totalRotation = new Quaternionf(combinedRotation).mul(elementRotation);

                display.setTransformation(new Transformation(
                        newPos,
                        totalRotation,
                        scale,
                        new Quaternionf().identity()
                ));
            }

            // Récupération du sous-outliner pour la récursivité
            if (childOutliner == null && childUuid != null) {
                childOutliner = entityPOJO.getCache().outliner().stream()
                        .filter(o -> o.uuid().equals(childUuid))
                        .findFirst()
                        .orElse(null);
            }

            if (childOutliner != null) {
                applyRotationRecursively(entityPOJO, childOutliner, combinedRotation, outlinerOrigin);
            }
        }
    }

    // ---- Animation playback ----

    public boolean playAnimation(EntityPOJO entityPOJO, String animationName) {
        return playAnimation(entityPOJO, animationName, defaultBlendTicks(), null);
    }

    /**
     * Plays an animation, cross-fading from the rig's current pose over {@code blendTicks} ticks.
     * {@code onEnd} (nullable) is invoked once when a non-looping animation reaches its end.
     */
    public boolean playAnimation(EntityPOJO entityPOJO, String animationName, int blendTicks, Runnable onEnd) {
        AnimationState st = buildState(entityPOJO, animationName);
        if (st == null) return false;

        // Snapshot the current pose of every display so the new animation can cross-fade from it.
        if (blendTicks > 0) {
            List<Transformation> from = new ArrayList<>();
            for (BlockDisplay d : entityPOJO.getBlockDisplays()) from.add(d.getTransformation());
            st.setBlendFrom(from);
            st.setBlendDuration(blendTicks / 20.0);
        }
        st.setOnEnd(onEnd);

        entityPOJO.setAnimationState(st);
        startTicker();
        // Apply the very first frame immediately so there is no one-tick gap.
        applyPose(entityPOJO, st, plugin.getConfig().getBoolean("use-minecraft-interpolation", true),
                Math.max(1, plugin.getConfig().getInt("interpolation-updates", 1)));
        return true;
    }

    /** Freezes the rig on a single frame of an animation at the given time (seconds). */
    public boolean poseAt(EntityPOJO entityPOJO, String animationName, double time) {
        AnimationState st = buildState(entityPOJO, animationName);
        if (st == null) return false;
        st.setTime(Math.max(0, time));
        st.setPaused(true);
        entityPOJO.setAnimationState(st);
        startTicker();
        applyPose(entityPOJO, st, false,
                Math.max(1, plugin.getConfig().getInt("interpolation-updates", 1)));
        return true;
    }

    public boolean pause(EntityPOJO pojo) {
        AnimationState st = pojo.getAnimationState();
        if (st == null) return false;
        st.setPaused(true);
        return true;
    }

    public boolean resume(EntityPOJO pojo) {
        AnimationState st = pojo.getAnimationState();
        if (st == null) return false;
        st.setPaused(false);
        startTicker();
        return true;
    }

    public boolean setSpeed(EntityPOJO pojo, double speed) {
        AnimationState st = pojo.getAnimationState();
        if (st == null) return false;
        st.setSpeed(speed);
        return true;
    }

    public String getCurrentAnimationName(EntityPOJO pojo) {
        AnimationState st = pojo.getAnimationState();
        return st == null ? null : st.getAnimationName();
    }

    public boolean isPlaying(EntityPOJO pojo) {
        AnimationState st = pojo.getAnimationState();
        return st != null && !st.isPaused();
    }

    private int defaultBlendTicks() {
        return Math.max(0, plugin.getConfig().getInt("animation-blend-ticks", 4));
    }

    /** Resolves the animation and precomputes everything the ticker needs. Returns null if not found. */
    private AnimationState buildState(EntityPOJO entityPOJO, String animationName) {
        final Cache cache = entityPOJO.getCache();
        final Cache.Animation animation = cache.animations().stream()
                .filter(a -> a.name().equals(animationName))
                .findFirst()
                .orElse(null);
        if (animation == null) {
            plugin.log.warning("Animation '" + animationName + "' not found.");
            return null;
        }

        final Map<String, List<Cache.Keyframe>> rotKf = new HashMap<>();
        final Map<String, List<Cache.Keyframe>> posKf = new HashMap<>();
        final Map<String, List<Cache.Keyframe>> scaleKf = new HashMap<>();
        for (Map.Entry<String, Cache.Animator> e : animation.animators().entrySet()) {
            rotKf.put(e.getKey(), filterAndSort(e.getValue().keyframes(), "rotation"));
            posKf.put(e.getKey(), filterAndSort(e.getValue().keyframes(), "position"));
            scaleKf.put(e.getKey(), filterAndSort(e.getValue().keyframes(), "scale"));
        }

        final Map<String, Cache.Outliner> outlinerByUuid = new HashMap<>();
        final Map<String, String> outlinerParent = new HashMap<>();
        final Map<String, String> elementParent = new HashMap<>();
        for (Cache.Outliner root : cache.outliner()) {
            indexOutliner(root, null, outlinerByUuid, outlinerParent, elementParent);
        }

        final Map<String, Integer> elementIndex = new HashMap<>();
        for (int i = 0; i < cache.elements().size(); i++) {
            elementIndex.put(cache.elements().get(i).uuid(), i);
        }

        double computedLength = animation.length();
        if (computedLength <= 0) {
            for (Cache.Animator a : animation.animators().values()) {
                for (Cache.Keyframe k : a.keyframes()) {
                    if (k.time() > computedLength) computedLength = k.time();
                }
            }
        }
        final boolean loop = "loop".equals(animation.loop());

        List<AnimationState.SoundCue> sounds = collectSounds(animation);

        AnimationState st = new AnimationState(animation, rotKf, posKf, scaleKf,
                outlinerByUuid, outlinerParent, elementParent, elementIndex, sounds, computedLength, loop);

        // Precompute, for every element, its owning bone chain and whether it is animated at all.
        Map<Integer, List<String>> chains = new HashMap<>();
        Set<Integer> animatedIndices = new HashSet<>();
        for (Map.Entry<String, Integer> entry : elementIndex.entrySet()) {
            List<String> chain = new ArrayList<>();
            String pUuid = elementParent.get(entry.getKey());
            while (pUuid != null) {
                chain.add(0, pUuid);
                pUuid = outlinerParent.get(pUuid);
            }
            chains.put(entry.getValue(), chain);
            for (String boneUuid : chain) {
                if (animation.animators().containsKey(boneUuid)) {
                    animatedIndices.add(entry.getValue());
                    break;
                }
            }
        }
        st.setChains(chains);
        st.setAnimatedIndices(animatedIndices);
        return st;
    }

    /** The global ticker: advances and renders every active animation once per interval. */
    private void tickAll() {
        if (plugin.getEntityPOJOS().isEmpty()) return;
        final int interval = Math.max(1, plugin.getConfig().getInt("interpolation-updates", 1));
        final boolean useMc = plugin.getConfig().getBoolean("use-minecraft-interpolation", true);
        final double dtBase = interval / 20.0;
        final double cullDist = plugin.getConfig().getDouble("cull-distance", 0);
        final double cullSq = cullDist > 0 ? cullDist * cullDist : -1;

        for (EntityPOJO pojo : new ArrayList<>(plugin.getEntityPOJOS())) {
            AnimationState st = pojo.getAnimationState();
            if (st == null) continue;
            tickEntity(pojo, st, dtBase, useMc, interval, cullSq);
        }
    }

    private void tickEntity(EntityPOJO pojo, AnimationState st, double dtBase, boolean useMc, int interval, double cullSq) {
        if (pojo.getRoot() == null || pojo.getRoot().isDead()) {
            stopAnimation(pojo);
            return;
        }
        if (st.isPaused()) return; // hold the current pose, do not advance time

        final double dt = dtBase * st.getSpeed();
        final double length = st.getLength();
        double rawTo = st.getTime() + dt;
        boolean ended = false;

        // ---- Sounds: fire cues crossing the elapsed window (handles a single loop wrap) ----
        List<AnimationState.SoundCue> sounds = st.getSounds();
        int cursor = st.getSoundCursor();
        while (cursor < sounds.size() && sounds.get(cursor).time() <= rawTo
                && (length <= 0 || sounds.get(cursor).time() <= length)) {
            playSound(pojo, sounds.get(cursor));
            cursor++;
        }
        st.setSoundCursor(cursor);

        double newTime;
        if (length > 0 && rawTo >= length) {
            if (st.isLoop()) {
                newTime = rawTo % length;
                cursor = 0;
                while (cursor < sounds.size() && sounds.get(cursor).time() <= newTime) {
                    playSound(pojo, sounds.get(cursor));
                    cursor++;
                }
                st.setSoundCursor(cursor);
            } else {
                newTime = length;
                ended = true;
            }
        } else if (length <= 0 && !st.isLoop()) {
            newTime = rawTo;
            ended = true;
        } else {
            newTime = rawTo;
        }
        st.setTime(newTime);

        if (st.getBlendDuration() > 0) {
            st.setBlendElapsed(st.getBlendElapsed() + dt);
        }

        boolean visible = cullSq < 0 || isVisible(pojo, cullSq);
        if (visible || st.isBlending()) {
            applyPose(pojo, st, useMc, interval);
        }

        if (ended) {
            Runnable cb = st.getOnEnd();
            pojo.setAnimationState(null); // final pose already applied; rig holds it
            if (cb != null) {
                try {
                    cb.run();
                } catch (Exception ex) {
                    plugin.log.warning("Animation end callback failed: " + ex.getMessage());
                }
            }
        }
    }

    /** Computes and applies the pose for the state's current time, cross-fading and culling-aware. */
    private void applyPose(EntityPOJO pojo, AnimationState st, boolean useMc, int interval) {
        final Cache cache = pojo.getCache();
        final double animTime = st.getTime();
        final float rigScale = pojo.getScale();

        Map<String, Vector3f> boneAnimPos = new HashMap<>();
        Map<String, Quaternionf> boneAnimRot = new HashMap<>();
        Map<String, Vector3f> boneAnimScale = new HashMap<>();
        for (String boneUuid : st.getAnimation().animators().keySet()) {
            Vector3f rawPos = interpolateVector3(st.getPosKf().get(boneUuid), animTime, new Vector3f(0, 0, 0));
            boneAnimPos.put(boneUuid, new Vector3f(rawPos.x / 16f, rawPos.y / 16f, rawPos.z / 16f));
            boneAnimRot.put(boneUuid, interpolateRotation(st.getRotKf().get(boneUuid), animTime));
            boneAnimScale.put(boneUuid, interpolateVector3(st.getScaleKf().get(boneUuid), animTime, new Vector3f(1, 1, 1)));
        }

        float blendFactor = 1f;
        boolean blending = st.isBlending();
        if (blending && st.getBlendDuration() > 0) {
            blendFactor = (float) Math.min(1.0, st.getBlendElapsed() / st.getBlendDuration());
        }

        List<BlockDisplay> displays = pojo.getBlockDisplays();
        List<Transformation> base = pojo.getBaseTransformations();

        for (int idx = 0; idx < cache.elements().size(); idx++) {
            BlockDisplay display = idx < displays.size() ? displays.get(idx) : null;
            if (display == null || display.isDead()) continue;

            Transformation target;
            if (st.getAnimatedIndices().contains(idx)) {
                target = computeAnimatedTransform(cache, st, idx, rigScale, boneAnimPos, boneAnimRot, boneAnimScale);
            } else if (idx < base.size()) {
                target = base.get(idx);
            } else {
                target = display.getTransformation();
            }

            if (blending && blendFactor < 1f && st.getBlendFrom() != null && idx < st.getBlendFrom().size()) {
                target = blendTransform(st.getBlendFrom().get(idx), target, blendFactor);
            }

            Transformation prev = st.getApplied().get(idx);
            if (prev == null) {
                display.setInterpolationDuration(0);
                display.setInterpolationDelay(0);
                display.setTransformation(target);
                st.getApplied().put(idx, target);
            } else if (!transformsClose(prev, target)) {
                display.setInterpolationDuration(useMc ? interval : 0);
                display.setInterpolationDelay(0);
                display.setTransformation(target);
                st.getApplied().put(idx, target);
            }
        }
    }

    private Transformation computeAnimatedTransform(Cache cache, AnimationState st, int idx, float rigScale,
                                                    Map<String, Vector3f> boneAnimPos,
                                                    Map<String, Quaternionf> boneAnimRot,
                                                    Map<String, Vector3f> boneAnimScale) {
        Cache.Element element = cache.elements().get(idx);
        List<String> chain = st.getChains().getOrDefault(idx, Collections.emptyList());

        Matrix4f M = new Matrix4f().scale(rigScale);
        for (String oUuid : chain) {
            Cache.Outliner o = st.getOutlinerByUuid().get(oUuid);
            if (o == null) continue;
            Vector3f Oo = originVec(o.origin());
            Quaternionf Rstatic = eulerZXY(o.rotation());
            Vector3f Pa = boneAnimPos.getOrDefault(oUuid, new Vector3f(0, 0, 0));
            Quaternionf Ra = boneAnimRot.getOrDefault(oUuid, new Quaternionf());
            Vector3f Sa = boneAnimScale.getOrDefault(oUuid, new Vector3f(1, 1, 1));

            M.translate(Oo.x + Pa.x, Oo.y + Pa.y, Oo.z + Pa.z);
            M.rotate(Ra);
            M.rotate(Rstatic);
            M.scale(Sa.x, Sa.y, Sa.z);
            M.translate(-Oo.x, -Oo.y, -Oo.z);
        }

        Vector3f Oe = originVec(element.origin());
        Quaternionf Re = eulerZXY(element.rotation());
        M.translate(Oe.x, Oe.y, Oe.z);
        M.rotate(Re);
        M.translate(-Oe.x, -Oe.y, -Oe.z);

        float fx = (float) (element.from()[0] / 16.0);
        float fy = (float) (element.from()[1] / 16.0);
        float fz = (float) (element.from()[2] / 16.0);
        float sx = (float) ((element.to()[0] - element.from()[0]) / 16.0);
        float sy = (float) ((element.to()[1] - element.from()[1]) / 16.0);
        float sz = (float) ((element.to()[2] - element.from()[2]) / 16.0);
        M.translate(fx, fy, fz);
        M.scale(sx, sy, sz);

        Vector3f translation = M.getTranslation(new Vector3f());
        Vector3f finalScale = M.getScale(new Vector3f());
        Quaternionf leftRot = extractRotation(M, finalScale);
        return new Transformation(translation, leftRot, finalScale, new Quaternionf());
    }

    private boolean isVisible(EntityPOJO pojo, double cullSq) {
        Location base = pojo.getRoot().getLocation();
        World w = base.getWorld();
        if (w == null) return false;
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(base) <= cullSq) return true;
        }
        return false;
    }

    private static Transformation blendTransform(Transformation a, Transformation b, float f) {
        Vector3f tr = new Vector3f(a.getTranslation()).lerp(b.getTranslation(), f);
        Vector3f sc = new Vector3f(a.getScale()).lerp(b.getScale(), f);
        Quaternionf lr = new Quaternionf(a.getLeftRotation()).slerp(b.getLeftRotation(), f);
        Quaternionf rr = new Quaternionf(a.getRightRotation()).slerp(b.getRightRotation(), f);
        return new Transformation(tr, lr, sc, rr);
    }

    // ---- Sounds ----

    private List<AnimationState.SoundCue> collectSounds(Cache.Animation animation) {
        List<AnimationState.SoundCue> cues = new ArrayList<>();
        for (Cache.Animator animator : animation.animators().values()) {
            if (animator.keyframes() == null) continue;
            for (Cache.Keyframe kf : animator.keyframes()) {
                if (!"sound".equals(kf.channel())) continue;
                if (kf.data_points() == null || kf.data_points().isEmpty()) continue;
                Map<String, String> dp = kf.data_points().get(0);
                String name = dp.get("effect");
                if (name == null) name = dp.get("sound");
                if (name == null) name = dp.get("file");
                if (name == null || name.isBlank()) continue;
                float volume = parseFloatOr(dp.get("volume"), 1.0f);
                float pitch = parseFloatOr(dp.get("pitch"), 1.0f);
                cues.add(new AnimationState.SoundCue(kf.time(), name.trim(), volume, pitch));
            }
        }
        cues.sort(Comparator.comparingDouble(AnimationState.SoundCue::time));
        return cues;
    }

    private void playSound(EntityPOJO pojo, AnimationState.SoundCue cue) {
        if (pojo.getRoot() == null || !pojo.getRoot().isValid()) return;
        Location loc = pojo.getRoot().getLocation();
        World w = loc.getWorld();
        if (w == null) return;
        String name = cue.sound();
        if (name.startsWith("minecraft:")) name = name.substring("minecraft:".length());
        try {
            w.playSound(loc, name, cue.volume(), cue.pitch());
        } catch (Exception ex) {
            plugin.log.warning("Failed to play sound '" + cue.sound() + "': " + ex.getMessage());
        }
    }

    private static float parseFloatOr(String s, float fallback) {
        if (s == null) return fallback;
        try {
            return Float.parseFloat(s.replace(",", ".").trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ---- Keyframe interpolation (easing + interpolation modes) ----

    private Quaternionf interpolateRotation(List<Cache.Keyframe> keyframes, double time) {
        if (keyframes == null || keyframes.isEmpty()) return new Quaternionf().identity();
        if (keyframes.size() == 1 || time <= keyframes.get(0).time()) {
            return eulerKeyframe(keyframes.get(0));
        }
        for (int i = 0; i < keyframes.size() - 1; i++) {
            Cache.Keyframe kf = keyframes.get(i);
            Cache.Keyframe nextKf = keyframes.get(i + 1);
            if (time >= kf.time() && time <= nextKf.time()) {
                double span = nextKf.time() - kf.time();
                float t = span <= 0 ? 0f : (float) ((time - kf.time()) / span);
                Quaternionf rotA = eulerKeyframe(kf);
                if ("step".equals(safeInterp(kf))) return rotA;
                Quaternionf rotB = eulerKeyframe(nextKf);
                float f = (float) ease(kf.easing(), kf.easingArgs(), t);
                return new Quaternionf(rotA).slerp(rotB, f);
            }
        }
        return eulerKeyframe(keyframes.get(keyframes.size() - 1));
    }

    private Quaternionf eulerKeyframe(Cache.Keyframe kf) {
        Map<String, String> data = kf.data_points().get(0);
        float x = evalExpr(data.get("x"), kf.time());
        float y = evalExpr(data.get("y"), kf.time());
        float z = evalExpr(data.get("z"), kf.time());
        return new Quaternionf().identity()
                .rotateZ((float) Math.toRadians(z))
                .rotateX((float) Math.toRadians(-x))
                .rotateY((float) Math.toRadians(-y));
    }

    private Vector3f interpolateVector3(List<Cache.Keyframe> keyframes, double time, Vector3f fallback) {
        if (keyframes == null || keyframes.isEmpty()) return new Vector3f(fallback);
        if (keyframes.size() == 1 || time <= keyframes.get(0).time()) {
            return vecKeyframe(keyframes.get(0));
        }
        for (int i = 0; i < keyframes.size() - 1; i++) {
            Cache.Keyframe kf = keyframes.get(i);
            Cache.Keyframe nextKf = keyframes.get(i + 1);
            if (time >= kf.time() && time <= nextKf.time()) {
                double span = nextKf.time() - kf.time();
                float t = span <= 0 ? 0f : (float) ((time - kf.time()) / span);
                String interp = safeInterp(kf);
                Vector3f a = vecKeyframe(kf);
                if ("step".equals(interp)) return a;
                Vector3f b = vecKeyframe(nextKf);
                if ("catmullrom".equals(interp) || "smooth".equals(interp)) {
                    Vector3f p0 = vecKeyframe(keyframes.get(Math.max(0, i - 1)));
                    Vector3f p3 = vecKeyframe(keyframes.get(Math.min(keyframes.size() - 1, i + 2)));
                    return catmullRom(p0, a, b, p3, t);
                }
                float f = (float) ease(kf.easing(), kf.easingArgs(), t);
                return new Vector3f(a).lerp(b, f);
            }
        }
        return vecKeyframe(keyframes.get(keyframes.size() - 1));
    }

    private Vector3f vecKeyframe(Cache.Keyframe kf) {
        Map<String, String> data = kf.data_points().get(0);
        return new Vector3f(
                evalExpr(data.get("x"), kf.time()),
                evalExpr(data.get("y"), kf.time()),
                evalExpr(data.get("z"), kf.time()));
    }

    private static String safeInterp(Cache.Keyframe kf) {
        return kf.interpolation() == null ? "linear" : kf.interpolation();
    }

    private static Vector3f catmullRom(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        float x = 0.5f * ((2 * p1.x) + (-p0.x + p2.x) * t + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3);
        float y = 0.5f * ((2 * p1.y) + (-p0.y + p2.y) * t + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3);
        float z = 0.5f * ((2 * p1.z) + (-p0.z + p2.z) * t + (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t2 + (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t3);
        return new Vector3f(x, y, z);
    }

    /** Blockbench keyframe easing applied to the normalized segment factor t in [0,1]. */
    private double ease(String easing, List<String> args, double t) {
        if (easing == null || "linear".equals(easing)) return t;
        switch (easing) {
            case "step": return t < 1 ? 0 : 1;
            case "easeInSine": return 1 - Math.cos((t * Math.PI) / 2);
            case "easeOutSine": return Math.sin((t * Math.PI) / 2);
            case "easeInOutSine": return -(Math.cos(Math.PI * t) - 1) / 2;
            case "easeInQuad": return t * t;
            case "easeOutQuad": return 1 - (1 - t) * (1 - t);
            case "easeInOutQuad": return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
            case "easeInCubic": return t * t * t;
            case "easeOutCubic": return 1 - Math.pow(1 - t, 3);
            case "easeInOutCubic": return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
            case "easeInQuart": return t * t * t * t;
            case "easeOutQuart": return 1 - Math.pow(1 - t, 4);
            case "easeInOutQuart": return t < 0.5 ? 8 * t * t * t * t : 1 - Math.pow(-2 * t + 2, 4) / 2;
            case "easeInQuint": return Math.pow(t, 5);
            case "easeOutQuint": return 1 - Math.pow(1 - t, 5);
            case "easeInOutQuint": return t < 0.5 ? 16 * Math.pow(t, 5) : 1 - Math.pow(-2 * t + 2, 5) / 2;
            case "easeInExpo": return t == 0 ? 0 : Math.pow(2, 10 * t - 10);
            case "easeOutExpo": return t == 1 ? 1 : 1 - Math.pow(2, -10 * t);
            case "easeInOutExpo":
                if (t == 0) return 0;
                if (t == 1) return 1;
                return t < 0.5 ? Math.pow(2, 20 * t - 10) / 2 : (2 - Math.pow(2, -20 * t + 10)) / 2;
            case "easeInCirc": return 1 - Math.sqrt(1 - Math.pow(t, 2));
            case "easeOutCirc": return Math.sqrt(1 - Math.pow(t - 1, 2));
            case "easeInOutCirc":
                return t < 0.5
                        ? (1 - Math.sqrt(1 - Math.pow(2 * t, 2))) / 2
                        : (Math.sqrt(1 - Math.pow(-2 * t + 2, 2)) + 1) / 2;
            case "easeInBack": {
                double c1 = easeArg(args, 1.70158);
                double c3 = c1 + 1;
                return c3 * t * t * t - c1 * t * t;
            }
            case "easeOutBack": {
                double c1 = easeArg(args, 1.70158);
                double c3 = c1 + 1;
                return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
            }
            case "easeInOutBack": {
                double c1 = easeArg(args, 1.70158);
                double c2 = c1 * 1.525;
                return t < 0.5
                        ? (Math.pow(2 * t, 2) * ((c2 + 1) * 2 * t - c2)) / 2
                        : (Math.pow(2 * t - 2, 2) * ((c2 + 1) * (t * 2 - 2) + c2) + 2) / 2;
            }
            case "easeInElastic": {
                if (t == 0) return 0;
                if (t == 1) return 1;
                double c4 = (2 * Math.PI) / 3;
                return -Math.pow(2, 10 * t - 10) * Math.sin((t * 10 - 10.75) * c4);
            }
            case "easeOutElastic": {
                if (t == 0) return 0;
                if (t == 1) return 1;
                double c4 = (2 * Math.PI) / 3;
                return Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * c4) + 1;
            }
            case "easeInOutElastic": {
                if (t == 0) return 0;
                if (t == 1) return 1;
                double c5 = (2 * Math.PI) / 4.5;
                return t < 0.5
                        ? -(Math.pow(2, 20 * t - 10) * Math.sin((20 * t - 11.125) * c5)) / 2
                        : (Math.pow(2, -20 * t + 10) * Math.sin((20 * t - 11.125) * c5)) / 2 + 1;
            }
            case "easeInBounce": return 1 - bounceOut(1 - t);
            case "easeOutBounce": return bounceOut(t);
            case "easeInOutBounce":
                return t < 0.5 ? (1 - bounceOut(1 - 2 * t)) / 2 : (1 + bounceOut(2 * t - 1)) / 2;
            default: return t;
        }
    }

    private static double easeArg(List<String> args, double fallback) {
        if (args == null || args.isEmpty()) return fallback;
        try {
            return Double.parseDouble(args.get(0).replace(",", ".").trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double bounceOut(double t) {
        double n1 = 7.5625;
        double d1 = 2.75;
        if (t < 1 / d1) return n1 * t * t;
        if (t < 2 / d1) return n1 * (t -= 1.5 / d1) * t + 0.75;
        if (t < 2.5 / d1) return n1 * (t -= 2.25 / d1) * t + 0.9375;
        return n1 * (t -= 2.625 / d1) * t + 0.984375;
    }

    private List<Cache.Keyframe> filterAndSort(List<Cache.Keyframe> all, String channel) {
        return all.stream()
                .filter(k -> channel.equals(k.channel()))
                .sorted(Comparator.comparingDouble(Cache.Keyframe::time))
                .toList();
    }

    private void indexOutliner(Cache.Outliner outliner, String parentUuid,
                               Map<String, Cache.Outliner> byUuid,
                               Map<String, String> outlinerParent,
                               Map<String, String> elementParent) {
        byUuid.put(outliner.uuid(), outliner);
        if (parentUuid != null) outlinerParent.put(outliner.uuid(), parentUuid);
        if (outliner.children() == null) return;
        for (Object child : outliner.children()) {
            if (child instanceof String s) {
                elementParent.put(s, outliner.uuid());
            } else if (child instanceof LinkedTreeMap<?, ?> map) {
                Object uuid = map.get("uuid");
                if (uuid == null) continue;
                if (map.get("name") != null && map.get("children") != null) {
                    indexOutliner(mapToOutliner(map), outliner.uuid(), byUuid, outlinerParent, elementParent);
                } else {
                    elementParent.put((String) uuid, outliner.uuid());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Cache.Outliner mapToOutliner(LinkedTreeMap<?, ?> map) {
        String name = (String) map.get("name");
        String uuid = (String) map.get("uuid");
        double[] origin = doubleArray(map.get("origin"));
        boolean visibility = map.get("visibility") instanceof Boolean b && b;
        List<Object> children = (List<Object>) map.get("children");
        double[] rotation = doubleArray(map.get("rotation"));
        return new Cache.Outliner(name, origin, uuid, visibility, children, rotation);
    }

    private double[] doubleArray(Object o) {
        if (!(o instanceof List<?> list)) return null;
        return list.stream().mapToDouble(v -> ((Number) v).doubleValue()).toArray();
    }

    private Vector3f originVec(double[] origin) {
        if (origin == null) return new Vector3f(0, 0, 0);
        return new Vector3f((float) (origin[0] / 16.0), (float) (origin[1] / 16.0), (float) (origin[2] / 16.0));
    }

    private Quaternionf eulerZXY(double[] rot) {
        if (rot == null) return new Quaternionf();
        return new Quaternionf()
                .rotateZ((float) Math.toRadians(rot[2]))
                .rotateX((float) Math.toRadians(rot[0]))
                .rotateY((float) Math.toRadians(rot[1]));
    }

    private Quaternionf extractRotation(Matrix4f M, Vector3f scale) {
        Matrix4f scaleless = new Matrix4f(M);
        if (scale.x > 1e-8f) {
            scaleless.m00(scaleless.m00() / scale.x);
            scaleless.m01(scaleless.m01() / scale.x);
            scaleless.m02(scaleless.m02() / scale.x);
        }
        if (scale.y > 1e-8f) {
            scaleless.m10(scaleless.m10() / scale.y);
            scaleless.m11(scaleless.m11() / scale.y);
            scaleless.m12(scaleless.m12() / scale.y);
        }
        if (scale.z > 1e-8f) {
            scaleless.m20(scaleless.m20() / scale.z);
            scaleless.m21(scaleless.m21() / scale.z);
            scaleless.m22(scaleless.m22() / scale.z);
        }
        return scaleless.getNormalizedRotation(new Quaternionf());
    }

    private static boolean transformsClose(Transformation a, Transformation b) {
        return vecClose(a.getTranslation(), b.getTranslation())
                && vecClose(a.getScale(), b.getScale())
                && quatClose(a.getLeftRotation(), b.getLeftRotation())
                && quatClose(a.getRightRotation(), b.getRightRotation());
    }

    private static boolean vecClose(Vector3f a, Vector3f b) {
        return Math.abs(a.x - b.x) < 1e-4f
                && Math.abs(a.y - b.y) < 1e-4f
                && Math.abs(a.z - b.z) < 1e-4f;
    }

    private static boolean quatClose(Quaternionf a, Quaternionf b) {
        // Sign-independent: q and -q represent the same orientation.
        float dot = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w;
        return Math.abs(dot) > 1f - 1e-5f;
    }

    // ---- Compiled keyframe expressions (sin/cos/tan/abs/log/sqrt, + - * /, parentheses, query.anim_time) ----

    @FunctionalInterface
    private interface Expr {
        double eval(double animTime);
    }

    private final Map<String, Expr> exprCache = new HashMap<>();
    private final Set<String> loggedBadExpr = new HashSet<>();

    private float evalExpr(String raw, double animTime) {
        if (raw == null) return 0f;
        Expr e = exprCache.get(raw);
        if (e == null) {
            e = compile(raw);
            exprCache.put(raw, e);
        }
        return (float) e.eval(animTime);
    }

    private Expr compile(String raw) {
        String s = raw.replace(",", ".").replace("math.", "").replaceAll("\\s+", "").trim();
        if (s.isEmpty()) return t -> 0.0;
        try {
            double c = Double.parseDouble(s);
            return t -> c;
        } catch (NumberFormatException ignored) {
        }
        try {
            return pAddSub(s);
        } catch (Exception ex) {
            if (loggedBadExpr.add(raw)) {
                plugin.log.warning("Cannot compile keyframe expression '" + raw + "': " + ex.getMessage());
            }
            return t -> 0.0;
        }
    }

    private Expr pAddSub(String expr) {
        int parens = 0;
        for (int i = expr.length() - 1; i >= 0; i--) {
            char c = expr.charAt(i);
            if (c == ')') parens++;
            if (c == '(') parens--;
            if (parens == 0 && (c == '+' || (c == '-' && i > 0 && "+-*/(,".indexOf(expr.charAt(i - 1)) == -1))) {
                Expr left = pAddSub(expr.substring(0, i));
                Expr right = pMulDiv(expr.substring(i + 1));
                return c == '+' ? (t -> left.eval(t) + right.eval(t)) : (t -> left.eval(t) - right.eval(t));
            }
        }
        return pMulDiv(expr);
    }

    private Expr pMulDiv(String expr) {
        int parens = 0;
        for (int i = expr.length() - 1; i >= 0; i--) {
            char c = expr.charAt(i);
            if (c == ')') parens++;
            if (c == '(') parens--;
            if (parens == 0 && (c == '*' || c == '/')) {
                Expr left = pMulDiv(expr.substring(0, i));
                Expr right = pUnary(expr.substring(i + 1));
                return c == '*' ? (t -> left.eval(t) * right.eval(t)) : (t -> left.eval(t) / right.eval(t));
            }
        }
        return pUnary(expr);
    }

    private Expr pUnary(String expr) {
        if (expr.startsWith("-")) {
            Expr u = pUnary(expr.substring(1));
            return t -> -u.eval(t);
        }
        if (expr.startsWith("+")) return pUnary(expr.substring(1));
        if (expr.startsWith("sin(")) {
            Expr a = pAddSub(expr.substring(4, expr.length() - 1));
            return t -> Math.sin(Math.toRadians(a.eval(t)));
        }
        if (expr.startsWith("cos(")) {
            Expr a = pAddSub(expr.substring(4, expr.length() - 1));
            return t -> Math.cos(Math.toRadians(a.eval(t)));
        }
        if (expr.startsWith("tan(")) {
            Expr a = pAddSub(expr.substring(4, expr.length() - 1));
            return t -> Math.tan(Math.toRadians(a.eval(t)));
        }
        if (expr.startsWith("abs(")) {
            Expr a = pAddSub(expr.substring(4, expr.length() - 1));
            return t -> Math.abs(a.eval(t));
        }
        if (expr.startsWith("sqrt(")) {
            Expr a = pAddSub(expr.substring(5, expr.length() - 1));
            return t -> Math.sqrt(a.eval(t));
        }
        if (expr.startsWith("log(")) {
            Expr a = pAddSub(expr.substring(4, expr.length() - 1));
            return t -> Math.log(a.eval(t));
        }
        if (expr.startsWith("(")) {
            return pAddSub(expr.substring(1, expr.length() - 1));
        }
        if (expr.equals("query.anim_time")) {
            return t -> t;
        }
        double c = Double.parseDouble(expr);
        return t -> c;
    }

    // ---- Bone naming + element labels ----

    /**
     * Maps each element index to the name of the outliner bone that owns it.
     * Falls back to the element's own name, then to "#index" when nothing is found.
     */
    public Map<Integer, String> elementBoneNames(Cache cache) {
        Map<String, String> uuidToBone = new HashMap<>();
        if (cache.outliner() != null) {
            for (Cache.Outliner root : cache.outliner()) {
                collectBoneNames(root.children(), root.name(), uuidToBone);
            }
        }

        Map<Integer, String> result = new HashMap<>();
        List<Cache.Element> elements = cache.elements();
        for (int i = 0; i < elements.size(); i++) {
            Cache.Element element = elements.get(i);
            String bone = uuidToBone.get(element.uuid());
            if (bone == null) bone = element.name();
            if (bone == null) bone = "#" + i;
            result.put(i, bone);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private void collectBoneNames(List<Object> children, String currentBone, Map<String, String> map) {
        if (children == null) return;
        for (Object child : children) {
            if (child instanceof String leaf) {
                if (currentBone != null) map.put(leaf, currentBone);
            } else if (child instanceof LinkedTreeMap<?, ?> group) {
                String name = group.get("name") instanceof String n ? n : currentBone;
                Object sub = group.get("children");
                if (sub instanceof List<?> list) {
                    collectBoneNames((List<Object>) list, name, map);
                }
            }
        }
    }

    /** Bone name for a single element index. */
    public String boneNameOf(Cache cache, int index) {
        return elementBoneNames(cache).getOrDefault(index, "#" + index);
    }

    /** Distinct bone names in element order, for tab completion. */
    public List<String> boneNames(Cache cache) {
        Map<Integer, String> map = elementBoneNames(cache);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < cache.elements().size(); i++) {
            String name = map.get(i);
            if (name != null && !result.contains(name)) result.add(name);
        }
        return result;
    }

    /**
     * Resolves a user-supplied identifier to an element index. Accepts a raw numeric index, a unique
     * cube label (see {@link #elementLabels}), a bare cube name (first occurrence) or a bone name.
     * Returns -1 if unknown.
     */
    public int elementIndexByName(Cache cache, String identifier) {
        if (identifier == null) return -1;
        try {
            int idx = Integer.parseInt(identifier);
            if (idx >= 0 && idx < cache.elements().size()) return idx;
        } catch (NumberFormatException ignored) {
        }

        for (Map.Entry<Integer, String> entry : elementLabels(cache).entrySet()) {
            if (entry.getValue().equalsIgnoreCase(identifier)) return entry.getKey();
        }

        List<Cache.Element> elements = cache.elements();
        for (int i = 0; i < elements.size(); i++) {
            if (identifier.equalsIgnoreCase(elements.get(i).name())) return i;
        }

        for (Map.Entry<Integer, String> entry : elementBoneNames(cache).entrySet()) {
            if (entry.getValue().equalsIgnoreCase(identifier)) return entry.getKey();
        }
        return -1;
    }

    /**
     * Maps each element index to a unique label built from the cube (element) name. When several
     * cubes share the same name they are disambiguated with a 1-based suffix, e.g. {@code b_1-1},
     * {@code b_1-2}, ...; a name used only once keeps its bare form.
     */
    public Map<Integer, String> elementLabels(Cache cache) {
        List<Cache.Element> elements = cache.elements();
        Map<String, Integer> totals = new HashMap<>();
        for (Cache.Element element : elements) {
            totals.merge(elementBaseName(element), 1, Integer::sum);
        }
        Map<String, Integer> seen = new HashMap<>();
        Map<Integer, String> result = new HashMap<>();
        for (int i = 0; i < elements.size(); i++) {
            String base = elementBaseName(elements.get(i));
            int n = seen.merge(base, 1, Integer::sum);
            result.put(i, totals.get(base) > 1 ? base + "-" + n : base);
        }
        return result;
    }

    private static String elementBaseName(Cache.Element element) {
        return element.name() != null ? element.name() : "cube";
    }

    /** Cube label for a single element index. */
    public String elementLabelOf(Cache cache, int index) {
        return elementLabels(cache).getOrDefault(index, "#" + index);
    }

    /** Cube labels in element order (every entry is unique), for tab completion. */
    public List<String> elementLabelList(Cache cache) {
        Map<Integer, String> labels = elementLabels(cache);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < cache.elements().size(); i++) out.add(labels.get(i));
        return out;
    }

    // ---- Ray picking (shared by edit mode and click interactions) ----

    /** A model element hit by a ray: the rig and the element/display index. */
    public record RayHit(EntityPOJO pojo, int index) {}

    private static final double DEFAULT_REACH = 6.0;

    public RayHit raytrace(Player player) {
        return raytrace(player, DEFAULT_REACH);
    }

    /** Returns the nearest model element the player is looking at within reach, or null. */
    public RayHit raytrace(Player player, double reach) {
        Location eye = player.getEyeLocation();
        Vector vEye = eye.toVector();
        Vector vDir = eye.getDirection();
        Vector3f origin = new Vector3f((float) vEye.getX(), (float) vEye.getY(), (float) vEye.getZ());
        Vector3f dir = new Vector3f((float) vDir.getX(), (float) vDir.getY(), (float) vDir.getZ());

        RayHit best = null;
        double bestT = reach;

        for (EntityPOJO pojo : plugin.getEntityPOJOS()) {
            if (pojo.getRoot() == null || !pojo.getRoot().isValid()) continue;
            Location base = pojo.getRoot().getLocation();
            if (base.getWorld() == null || !base.getWorld().equals(player.getWorld())) continue;

            List<BlockDisplay> displays = pojo.getBlockDisplays();
            for (int i = 0; i < displays.size(); i++) {
                BlockDisplay display = displays.get(i);
                if (display == null || display.isDead()) continue;
                Transformation t = display.getTransformation();
                Location loc = display.getLocation();

                Matrix4f m = new Matrix4f()
                        .translate((float) loc.getX(), (float) loc.getY(), (float) loc.getZ())
                        .rotateY((float) Math.toRadians(-loc.getYaw()))
                        .rotateX((float) Math.toRadians(loc.getPitch()))
                        .translate(t.getTranslation().x, t.getTranslation().y, t.getTranslation().z)
                        .rotate(t.getLeftRotation())
                        .scale(t.getScale())
                        .rotate(t.getRightRotation());
                Matrix4f inv = m.invert(new Matrix4f());

                Vector3f localOrigin = inv.transformPosition(origin, new Vector3f());
                Vector3f localDir = inv.transformDirection(dir, new Vector3f());

                Double tHit = rayUnitBox(localOrigin, localDir);
                if (tHit != null && tHit >= 0 && tHit < bestT) {
                    bestT = tHit;
                    best = new RayHit(pojo, i);
                }
            }
        }
        return best;
    }

    /** Slab test of a ray against the axis-aligned unit cube [0,1]^3. Returns the near hit distance. */
    private Double rayUnitBox(Vector3f o, Vector3f d) {
        double tmin = 0.0;
        double tmax = Double.MAX_VALUE;
        float[] oo = {o.x, o.y, o.z};
        float[] dd = {d.x, d.y, d.z};
        for (int a = 0; a < 3; a++) {
            if (Math.abs(dd[a]) < 1e-8) {
                if (oo[a] < 0 || oo[a] > 1) return null;
            } else {
                double inv = 1.0 / dd[a];
                double t1 = (0 - oo[a]) * inv;
                double t2 = (1 - oo[a]) * inv;
                if (t1 > t2) {
                    double tmp = t1;
                    t1 = t2;
                    t2 = tmp;
                }
                tmin = Math.max(tmin, t1);
                tmax = Math.min(tmax, t2);
                if (tmin > tmax) return null;
            }
        }
        return tmin;
    }

    // ---- Editing / cleanup ----

    /**
     * Writes a new block material for one element to the cache file on disk, reloads the cache,
     * and respawns every entity currently using that cache (preserving name, uuid, scale and location).
     * Returns false if the cache, file or element could not be resolved.
     */
    public boolean editElementMaterial(String cacheName, int elementIndex, Material material) {
        Cache cache = plugin.getAllCaches().stream()
                .filter(c -> c.name().equals(cacheName))
                .findFirst()
                .orElse(null);
        if (cache == null) return false;
        if (elementIndex < 0 || elementIndex >= cache.elements().size()) return false;

        File cacheFile = new File(plugin.cache, cacheName + ".json");
        if (!cacheFile.exists()) return false;

        JsonObject root;
        try (FileReader reader = new FileReader(cacheFile)) {
            root = editGson.fromJson(reader, JsonObject.class);
        } catch (Exception e) {
            plugin.log.warning("edit: failed to read " + cacheFile.getName() + ": " + e.getMessage());
            return false;
        }
        if (root == null || !root.has("elements") || !root.get("elements").isJsonArray()) return false;

        JsonArray elements = root.get("elements").getAsJsonArray();
        if (elementIndex >= elements.size() || !elements.get(elementIndex).isJsonObject()) return false;
        elements.get(elementIndex).getAsJsonObject().addProperty("material", material.name());

        try (FileWriter writer = new FileWriter(cacheFile)) {
            editGson.toJson(root, writer);
        } catch (Exception e) {
            plugin.log.warning("edit: failed to write " + cacheFile.getName() + ": " + e.getMessage());
            return false;
        }

        Cache newCache;
        try {
            newCache = new CacheReader(plugin).readCache(cacheFile);
        } catch (Exception e) {
            plugin.log.warning("edit: failed to reload " + cacheFile.getName() + ": " + e.getMessage());
            return false;
        }
        plugin.getAllCaches().removeIf(c -> c.name().equals(cacheName));
        plugin.getAllCaches().add(newCache);

        for (EntityPOJO old : new ArrayList<>(plugin.getEntityPOJOS())) {
            if (!old.getCache().name().equals(cacheName)) continue;
            if (old.getRoot() == null || !old.getRoot().isValid()) continue;
            String name = old.getName();
            UUID uuid = old.getUuid();
            float scale = old.getScale();
            Location loc = old.getRoot().getLocation();
            removeEntity(old);
            EntityPOJO neo = createEntity(name, newCache, uuid, scale);
            spawnEntity(neo, loc);
            setRotation(neo, loc.getYaw(), loc.getPitch()); // preserve facing across the respawn
        }
        return true;
    }

    /** Stops the animation loop currently running on the entity, if any. */
    public void stopAnimation(EntityPOJO pojo) {
        pojo.setAnimationState(null);
    }

    /**
     * Teleports the whole rig (root + every block display) to the location, keeping the pose.
     * The location's yaw/pitch become the facing direction of the rig.
     */
    public void teleport(EntityPOJO pojo, Location location) {
        Location target = location.clone();
        if (pojo.getRoot() != null && pojo.getRoot().isValid()) pojo.getRoot().teleport(target);
        for (BlockDisplay display : pojo.getBlockDisplays()) {
            if (display != null && !display.isDead()) display.teleport(target);
        }
    }

    /**
     * Rescales an already-spawned rig uniformly. Rescales the stored bind pose; if an animation is
     * active the new scale is picked up on the next tick (the pose is recomputed from {@code rigScale}),
     * otherwise the live displays are rescaled immediately. Returns false if the scale is not positive.
     */
    public boolean setScale(EntityPOJO pojo, float newScale) {
        if (newScale <= 0) return false;
        float old = pojo.getScale();
        if (old <= 0) old = 1f;
        float factor = newScale / old;
        pojo.setScale(newScale);

        List<Transformation> base = pojo.getBaseTransformations();
        for (int i = 0; i < base.size(); i++) base.set(i, scaleAbout(base.get(i), factor));

        AnimationState st = pojo.getAnimationState();
        if (st != null) {
            st.getApplied().clear(); // force a re-push with the new scale on the next tick
        } else {
            for (BlockDisplay d : pojo.getBlockDisplays()) {
                if (d == null || d.isDead()) continue;
                d.setInterpolationDuration(0);
                d.setInterpolationDelay(0);
                d.setTransformation(scaleAbout(d.getTransformation(), factor));
            }
        }
        return true;
    }

    /** Scales a transformation's offset and size about the rig root (rotations untouched). */
    private static Transformation scaleAbout(Transformation t, float factor) {
        return new Transformation(
                new Vector3f(t.getTranslation()).mul(factor),
                t.getLeftRotation(),
                new Vector3f(t.getScale()).mul(factor),
                t.getRightRotation());
    }

    /** Sets the facing direction (yaw/pitch, in degrees) of the whole rig without moving it. */
    public void setRotation(EntityPOJO pojo, float yaw, float pitch) {
        if (pojo.getRoot() != null && pojo.getRoot().isValid()) pojo.getRoot().setRotation(yaw, pitch);
        for (BlockDisplay display : pojo.getBlockDisplays()) {
            if (display != null && !display.isDead()) display.setRotation(yaw, pitch);
        }
    }

    /** Stops the animation and restores the rig to its initial (bind) pose. */
    public void resetAnimation(EntityPOJO pojo) {
        stopAnimation(pojo);
        List<BlockDisplay> displays = pojo.getBlockDisplays();
        List<Transformation> base = pojo.getBaseTransformations();
        for (int i = 0; i < displays.size() && i < base.size(); i++) {
            BlockDisplay display = displays.get(i);
            if (display == null || display.isDead()) continue;
            display.setInterpolationDuration(0);
            display.setInterpolationDelay(0);
            display.setTransformation(base.get(i));
        }
    }

    public void removeEntity(EntityPOJO pojo) {
        stopAnimation(pojo);
        // Remove BlockDisplays
        if (pojo.getBlockDisplays() != null) {
            for (BlockDisplay display : pojo.getBlockDisplays()) {
                if (display != null && !display.isDead()) {
                    display.remove();
                }
            }
        }
        // Remove root ArmorStand
        if (pojo.getRoot() != null && pojo.getRoot().isValid()) {
            pojo.getRoot().remove();
        }
        // Remove from entityPOJOS list
        plugin.getEntityPOJOS().remove(pojo);
    }
}
