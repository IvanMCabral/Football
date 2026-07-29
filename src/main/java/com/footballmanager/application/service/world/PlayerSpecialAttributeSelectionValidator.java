package com.footballmanager.application.service.world;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates the MVP 1 dataset rule for player special attributes.
 *
 * <p>The database enforces referential integrity, unique player/attribute
 * pairs and unique player/slot pairs. This validator enforces the importer
 * acceptance rule before any player rows are written: every final dataset
 * player must provide exactly two different catalog codes.
 */
public final class PlayerSpecialAttributeSelectionValidator {

    public ValidatedSelection validate(Collection<String> selectedCodes, Set<String> catalogCodes) {
        Objects.requireNonNull(selectedCodes, "selectedCodes cannot be null");
        Objects.requireNonNull(catalogCodes, "catalogCodes cannot be null");

        if (selectedCodes.size() != 2) {
            throw new IllegalArgumentException(
                "Each MVP 1 dataset player must have exactly two special attributes");
        }

        Set<String> normalizedCatalog = catalogCodes.stream()
            .map(PlayerSpecialAttributeSelectionValidator::normalize)
            .collect(Collectors.toUnmodifiableSet());

        LinkedHashSet<String> normalizedSelected = selectedCodes.stream()
            .map(PlayerSpecialAttributeSelectionValidator::normalize)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        if (normalizedSelected.size() != 2) {
            throw new IllegalArgumentException("Special attributes must be different");
        }

        for (String code : normalizedSelected) {
            if (code.isBlank()) {
                throw new IllegalArgumentException("Special attribute code cannot be blank");
            }
            if (!normalizedCatalog.contains(code)) {
                throw new IllegalArgumentException("Unknown special attribute code: " + code);
            }
        }

        return new ValidatedSelection(normalizedSelected.stream().toList());
    }

    private static String normalize(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Special attribute code cannot be null");
        }
        return code.trim().toLowerCase(Locale.ROOT);
    }

    public record ValidatedSelection(Collection<String> codes) {
        public ValidatedSelection {
            codes = List.copyOf(codes);
        }
    }
}
