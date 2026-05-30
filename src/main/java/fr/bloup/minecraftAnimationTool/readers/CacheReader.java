package fr.bloup.minecraftAnimationTool.readers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.bloup.minecraftAnimationTool.MinecraftAnimationTool;
import fr.bloup.minecraftAnimationTool.records.Cache;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class CacheReader {
    private final MinecraftAnimationTool plugin;

    private final Gson GSON = new GsonBuilder().create();

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
}
