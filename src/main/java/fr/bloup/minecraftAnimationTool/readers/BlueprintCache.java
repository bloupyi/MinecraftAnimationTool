package fr.bloup.minecraftAnimationTool.reader;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import lombok.RequiredArgsConstructor;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

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
        if (bbmodelFiles != null) {
            for (File bbmodelFile : bbmodelFiles) {
                JsonObject cache = new JsonObject();
                cache.add("elements", getBlueprintKey(bbmodelFile, "elements"));
                cache.add("outliner", getBlueprintKey(bbmodelFile, "outliner"));
                cache.add("animations", getBlueprintKey(bbmodelFile, "animations"));

                String cacheFileName = bbmodelFile.getName().replaceAll("\\.bbmodel$", ".json");
                File cacheFile = new File(plugin.cache, cacheFileName);

                try (FileWriter writer = new FileWriter(cacheFile)) {
                    gson.toJson(cache, writer);
                } catch (IOException e) {
                    plugin.log.warning("Failed to write cache for " + bbmodelFile.getName() + ": " + e.getMessage());
                }
            }
        } else {
            plugin.log.warning("No blueprint .bbmodel files found in " + plugin.blueprints.getAbsolutePath());
        }
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