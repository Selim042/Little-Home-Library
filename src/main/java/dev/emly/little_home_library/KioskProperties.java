package dev.emly.little_home_library;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties()
public class KioskProperties {
	private long autoLogoutMs = 120000;
	private String discordWebhook = "";
	private List<String> allowedUsers = new ArrayList<>();
	private Map<String, CategoryDetails> rentalCategories = new HashMap<>(); // Updated to nested map structures

	public static class CategoryDetails {
		private int rentalPeriod = 1;
		private int rentalMax = 1;

		public int getRentalPeriod() {
			return rentalPeriod;
		}

		public void setRentalPeriod(int rentalPeriod) {
			this.rentalPeriod = rentalPeriod;
		}

		public int getRentalMax() {
			return rentalMax;
		}

		public void setRentalMax(int rentalMax) {
			this.rentalMax = rentalMax;
		}
	}

	public static class ItemDetails {
		private String title;
		private String author;
		private String category;

		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		public String getAuthor() {
			return author;
		}

		public void setAuthor(String author) {
			this.author = author;
		}

		public String getCategory() {
			return category;
		}

		public void setCategory(String category) {
			this.category = category;
		}
	}

	public long getAutoLogoutMs() {
		return autoLogoutMs;
	}

	public void setAutoLogoutMs(long autoLogoutMs) {
		this.autoLogoutMs = autoLogoutMs;
	}

	public String getDiscordWebhook() {
		return discordWebhook;
	}

	public void setDiscordWebhook(String discordWebhook) {
		this.discordWebhook = discordWebhook;
	}

	public List<String> getAllowedUsers() {
		return allowedUsers;
	}

	public void setAllowedUsers(List<String> allowedUsers) {
		this.allowedUsers = allowedUsers;
	}

	public Map<String, CategoryDetails> getRentalCategories() {
		return rentalCategories;
	}

	public void setRentalCategories(Map<String, CategoryDetails> rentalCategories) {
		this.rentalCategories = rentalCategories;
	}
}
