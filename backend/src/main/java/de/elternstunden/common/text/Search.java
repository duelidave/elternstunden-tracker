package de.elternstunden.common.text;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Search in admin lists: case and diacritics do not matter (same normalization as 7.1.3), at least 2 characters. */
public final class Search {

    private final @Nullable String needle;

    private Search(@Nullable String needle) {
        this.needle = needle;
    }

    /** A query shorter than 2 characters (after trimming) means "no filter". */
    public static Search of(@Nullable String query) {
        if (query == null || query.strip().length() < 2) {
            return new Search(null);
        }
        return new Search(NameNormalizer.normalize(query));
    }

    /** False if the query was too short: then everything matches. */
    public boolean isActive() {
        return needle != null;
    }

    public boolean matches(@Nullable String... values) {
        return matches(Arrays.asList(values));
    }

    public boolean matches(Collection<? extends @Nullable String> values) {
        String wanted = needle;
        if (wanted == null) {
            return true;
        }
        return values.stream()
                .filter(Objects::nonNull)
                .anyMatch(value -> NameNormalizer.normalize(value).contains(wanted));
    }
}
