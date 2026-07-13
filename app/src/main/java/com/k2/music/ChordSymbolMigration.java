package com.k2.music;

import java.util.ArrayList;
import java.util.List;

/** Pure migration helper for legacy favorites/history chord spellings. */
public final class ChordSymbolMigration {
    private ChordSymbolMigration() {
    }

    public static List<String> normalize(List<String> source, ChordRepository repository, int maxItems) {
        List<String> result = new ArrayList<>();
        if (source == null || repository == null) return result;
        for (String raw : source) {
            String value = raw == null ? "" : raw.trim();
            if (value.isEmpty()) continue;
            ChordRepository.LookupResult lookup = repository.find(value);
            String normalized = lookup.recognized ? lookup.chord.symbol : value;
            if (!result.contains(normalized)) result.add(normalized);
            if (maxItems > 0 && result.size() >= maxItems) break;
        }
        return result;
    }
}
