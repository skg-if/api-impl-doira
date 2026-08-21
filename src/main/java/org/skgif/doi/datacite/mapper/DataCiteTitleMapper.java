package org.skgif.doi.datacite.mapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.skgif.doi.datacite.dto.DataCiteAttributes;
import org.skgif.doi.datacite.dto.DataCiteDescription;
import org.skgif.doi.generated.model.ProductAllOfTopics;
import org.skgif.doi.generated.model.Topic;
import org.skgif.doi.util.MapperTextUtils;

/**
 * Maps a DataCite record's title/abstract/subject fields onto {@code Product.titles}/{@code
 * abstracts}/{@code topics}, and the equivalent concatenated-string {@code Grant.titles}/{@code
 * abstracts}. Split out of {@code DataCiteToSkgIfMapper} to keep that class down to
 * orchestration.
 */
final class DataCiteTitleMapper {

    private DataCiteTitleMapper() {
    }

    static Map<String, List<String>> titles(DataCiteAttributes attributes) {
        return titlesByLanguage(attributes);
    }

    static Map<String, List<String>> abstracts(DataCiteAttributes attributes) {
        List<String> values = abstractValues(attributes);
        return values.isEmpty() ? Map.of() : Map.of("en", values);
    }

    /**
     * Unlike {@code Product.titles}/{@code abstracts} (array of strings per language, for
     * multiple manifestations of the same product), {@code Grant.titles}/{@code abstracts} are a
     * plain string per language - so multiple DataCite {@code titles[]}/{@code
     * descriptions[type=Abstract]} entries sharing the same {@code lang} are concatenated into
     * one string per language.
     *
     * @param attributes the DataCite record to read titles from
     * @return the concatenated titles keyed by language, or an empty map if none carry a title
     */
    static Map<String, String> grantTitles(DataCiteAttributes attributes) {
        Map<String, String> joined = new LinkedHashMap<>();
        titlesByLanguage(attributes).forEach((lang, values) -> joined.put(lang, String.join(" ", values)));
        return joined;
    }

    static Map<String, String> grantAbstracts(DataCiteAttributes attributes) {
        List<String> values = abstractValues(attributes);
        return values.isEmpty() ? Map.of() : Map.of("en", String.join("\n\n", values));
    }

    /**
     * Groups DataCite {@code titles[]} by {@code lang} (defaulting null/blank to {@code "en"}).
     * DataCite's {@code lang} is free text, not restricted to ISO 639-1 two-letter codes - some
     * real records use 3-letter ISO 639-2 codes (e.g. {@code "eng"}) instead. Those are passed
     * through unnormalized rather than guessed at, since this codebase has no reliable ISO
     * 639-2-to-639-1 conversion table (see SKG_IF_DOI_MAPPING_LIMITATIONS.md).
     *
     * @param attributes the DataCite record to read titles from
     * @return the titles keyed by language, or an empty map if none carry a title
     */
    private static Map<String, List<String>> titlesByLanguage(DataCiteAttributes attributes) {
        return Optional.ofNullable(attributes.titles())
                .orElseGet(List::of)
                .stream()
                .filter(title -> title.title() != null)
                .collect(Collectors.groupingBy(
                        DataCiteTitleMapper::titleLanguage,
                        LinkedHashMap::new,
                        Collectors.mapping(DataCiteAttributes.Title::title, Collectors.toList())));
    }

    private static String titleLanguage(DataCiteAttributes.Title title) {
        String lang = title.lang();
        return (lang == null || lang.isBlank()) ? "en" : lang.strip().toLowerCase(Locale.ROOT);
    }

    private static List<String> abstractValues(DataCiteAttributes attributes) {
        return Optional.ofNullable(attributes.descriptions())
                .orElseGet(List::of)
                .stream()
                .filter(d -> "Abstract".equals(d.descriptionType()))
                .map(DataCiteDescription::description)
                .filter(Objects::nonNull)
                .toList();
    }

    static List<ProductAllOfTopics> topics(DataCiteAttributes attributes) {
        return Optional.ofNullable(attributes.subjects()).orElseGet(List::of).stream()
                .filter(subject -> subject.subject() != null)
                .map(subject -> {
                    String lang = subject.lang() != null ? subject.lang() : "none";
                    // DataCite subjects have no external identifier system behind them, so this
                    // is always an otf id - there's nothing more stable to hang it off.
                    return new ProductAllOfTopics().term(new Topic()
                            .localIdentifier(MapperTextUtils.otf(attributes.doi(), subject.subject()))
                            .entityType(Topic.EntityTypeEnum.TOPIC)
                            .labels(Map.of(lang, subject.subject())));
                })
                .toList();
    }
}
