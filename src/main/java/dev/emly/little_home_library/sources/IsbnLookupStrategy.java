package dev.emly.little_home_library.sources;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import dev.emly.little_home_library.ItemDetails;

@Component
@Order(2)
public class IsbnLookupStrategy implements ItemLookupStrategy {
	private final RestTemplate restTemplate = new RestTemplate();

	@Override
	public Optional<ItemDetails> lookup(String barcode) {
		String cleanIsbn = barcode.replaceAll("[^0-9X]", "");
		try {
			String url = "https://openlibrary.org/isbn/" + cleanIsbn + ".json";
			Map<String, Object> details = restTemplate.getForObject(url, Map.class);
			if (details != null && !details.isEmpty()) {
				String title = details.getOrDefault("title", "Unknown Book").toString();
				String releaseDate = details.getOrDefault("publish_date", "N/A").toString();
				String author = "Unknown Author";
				if (details.containsKey("authors")) {
					var authorsList = (List<Map<String, Object>>) details.get("authors");
					if (!authorsList.isEmpty()) {
						Map<String, Object> authorRef = authorsList.get(0);
						if (authorRef.containsKey("key")) {
							String authorUrl = "https://openlibrary.org" + authorRef.get("key").toString() + ".json";
							Map<String, Object> authorDetails = restTemplate.getForObject(authorUrl, Map.class);
							if (authorDetails != null && authorDetails.containsKey("name")) {
								author = authorDetails.get("name").toString();
							}
						}
					}
				}
				String coverUrl = "https://openlibrary.org" + cleanIsbn + "-M.jpg";
				return Optional.of(new ItemDetails(title, author, "book", releaseDate, coverUrl));
			}
		} catch (Exception ignored) {
		}
		return Optional.empty();
	}

	@Override
	public boolean supports(String barcode) {
		String clean = barcode.replaceAll("[^0-9X]", "");
		// --- FIXED: Explicitly look for the 978/979 Bookland prefix for 13-digit
		// ISBNs, or standard 10-digit formats ---
		return (clean.length() == 13 && (clean.startsWith("978") || clean.startsWith("979"))) || (clean.length() == 10);
	}
}
