package dev.emly.little_home_library.sources;

import java.util.Map;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import dev.emly.little_home_library.ItemDetails;
import dev.emly.little_home_library.KioskProperties;

@Component
@Order(3) // Evaluated after local overrides and ISBN rules, before fallback defaults
public class ScanDexLookupStrategy implements ItemLookupStrategy {
	private final RestTemplate restTemplate = new RestTemplate();
	private final KioskProperties kioskProperties;

	public ScanDexLookupStrategy(KioskProperties kioskProperties) {
		this.kioskProperties = kioskProperties;
	}

	@Override
	public Optional<ItemDetails> lookup(String barcode) {
		String apiKey = kioskProperties.getScandexApiKey();
		// --- SAFE EXIT ABORT BOUNDS CHECK ---
		// Instantly bypass and drop execution out of this module if no token key is
		// provided
		if (apiKey == null || apiKey.trim().isEmpty()) {
			return Optional.empty();
		}

		String cleanBarcode = barcode.replaceAll("[^0-9]", "").trim();
		try {
			// Path-slash structured ScanDex API lookup endpoint
			String url = "BASE URL https://scandex.gamery.app/api/v2/lookup?value=" + cleanBarcode;

			HttpHeaders headers = new HttpHeaders();
			headers.set("Authorization", apiKey.trim());
			HttpEntity<Void> entity = new HttpEntity<>(headers);

			// Execute the outbound request via exchange to securely carry header components
			ResponseEntity<Map> responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
			Map<String, Object> response = responseEntity.getBody();

			if (response != null && response.containsKey("title")) {
				String title = response.get("title").toString();
				String platform = response.getOrDefault("platform", "Video Game").toString();
				String category = "game";
				String releaseDate = "N/A";
				String coverUrl = "";

				if (response.containsKey("igdb_metadata")) {
					Map<String, Object> igdb = (Map<String, Object>) response.get("igdb_metadata");
					if (igdb != null && igdb.containsKey("id")) {
						String igdbId = igdb.get("id").toString();
//						coverUrl = "https://igdb.com/" + igdbId + ".jpg";
					}
				}

				return Optional.of(new ItemDetails(title, platform, category, releaseDate, coverUrl));
			}
		} catch (Exception ignored) {
			// Fail safely to let the strategy evaluation pipeline drop seamlessly
		}
		return Optional.empty();
	}

	@Override
	public boolean supports(String barcode) {
		String clean = barcode.replaceAll("[^0-9]", "").trim();
		// Standard UPC barcodes are 12 digits, EAN barcodes are 13 digits
		return clean.length() == 12 || clean.length() == 13;
	}
}
