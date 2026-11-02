package dev.emly.little_home_library;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import dev.emly.little_home_library.sources.LocalYamlLookupStrategy;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class ItemOverridesWatcherService {

    private final LocalYamlLookupStrategy localYamlLookupStrategy;
    private Thread watchThread;
    private WatchService watchService;

    @Value("${DATA_DIR:/app/data}")
    private String dataDir;

    public ItemOverridesWatcherService(LocalYamlLookupStrategy localYamlLookupStrategy) {
        this.localYamlLookupStrategy = localYamlLookupStrategy;
    }

    @PostConstruct
    public void startWatcher() {
        Path path = Paths.get(dataDir);
        if (!Files.exists(path)) return;

        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

            this.watchThread = new Thread(() -> {
                try {
                    WatchKey key;
                    while ((key = watchService.take()) != null) {
                        for (WatchEvent<?> event : key.pollEvents()) {
                            Path changed = (Path) event.context();
                            if (changed.toString().equals("item-overrides.yml")) {
                                System.out.println("⚡ External item-overrides.yml modification detected. Hot-reloading inventory datasets...");
                                Thread.sleep(200); // Small stability debounce window
                                localYamlLookupStrategy.loadOverridesFromFile();
                            }
                        }
                        key.reset();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    System.err.println("Item overrides monitor encountered a processing fault: " + e.getMessage());
                }
            });
            this.watchThread.setDaemon(true);
            this.watchThread.start();
        } catch (IOException e) {
            System.err.println("Could not initialize independent item overrides file watcher: " + e.getMessage());
        }
    }

    @PreDestroy
    public void stopWatcher() {
        try {
            if (watchService != null) watchService.close();
            if (watchThread != null) watchThread.interrupt();
        } catch (IOException ignored) {}
    }
}
