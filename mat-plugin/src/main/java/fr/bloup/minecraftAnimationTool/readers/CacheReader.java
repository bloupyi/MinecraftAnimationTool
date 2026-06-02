package fr.bloup.minecraftAnimationTool.readers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import fr.bloup.minecraftAnimationTool.records.Cache;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class CacheReader {
    private final MinecraftAnimationTool plugin;

    private final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Cache.class, new CacheDeserializer())
            .create();

    public Cache readCache(File file) throws IOException {
        try (FileReader reader = new FileReader(file)) {
            Cache cache = GSON.fromJson(reader, Cache.class);
            String name = file.getName().replaceFirst("\\.json$", "");
            return new Cache(name, cache.elements(), cache.outliner(), cache.animations());
        }
    }

    public List<Cache> readAllCaches() throws IOException {
        List<Cache> caches = new ArrayList<>();
        File cacheDir = plugin.cache;

        if (!cacheDir.exists() || !cacheDir.isDirectory()) {
            plugin.log.severe("Cache directory not found: " + cacheDir.getAbsolutePath());
        }

        File[] files = cacheDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
        if (files == null || files.length == 0) {
            plugin.log.warning("No chache files found: " + cacheDir.getAbsolutePath());
            return caches;
        }

        for (File file : files) {
            try {
                Cache cache = readCache(file);
                caches.add(cache);
                plugin.log.info("Loaded " + file.getName() + " (" + cache.elements().size() + " elements)");
            } catch (Exception e) {
                plugin.log.warning("Error while reading " + file.getName() + ": " + e.getMessage());
            }
        }

        return caches;
    }

    /**
     * Tolerant deserializer for the shapes Blockbench actually exports:
     * <ul>
     *   <li>{@code outliner} entries may be group objects OR a bare element-uuid string
     *       (a "loose" cube not nested in any group). Strings are wrapped in a synthetic
     *       group so the element is still picked up by the rotation walk.</li>
     *   <li>{@code animations} may be a JSON array or a uuid-keyed object; both collapse
     *       to a {@code List<Animation>}.</li>
     * </ul>
     */
    private static class CacheDeserializer implements JsonDeserializer<Cache> {
        private static final Type ELEMENT_LIST = new TypeToken<List<Cache.Element>>() {}.getType();

        @Override
        public Cache deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext ctx) {
            JsonObject root = json.getAsJsonObject();

            List<Cache.Element> elements = root.has("elements") && root.get("elements").isJsonArray()
                    ? ctx.deserialize(root.get("elements"), ELEMENT_LIST)
                    : new ArrayList<>();

            List<Cache.Outliner> outliner = new ArrayList<>();
            if (root.has("outliner") && root.get("outliner").isJsonArray()) {
                for (JsonElement entry : root.getAsJsonArray("outliner")) {
                    if (entry.isJsonObject()) {
                        outliner.add(ctx.deserialize(entry, Cache.Outliner.class));
                    } else if (entry.isJsonPrimitive()) {
                        // Loose root element: wrap it in a zero-transform group so the
                        // rotation walk still applies its own element-level transform.
                        String uuid = entry.getAsString();
                        List<Object> children = new ArrayList<>();
                        children.add(uuid);
                        outliner.add(new Cache.Outliner(
                                "root", new double[]{0, 0, 0}, "loose-" + uuid, true, children, null));
                    }
                }
            }

            List<Cache.Animation> animations = new ArrayList<>();
            if (root.has("animations")) {
                JsonElement anims = root.get("animations");
                if (anims.isJsonArray()) {
                    for (JsonElement a : anims.getAsJsonArray()) {
                        animations.add(ctx.deserialize(a, Cache.Animation.class));
                    }
                } else if (anims.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> e : anims.getAsJsonObject().entrySet()) {
                        animations.add(ctx.deserialize(e.getValue(), Cache.Animation.class));
                    }
                }
            }

            return new Cache(null, elements, outliner, animations);
        }
    }
}
