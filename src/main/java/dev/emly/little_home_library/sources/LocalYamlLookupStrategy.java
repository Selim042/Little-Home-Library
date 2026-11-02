package dev.emly.little_home_library.sources;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import dev.emly.little_home_library.ItemDetails;
import jakarta.annotation.PostConstruct;

@Component
@Order(1) // Run this strategy first to honor your direct local inventory overrides
public class LocalYamlLookupStrategy implements ItemLookupStrategy {

	@Value("${DATA_DIR:/app/data}")
	private String dataDir;

	private Map<String, Map<String, String>> itemOverrides = new HashMap<>();
	private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

	@PostConstruct
	public void initLoadOverrides() {
		loadOverridesFromFile();
	}

	public synchronized void loadOverridesFromFile() {
		File file = new File(dataDir + "/item-overrides.yml");
		if (!file.exists()) {
			System.out.println("No item-overrides.yml file found. Initializing empty override system.");
			return;
		}

		try {
			// Read and parse the root map directly out of the split file
			itemOverrides = yamlMapper.readValue(file, Map.class);
			System.out.println("Successfully loaded item metadata overrides dataset.");
		} catch (Exception e) {
			System.err.println("Failed to parse external item-overrides.yml: " + e.getMessage());
		}
	}

	@Override
	public Optional<ItemDetails> lookup(String barcode) {
		if (itemOverrides != null && itemOverrides.containsKey(barcode)) {
			Map<String, String> item = itemOverrides.get(barcode);
			return Optional.of(new ItemDetails(item.getOrDefault("title", "Unknown"),
					item.getOrDefault("author", "Unknown"), item.getOrDefault("category", "unknown"), "N/A", ""));
		}
		return Optional.empty();
	}

	@Override
	public boolean supports(String barcode) {
		return itemOverrides != null && itemOverrides.containsKey(barcode);
	}
}
