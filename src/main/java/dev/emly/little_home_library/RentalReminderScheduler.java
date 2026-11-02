package dev.emly.little_home_library;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import dev.emly.little_home_library.KioskProperties.CategoryDetails;

@Service
@EnableScheduling
public class RentalReminderScheduler {

	private final RentalRepository rentalRepository;
	private final LibraryService libraryService;
	private final KioskProperties kioskProperties;
	private final RestTemplate restTemplate = new RestTemplate();

	public RentalReminderScheduler(RentalRepository rentalRepository, LibraryService libraryService,
			KioskProperties kioskProperties) {
		this.rentalRepository = rentalRepository;
		this.libraryService = libraryService;
		this.kioskProperties = kioskProperties;
	}

	// Runs automatically every day at 8:00 AM server local time
	@Scheduled(cron = "0 0 8 * * ?")
	public void executeDailyReminderChecks() {
		if (kioskProperties.getDiscordWebhook() == null || kioskProperties.getDiscordWebhook().isEmpty())
			return;

		List<Rental> activeRentals = rentalRepository.findAll();
		Instant now = Instant.now();

		for (Rental rental : activeRentals) {
			ItemDetails item = libraryService.resolveItem(rental.getIsbn());

			// Resolve maximum checkout duration allowed for this item category from active
			// config maps
			int allowedDays = kioskProperties.getRentalCategories().getOrDefault(item.category(), new CategoryDetails())
					.getRentalPeriod();
			Instant dueTime = rental.getCheckoutTime().plus(Duration.ofDays(allowedDays));

			if (now.isAfter(dueTime))
				continue; // Already overdue, skip reminders

			long daysRemaining = Duration.between(now, dueTime).toDays() + 1; // Round up daylight boundary windows

			if (daysRemaining == 3 || daysRemaining == 2 || daysRemaining == 1) {
				String message = String.format("⏰ **Return Reminder**: User `%s` has \"%s\" (%s) due in %d day(s).",
						rental.getUserCard(), item.title(), item.category(), daysRemaining);

				try {
					restTemplate.postForObject(kioskProperties.getDiscordWebhook(), Map.of("content", message),
							String.class);
				} catch (Exception ignored) {
				}
			}
		}
	}
}
