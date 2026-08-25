package org.skgif.doi.util;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Text-only helpers shared by every provider's mapper ({@code CrossrefToSkgIfMapper}, {@code
 * DataCiteToSkgIfMapper}, {@code MedraToSkgIfMapper}) - byte-identical logic that was previously
 * duplicated once per mapper class. Kept to pure {@code String -> String} transforms only: each
 * mapper's own identifier-lookup helpers (e.g. {@code firstRor}) still live on that mapper since
 * they operate on a format-specific DTO type.
 */
public final class MapperTextUtils {

    /** Maximum length of a generated slug before truncation. */
    private static final int MAX_SLUG_LENGTH = 40;

    private MapperTextUtils() {
    }

    /**
     * An "on-the-fly" identifier per the SKG-IF Entity.local_identifier convention, for entities
     * with no stable identifier of their own - built from the owning record's DOI so it's
     * deterministic.
     *
     * @param doi   the owning record's DOI
     * @param label a human-readable label for the entity (e.g. a name), slugged into the id
     * @return an {@code otf___<doi-slug>___<label-slug>} identifier
     */
    public static String otf(@Nullable String doi, @Nullable String label) {
        return "otf___" + slug(doi) + "___" + slug(label);
    }

    /**
     * Reduces arbitrary text to a slug safe for use inside an on-the-fly identifier.
     *
     * @param text arbitrary text, or null
     * @return a lowercase, hyphenated, length-capped slug of text; "unknown" if text is null,
     *         empty, or has no alphanumeric characters
     */
    public static String slug(@Nullable String text) {
        if (text == null) {
            return "unknown";
        }
        String slug = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+)|(-+$)", "");
        if (slug.isEmpty()) {
            return "unknown";
        }
        return slug.length() > MAX_SLUG_LENGTH ? slug.substring(0, MAX_SLUG_LENGTH) : slug;
    }
}
