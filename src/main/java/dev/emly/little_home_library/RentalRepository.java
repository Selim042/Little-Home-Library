package dev.emly.little_home_library;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RentalRepository extends JpaRepository<Rental, Long> {
    List<Rental> findByUserCard(String userCard);
    
    Optional<Rental> findByUserCardAndCachedItemIsbn(String userCard, String isbn);
}
