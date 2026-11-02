package dev.emly.little_home_library;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "rentals")
public class Rental {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String userCard;
    private Instant checkoutTime;

    // --- REFACTORED: Remove the duplicate String isbn field and let the relationship own the column natively ---
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "isbn", referencedColumnName = "isbn", nullable = false)
    private BookCache cachedItem;

    // Helper method to keep your existing service code (.getIsbn()) working cleanly without breaking types
    public String getIsbn() {
        return cachedItem != null ? cachedItem.getIsbn() : null;
    }

    // Accessors
    public Long getId() { return id; }
    public String getUserCard() { return userCard; }
    public void setUserCard(String userCard) { this.userCard = userCard; }
    public Instant getCheckoutTime() { return checkoutTime; }
    public void setCheckoutTime(Instant checkoutTime) { this.checkoutTime = checkoutTime; }
    public BookCache getCachedItem() { return cachedItem; }
    public void setCachedItem(BookCache cachedItem) { this.cachedItem = cachedItem; }
}
