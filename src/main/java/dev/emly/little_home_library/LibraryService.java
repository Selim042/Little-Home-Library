package dev.emly.little_home_library;

import java.io.File;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import dev.emly.little_home_library.sources.ItemLookupStrategy;
import dev.emly.little_home_library.sources.LocalYamlLookupStrategy;

@Service
public class LibraryService {
	private final RentalRepository rentalRepository;
	private final BookCacheRepository bookCacheRepository;
	private final KioskProperties kioskProperties;
	private final List<ItemLookupStrategy> lookupStrategies;
	private final LocalYamlLookupStrategy localYamlLookupStrategy;
	private final RestTemplate restTemplate = new RestTemplate();
	private final com.fasterxml.jackson.databind.ObjectMapper yamlMapper = new com.fasterxml.jackson.databind.ObjectMapper(
			new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());

	@Value("${DATA_DIR:/app/data}")
	private String dataDir;

	public LibraryService(RentalRepository rentalRepository, BookCacheRepository bookCacheRepository,
			KioskProperties kioskProperties, List<ItemLookupStrategy> lookupStrategies,
			LocalYamlLookupStrategy localYamlLookupStrategy) {
		this.rentalRepository = rentalRepository;
		this.bookCacheRepository = bookCacheRepository;
		this.kioskProperties = kioskProperties;
		this.lookupStrategies = lookupStrategies;
		this.localYamlLookupStrategy = localYamlLookupStrategy;
	}

	public List<Rental> getActiveRentals(String userCard) {
		return rentalRepository.findByUserCard(userCard);
	}

	// --- REFACTORED: 7-DAY AUTOMATED DATABASE CACHE EXPIRATION ---
	public ItemDetails resolveItem(String barcode) {
		Optional<BookCache> localCache = bookCacheRepository.findById(barcode);

		if (localCache.isPresent()) {
			BookCache c = localCache.get();

			// Check if the record was cached more than 7 days ago
			boolean isExpired = c.getCachedAt() == null
					|| c.getCachedAt().isBefore(Instant.now().minus(7, ChronoUnit.DAYS));

			// If the item category is manually locked to "unknown", do not force an
			// auto-refresh loop
			if (!isExpired || "unknown".equalsIgnoreCase(c.getCategory())) {
				return new ItemDetails(c.getTitle(), c.getAuthor(), c.getCategory(), c.getReleaseDate(),
						c.getCoverUrl());
			}
			System.out.printf("⚡ Cache expired (>7 days) for barcode %s. Forcing fresh strategic lookup...\n", barcode);
		}

		// Run through the strategy pipeline if missing or expired
		for (ItemLookupStrategy strategy : lookupStrategies) {
			if (strategy.supports(barcode)) {
				Optional<ItemDetails> details = strategy.lookup(barcode);
				if (details.isPresent()) {
					ItemDetails item = details.get();
					// Save or overwrite with a brand new Instant.now() time anchor
					bookCacheRepository.saveAndFlush(new BookCache(barcode, item.title(), item.author(),
							item.category(), item.releaseDate(), item.coverUrl(), Instant.now()));
					return item;
				}
			}
		}

		// Fallback placeholder record updated with current time if completely unindexed
		// across APIs
		ItemDetails unknownFallback = new ItemDetails("Unknown Item (" + barcode + ")", "N/A", "unknown", "N/A", "");
		bookCacheRepository.saveAndFlush(new BookCache(barcode, unknownFallback.title(), unknownFallback.author(),
				unknownFallback.category(), unknownFallback.releaseDate(), unknownFallback.coverUrl(), Instant.now()));
		return unknownFallback;
	}

	@Transactional
	public void processManualItemCheckout(String userCard, String barcode, String title, String author,
			String category) {
		// Save manual entries with a fresh timestamp so they remain valid for 7 days
		// before looking up again
		BookCache manualCache = new BookCache(barcode, title, author, category, "N/A", "", Instant.now());
		BookCache persistedCache = bookCacheRepository.saveAndFlush(manualCache);

		List<Rental> activeRentals = rentalRepository.findByUserCard(userCard);
		long currentCategoryCount = activeRentals.stream().map(r -> resolveItem(r.getIsbn()))
				.filter(i -> i.category().equalsIgnoreCase(category)).count();

		KioskProperties.CategoryDetails catSettings = kioskProperties.getRentalCategories()
				.getOrDefault(category.toLowerCase(), new KioskProperties.CategoryDetails());

		if (currentCategoryCount >= catSettings.getRentalMax()) {
			throw new IllegalStateException("LIMIT_REACHED:" + catSettings.getRentalMax() + ":" + category);
		}

		Rental newRental = new Rental();
		newRental.setUserCard(userCard);
		newRental.setCheckoutTime(Instant.now());
		newRental.setCachedItem(persistedCache);
		rentalRepository.save(newRental);

		File file = new File(dataDir + "/item-overrides.yml");
		Map<String, Map<String, String>> existingOverrides = new HashMap<>();
		try {
			if (file.exists() && file.length() > 0) {
				existingOverrides = yamlMapper.readValue(file, Map.class);
			}
			Map<String, String> itemData = new HashMap<>();
			itemData.put("title", title);
			itemData.put("author", author);
			itemData.put("category", category);
			existingOverrides.put(barcode, itemData);
			yamlMapper.writeValue(file, existingOverrides);
			localYamlLookupStrategy.loadOverridesFromFile();
		} catch (Exception e) {
			System.err.println("Failed to append manual entry to item-overrides.yml: " + e.getMessage());
		}

		ItemDetails itemDetails = new ItemDetails(title, author, category, "N/A", "");
		sendRichDiscordEmbed("📥 Item Checked Out", "User `" + userCard + "` checked out manual entry.", itemDetails,
				3066993);
	}

	public void processBarcode(String userCard, String barcode) {
		Optional<Rental> existingRental = rentalRepository.findByUserCardAndCachedItemIsbn(userCard, barcode);
		ItemDetails item = resolveItem(barcode);

		if (existingRental.isPresent()) {
			rentalRepository.delete(existingRental.get());
			sendRichDiscordEmbed("📤 Item Returned", "User `" + userCard + "` successfully returned an item.", item,
					15143484);
		} else {
			List<Rental> activeRentals = rentalRepository.findByUserCard(userCard);
			long currentCategoryCount = activeRentals.stream().map(r -> resolveItem(r.getIsbn()))
					.filter(i -> i.category().equalsIgnoreCase(item.category())).count();

			KioskProperties.CategoryDetails catSettings = kioskProperties.getRentalCategories()
					.getOrDefault(item.category().toLowerCase(), new KioskProperties.CategoryDetails());

			if (currentCategoryCount >= catSettings.getRentalMax()) {
				throw new IllegalStateException("LIMIT_REACHED:" + catSettings.getRentalMax() + ":" + item.category());
			}

			Rental newRental = new Rental();
			newRental.setUserCard(userCard);
			newRental.setCheckoutTime(Instant.now());

			BookCache currentCache = bookCacheRepository.findById(barcode).orElseGet(() -> {
				ItemDetails resolved = resolveItem(barcode);
				return bookCacheRepository.saveAndFlush(new BookCache(barcode, resolved.title(), resolved.author(),
						resolved.category(), resolved.releaseDate(), resolved.coverUrl(), Instant.now()));
			});

			newRental.setCachedItem(currentCache);
			rentalRepository.save(newRental);

			sendRichDiscordEmbed("📥 Item Checked Out", "User `" + userCard + "` successfully borrowed an item. Due in "
					+ catSettings.getRentalPeriod() + " days.", item, 3066993);
		}
	}

	private void sendRichDiscordEmbed(String statusTitle, String description, ItemDetails item, int decimalColor) {
		String url = kioskProperties.getDiscordWebhook();
		if (url == null || url.trim().isEmpty())
			return;
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			Map<String, Object> embed = new HashMap<>();
			embed.put("title", statusTitle);
			embed.put("description", description);
			embed.put("color", decimalColor);
			List<Map<String, Object>> fields = new ArrayList<>();
			fields.add(Map.of("name", "Title / Name", "value", item.title(), "inline", true));
			fields.add(Map.of("name", "Author / Studio", "value", item.author(), "inline", true));
			fields.add(Map.of("name", "Format Category", "value", item.category().toUpperCase(), "inline", true));
			embed.put("fields", fields);
			if (item.coverUrl() != null && !item.coverUrl().trim().isEmpty()) {
				embed.put("thumbnail", Map.of("url", item.coverUrl()));

			}
			Map<String, Object> rootPayload = new HashMap<>();
			rootPayload.put("embeds", List.of(embed));
			HttpEntity<Map<String, Object>> request = new HttpEntity<>(rootPayload, headers);
			restTemplate.postForLocation(url, request);
		} catch (Exception e) {
			System.err.println("Discord embed webhook payload dispatch failure: " + e.getMessage());
		}
	}
}