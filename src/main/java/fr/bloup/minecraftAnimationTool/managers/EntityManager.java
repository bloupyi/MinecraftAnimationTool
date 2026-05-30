package fr.bloup.minecraftAnimationTool.managers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.internal.LinkedTreeMap;
import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import fr.bloup.minecraftAnimationTool.pojos.EntityPOJO;
import fr.bloup.minecraftAnimationTool.readers.CacheReader;
import fr.bloup.minecraftAnimationTool.records.Cache;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
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

    public EntityPOJO createEntity(String name, Cache cache) {
        return createEntity(name, cache, UUID.randomUUID());
    }

    public EntityPOJO createEntity(String name, Cache cache, UUID uuid) {
        EntityPOJO entityPOJO = new EntityPOJO(name, uuid, cache);
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

        // Snapshot the bind (rest) pose so the animation can be reset to it later.
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

    public boolean playAnimation(EntityPOJO entityPOJO, String animationName) {
        final Cache cache = entityPOJO.getCache();
        final Cache.Animation animation = cache.animations().stream()
                .filter(a -> a.name().equals(animationName))
                .findFirst()
                .orElse(null);

        if (animation == null) {
            plugin.log.warning("Animation '" + animationName + "' not found.");
            return false;
        }

        stopAnimation(entityPOJO);

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
        final double animLength = computedLength;
        final int intervalTicks = Math.max(1, plugin.getConfig().getInt("interpolation-updates", 1));
        final boolean useMcInterpolation = plugin.getConfig().getBoolean("use-minecraft-interpolation", true);
        final int totalSteps = Math.max(1, (int) Math.ceil(animLength * 20.0 / intervalTicks));
        final boolean loop = "loop".equals(animation.loop());

        entityPOJO.setAnimationTask(new BukkitRunnable() {
            int step = 0;
            final Map<Integer, Transformation> applied = new HashMap<>();

            @Override
            public void run() {
                if (entityPOJO.getRoot() == null || entityPOJO.getRoot().isDead()) {
                    cancel();
                    return;
                }
                if (step > totalSteps) {
                    if (loop) {
                        step = 0;
                    } else {
                        cancel();
                        return;
                    }
                }
                double animTime = (step * intervalTicks) / 20.0;
                if (loop && animLength > 0) animTime = animTime % animLength;

                Map<String, Vector3f> boneAnimPos = new HashMap<>();
                Map<String, Quaternionf> boneAnimRot = new HashMap<>();
                Map<String, Vector3f> boneAnimScale = new HashMap<>();
                for (String boneUuid : animation.animators().keySet()) {
                    Vector3f rawPos = interpolateVector3(posKf.get(boneUuid), animTime, new Vector3f(0, 0, 0));
                    boneAnimPos.put(boneUuid, new Vector3f(rawPos.x / 16f, rawPos.y / 16f, rawPos.z / 16f));
                    boneAnimRot.put(boneUuid, interpolateRotation(rotKf.get(boneUuid), animTime));
                    boneAnimScale.put(boneUuid, interpolateVector3(scaleKf.get(boneUuid), animTime, new Vector3f(1, 1, 1)));
                }

                for (Map.Entry<String, Integer> entry : elementIndex.entrySet()) {
                    int idx = entry.getValue();
                    Cache.Element element = cache.elements().get(idx);
                    BlockDisplay display = entityPOJO.getBlockDisplays().get(idx);
                    if (display == null || display.isDead()) continue;

                    List<String> chain = new ArrayList<>();
                    String pUuid = elementParent.get(entry.getKey());
                    while (pUuid != null) {
                        chain.add(0, pUuid);
                        pUuid = outlinerParent.get(pUuid);
                    }

                    boolean animated = false;
                    for (String boneUuid : chain) {
                        if (animation.animators().containsKey(boneUuid)) {
                            animated = true;
                            break;
                        }
                    }
                    if (!animated) continue;

                    Matrix4f M = new Matrix4f();
                    for (String oUuid : chain) {
                        Cache.Outliner o = outlinerByUuid.get(oUuid);
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

                    Transformation next = new Transformation(translation, leftRot, finalScale, new Quaternionf());
                    Transformation prev = applied.get(idx);
                    if (prev == null) {
                        display.setInterpolationDuration(0);
                        display.setInterpolationDelay(0);
                        display.setTransformation(next);
                        applied.put(idx, next);
                    } else if (!transformsClose(prev, next)) {
                        display.setInterpolationDuration(useMcInterpolation ? intervalTicks : 0);
                        display.setInterpolationDelay(0);
                        display.setTransformation(next);
                        applied.put(idx, next);
                    }
                }

                step++;
            }
        }.runTaskTimer(plugin, 0L, intervalTicks));
        return true;
    }

    // Interpolate rotation between keyframes for a given time
    private Quaternionf interpolateRotation(List<Cache.Keyframe> keyframes, double time) {
        if (keyframes.isEmpty()) return new Quaternionf().identity();
        if (keyframes.size() == 1 || time <= keyframes.get(0).time()) {
            Map<String, String> data = keyframes.get(0).data_points().get(0);
            float x = evalExpressionOrFloat(data.get("x"), keyframes.get(0).time());
            float y = evalExpressionOrFloat(data.get("y"), keyframes.get(0).time());
            float z = evalExpressionOrFloat(data.get("z"), keyframes.get(0).time());
            return new Quaternionf().identity()
                    .rotateZ((float)Math.toRadians(z))
                    .rotateX((float)Math.toRadians(-x))
                    .rotateY((float)Math.toRadians(-y));
        }
        for (int i = 0; i < keyframes.size() - 1; i++) {
            Cache.Keyframe kf = keyframes.get(i);
            Cache.Keyframe nextKf = keyframes.get(i + 1);
            if (time >= kf.time() && time <= nextKf.time()) {
                float t = (float) ((time - kf.time()) / (nextKf.time() - kf.time()));
                Map<String, String> dataA = kf.data_points().get(0);
                Map<String, String> dataB = nextKf.data_points().get(0);
                float xA = evalExpressionOrFloat(dataA.get("x"), kf.time());
                float yA = evalExpressionOrFloat(dataA.get("y"), kf.time());
                float zA = evalExpressionOrFloat(dataA.get("z"), kf.time());
                float xB = evalExpressionOrFloat(dataB.get("x"), nextKf.time());
                float yB = evalExpressionOrFloat(dataB.get("y"), nextKf.time());
                float zB = evalExpressionOrFloat(dataB.get("z"), nextKf.time());
                Quaternionf rotA = new Quaternionf().identity()
                        .rotateZ((float)Math.toRadians(zA))
                        .rotateX((float)Math.toRadians(-xA))
                        .rotateY((float)Math.toRadians(-yA));
                Quaternionf rotB = new Quaternionf().identity()
                        .rotateZ((float)Math.toRadians(zB))
                        .rotateX((float)Math.toRadians(-xB))
                        .rotateY((float)Math.toRadians(-yB));
                return new Quaternionf(rotA).slerp(rotB, t);
            }
        }
        // After last keyframe
        Map<String, String> data = keyframes.get(keyframes.size()-1).data_points().get(0);
        float x = evalExpressionOrFloat(data.get("x"), keyframes.get(keyframes.size()-1).time());
        float y = evalExpressionOrFloat(data.get("y"), keyframes.get(keyframes.size()-1).time());
        float z = evalExpressionOrFloat(data.get("z"), keyframes.get(keyframes.size()-1).time());
        return new Quaternionf().identity()
                .rotateZ((float)Math.toRadians(z))
                .rotateX((float)Math.toRadians(-x))
                .rotateY((float)Math.toRadians(-y));
    }

    // Interpolate position or scale between keyframes for a given time
    private Vector3f interpolateVector3(List<Cache.Keyframe> keyframes, double time, Vector3f fallback) {
        if (keyframes.isEmpty()) return new Vector3f(fallback);
        if (keyframes.size() == 1 || time <= keyframes.get(0).time()) {
            Map<String, String> data = keyframes.get(0).data_points().get(0);
            float x = evalExpressionOrFloat(data.get("x"), keyframes.get(0).time());
            float y = evalExpressionOrFloat(data.get("y"), keyframes.get(0).time());
            float z = evalExpressionOrFloat(data.get("z"), keyframes.get(0).time());
            return new Vector3f(x, y, z);
        }
        for (int i = 0; i < keyframes.size() - 1; i++) {
            Cache.Keyframe kf = keyframes.get(i);
            Cache.Keyframe nextKf = keyframes.get(i + 1);
            if (time >= kf.time() && time <= nextKf.time()) {
                float t = (float) ((time - kf.time()) / (nextKf.time() - kf.time()));
                Map<String, String> dataA = kf.data_points().get(0);
                Map<String, String> dataB = nextKf.data_points().get(0);
                float xA = evalExpressionOrFloat(dataA.get("x"), kf.time());
                float yA = evalExpressionOrFloat(dataA.get("y"), kf.time());
                float zA = evalExpressionOrFloat(dataA.get("z"), kf.time());
                float xB = evalExpressionOrFloat(dataB.get("x"), nextKf.time());
                float yB = evalExpressionOrFloat(dataB.get("y"), nextKf.time());
                float zB = evalExpressionOrFloat(dataB.get("z"), nextKf.time());
                return new Vector3f(xA, yA, zA).lerp(new Vector3f(xB, yB, zB), t);
            }
        }
        Map<String, String> data = keyframes.get(keyframes.size()-1).data_points().get(0);
        float x = evalExpressionOrFloat(data.get("x"), keyframes.get(keyframes.size()-1).time());
        float y = evalExpressionOrFloat(data.get("y"), keyframes.get(keyframes.size()-1).time());
        float z = evalExpressionOrFloat(data.get("z"), keyframes.get(keyframes.size()-1).time());
        return new Vector3f(x, y, z);
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

    // Mini-parser maison pour sin/cos/tan/abs/log/sqrt, *, +, -, /, parenthèses, et query.anim_time
    private float evalExpressionOrFloat(String expr, double animTime) {
        if (expr == null) return 0f;
        expr = expr.replace(",", ".").trim();
        expr = expr.replace("math.", "");
        try {
            return Float.parseFloat(expr);
        } catch (NumberFormatException ignored) {}
        try {
            // Remplacement de la variable
            String e = expr.replace("query.anim_time", Double.toString(animTime));
            // Évaluation récursive simple
            return (float)evaluateMathExpr(e);
        } catch (Exception ex) {
            plugin.log.warning("[DEBUG] evalExpressionOrFloat: Erreur d'évaluation maison pour '" + expr + "' : " + ex.getMessage());
            return 0f;
        }
    }

    // Évaluateur mathématique simple (supporte +, -, *, /, sin, cos, tan, abs, sqrt, log, parenthèses)
    private double evaluateMathExpr(String expr) {
        expr = expr.replaceAll("\\s+", "");
        return parseAddSub(expr);
    }
    // Addition/Soustraction
    private double parseAddSub(String expr) {
        int parens = 0;
        for (int i = expr.length() - 1; i >= 0; i--) {
            char c = expr.charAt(i);
            if (c == ')') parens++;
            if (c == '(') parens--;
            if (parens == 0 && (c == '+' || (c == '-' && i > 0 && "+-*/(,".indexOf(expr.charAt(i-1)) == -1))) {
                double left = parseAddSub(expr.substring(0, i));
                double right = parseMulDiv(expr.substring(i + 1));
                return c == '+' ? left + right : left - right;
            }
        }
        return parseMulDiv(expr);
    }
    // Multiplication/Division
    private double parseMulDiv(String expr) {
        int parens = 0;
        for (int i = expr.length() - 1; i >= 0; i--) {
            char c = expr.charAt(i);
            if (c == ')') parens++;
            if (c == '(') parens--;
            if (parens == 0 && (c == '*' || c == '/')) {
                double left = parseMulDiv(expr.substring(0, i));
                double right = parseUnary(expr.substring(i + 1));
                return c == '*' ? left * right : left / right;
            }
        }
        return parseUnary(expr);
    }
    // Fonctions et parenthèses
    private double parseUnary(String expr) {
        if (expr.startsWith("-")) return -parseUnary(expr.substring(1));
        if (expr.startsWith("+")) return parseUnary(expr.substring(1));
        if (expr.startsWith("sin(")) return Math.sin(Math.toRadians(parseAddSub(expr.substring(4, expr.length() - 1))));
        if (expr.startsWith("cos(")) return Math.cos(Math.toRadians(parseAddSub(expr.substring(4, expr.length() - 1))));
        if (expr.startsWith("tan(")) return Math.tan(Math.toRadians(parseAddSub(expr.substring(4, expr.length() - 1))));
        if (expr.startsWith("abs(")) return Math.abs(parseAddSub(expr.substring(4, expr.length() - 1)));
        if (expr.startsWith("sqrt(")) return Math.sqrt(parseAddSub(expr.substring(5, expr.length() - 1)));
        if (expr.startsWith("log(")) return Math.log(parseAddSub(expr.substring(4, expr.length() - 1)));
        if (expr.startsWith("(")) return parseAddSub(expr.substring(1, expr.length() - 1));
        // Constante/numérique
        return Double.parseDouble(expr);
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

    /**
     * Writes a new block material for one element to the cache file on disk, reloads the cache,
     * and respawns every entity currently using that cache (preserving name, uuid and location).
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
            Location loc = old.getRoot().getLocation();
            removeEntity(old);
            EntityPOJO neo = createEntity(name, newCache, uuid);
            spawnEntity(neo, loc);
            setRotation(neo, loc.getYaw(), loc.getPitch()); // preserve facing across the respawn
        }
        return true;
    }

    /** Stops the animation loop currently running on the entity, if any. */
    public void stopAnimation(EntityPOJO pojo) {
        if (pojo.getAnimationTask() != null) {
            pojo.getAnimationTask().cancel();
            pojo.setAnimationTask(null);
        }
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
