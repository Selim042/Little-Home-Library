package dev.emly.little_home_library.sources;

import java.util.Optional;

import dev.emly.little_home_library.ItemDetails;

public interface ItemLookupStrategy {
    Optional<ItemDetails> lookup(String barcode);
    boolean supports(String barcode);
}
