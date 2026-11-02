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
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class ConfigWatcherService {

	private final ConfigurableEnvironment environment;
	private final KioskProperties kioskProperties;
	private Thread watchThread;
	private WatchService watchService;

	@Value("${DATA_DIR:/app/data}")
	private String dataDir;

	public ConfigWatcherService(ConfigurableEnvironment environment, KioskProperties kioskProperties) {
		this.environment = environment;
		this.kioskProperties = kioskProperties;
	}

	@PostConstruct
	public void startWatcher() {
		Path path = Paths.get(dataDir);
		if (!Files.exists(path))
			return;

		try {
			this.watchService = FileSystems.getDefault().newWatchService();
			path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

			this.watchThread = new Thread(() -> {
				try {
					WatchKey key;
					while ((key = watchService.take()) != null) {
						for (WatchEvent<?> event : key.pollEvents()) {
							Path changed = (Path) event.context();
							if (changed.toString().equals("kiosk-config.yml")) {
								System.out.println(
										"External kiosk-config.yml modification detected. Hot-reloading configurations...");
								Thread.sleep(500); // Tiny debounce window to let file write operations conclude safely
								reloadYamlContext();
							}
						}
						key.reset();
					}
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (Exception e) {
					System.err.println("Configuration monitor encountered a parsing fault: " + e.getMessage());
				}
			});
			this.watchThread.setDaemon(true);
			this.watchThread.start();
		} catch (IOException e) {
			System.err.println("Could not initialize file environment watcher instance: " + e.getMessage());
		}
	}

	private synchronized void reloadYamlContext() {
		try {
			String fullPath = dataDir + "/kiosk-config.yml";
			FileSystemResource resource = new FileSystemResource(fullPath);
			if (!resource.exists())
				return;

			// Explicit refresh pass forcing context values down to properties beans
			// definitions mapping targets
			kioskProperties.setAutoLogoutMs(environment.getProperty("autoLogoutMs", Long.class, 120000L));
			kioskProperties.setDiscordWebhook(environment.getProperty("discordWebhook", ""));
			kioskProperties.setScandexApiKey(environment.getProperty("scandexApiKey", ""));
			kioskProperties.setRentalCategories(environment.getProperty("rentalCategories", Map.class, new HashMap<>()));

			System.out.println("Hot-Swap complete! Rental windows and limits are now live.");
		} catch (Exception e) {
			System.err.println("Hot-swap injection compilation processing failed: " + e.getMessage());
		}
	}

	@PreDestroy
	public void stopWatcher() {
		try {
			if (watchService != null)
				watchService.close();
			if (watchThread != null)
				watchThread.interrupt();
		} catch (IOException ignored) {
		}
	}
}
