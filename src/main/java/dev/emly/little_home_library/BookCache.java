package dev.emly.little_home_library;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "book_caches")
public class BookCache {
    @Id
    private String isbn;
    private String title;
    private String author;
    private String category;
    private String releaseDate;
    private String coverUrl;
    private Instant cachedAt; // Added to track cache expiration bounds

    public BookCache() {}

    public BookCache(String isbn, String title, String author, String category, String releaseDate, String coverUrl, Instant cachedAt) {
        this.isbn = isbn;
        this.title = title;
        this.author = author;
        this.category = category;
        this.releaseDate = releaseDate;
        this.coverUrl = coverUrl;
        this.cachedAt = cachedAt;
    }

    // Getters and Setters
    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getReleaseDate() { return releaseDate; }
    public void setReleaseDate(String releaseDate) { this.releaseDate = releaseDate; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public Instant getCachedAt() { return cachedAt; }
    public void setCachedAt(Instant cachedAt) { this.cachedAt = cachedAt; }
}
