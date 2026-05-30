package fr.bloup.minecraftAnimationTool.readers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class BlueprintCache {
    private final MinecraftAnimationTool plugin;

    private final Gson gson = new Gson();
    private final List<String> ignoredKeys = Arrays.asList(
            "box_uv",
            "locked",
            "light_emission",
            "allow_mirror_modeling",
            "mirror_uv",
            "autouv",
            "color",
            "faces",
            "export",
            "isOpen",
            "selected"
    );

    public void cacheBlueprint() {
        File[] bbmodelFiles = plugin.blueprints.listFiles((dir, name) -> name.endsWith(".bbmodel"));
        if (bbmodelFiles == null) {
            plugin.log.warning("No blueprint .bbmodel files found in " + plugin.blueprints.getAbsolutePath());
            return;
        }
        for (File bbmodelFile : bbmodelFiles) {
            String cacheFileName = bbmodelFile.getName().replaceAll("\\.bbmodel$", ".json");
            File cacheFile = new File(plugin.cache, cacheFileName);

            Map<String, String> preservedMaterials = readExistingMaterials(cacheFile);

            JsonObject cache = new JsonObject();
            JsonElement elements = getBlueprintKey(bbmodelFile, "elements");
            if (elements.isJsonArray() && !preservedMaterials.isEmpty()) {
                for (JsonElement el : elements.getAsJsonArray()) {
                    if (!el.isJsonObject()) continue;
                    JsonObject obj = el.getAsJsonObject();
                    if (!obj.has("uuid")) continue;
                    String mat = preservedMaterials.get(obj.get("uuid").getAsString());
                    if (mat != null) obj.addProperty("material", mat);
                }
            }
            cache.add("elements", elements);
            cache.add("outliner", getBlueprintKey(bbmodelFile, "outliner"));
            cache.add("animations", getBlueprintKey(bbmodelFile, "animations"));

            try (FileWriter writer = new FileWriter(cacheFile)) {
                gson.toJson(cache, writer);
            } catch (IOException e) {
                plugin.log.warning("Failed to write cache for " + bbmodelFile.getName() + ": " + e.getMessage());
            }
        }
    }

    private Map<String, String> readExistingMaterials(File cacheFile) {
        Map<String, String> out = new HashMap<>();
        if (!cacheFile.exists()) return out;
        try (FileReader reader = new FileReader(cacheFile)) {
            JsonObject existing = gson.fromJson(reader, JsonObject.class);
            if (existing == null || !existing.has("elements") || !existing.get("elements").isJsonArray()) return out;
            for (JsonElement el : existing.get("elements").getAsJsonArray()) {
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("uuid") && obj.has("material") && !obj.get("material").isJsonNull()) {
                    out.put(obj.get("uuid").getAsString(), obj.get("material").getAsString());
                }
            }
        } catch (Exception e) {
            plugin.log.warning("Failed to preserve materials from " + cacheFile.getName() + ": " + e.getMessage());
        }
        return out;
    }

    public JsonElement getBlueprintKey(File blueprintFile, String key) {
        try (FileReader reader = new FileReader(blueprintFile)) {
            JsonObject jsonObject = gson.fromJson(reader, JsonObject.class);
            JsonElement element = jsonObject.get(key);

            if (element == null) {
                plugin.log.warning("Key '" + key + "' not found in " + blueprintFile.getName());
                return new JsonObject();
            }

            if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();
                for (String ignoredKey : ignoredKeys) {
                    obj.remove(ignoredKey);
                }
                return obj;
            } else if (element.isJsonArray()) {
                JsonArray array = element.getAsJsonArray();
                JsonArray filteredArray = new JsonArray();
                for (JsonElement item : array) {
                    if (item.isJsonObject()) {
                        JsonObject obj = item.getAsJsonObject();
                        for (String ignoredKey : ignoredKeys) {
                            obj.remove(ignoredKey);
                        }
                        filteredArray.add(obj);
                    } else {
                        filteredArray.add(item);
                    }
                }
                return filteredArray;
            } else {
                plugin.log.warning("Key '" + key + "' in " + blueprintFile.getName() + " is neither an object nor an array.");
                return new JsonObject();
            }
        } catch (IOException e) {
            plugin.log.warning("Failed to read " + blueprintFile.getName() + ": " + e.getMessage());
            return new JsonObject();
        } catch (Exception e) {
            plugin.log.warning("Error processing " + blueprintFile.getName() + ": " + e.getMessage());
            return new JsonObject();
        }
    }
}