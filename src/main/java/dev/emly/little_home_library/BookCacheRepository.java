package dev.emly.little_home_library;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookCacheRepository extends JpaRepository<BookCache, String> {
}
