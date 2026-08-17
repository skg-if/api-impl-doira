package org.skgif.doi.datacite.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.skgif.doi.datacite.dto.DataCiteAttributes;
import org.skgif.doi.datacite.dto.DataCiteSubject;
import org.skgif.doi.generated.model.ProductAllOfTerm;
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
        List<String> values = titleValues(attributes);
        return values.isEmpty() ? null : Map.of("en", values);
    }

    static Map<String, List<String>> abstracts(DataCiteAttributes attributes) {
        List<String> values = abstractValues(attributes);
        return values.isEmpty() ? null : Map.of("en", values);
    }

    /**
     * Unlike {@code Product.titles}/{@code abstracts} (array of strings per language, for
     * multiple manifestations of the same product), {@code Grant.titles}/{@code abstracts} are a
     * plain string per language - so multiple DataCite {@code titles[]}/{@code
     * descriptions[type=Abstract]} entries are concatenated into one string.
     *
     * @param attributes the DataCite record to read titles from
     * @return the concatenated titles keyed by "en", or null if none carry a title
     */
    static Map<String, String> grantTitles(DataCiteAttributes attributes) {
        List<String> values = titleValues(attributes);
        return values.isEmpty() ? null : Map.of("en", String.join(" ", values));
    }

    static Map<String, String> grantAbstracts(DataCiteAttributes attributes) {
        List<String> values = abstractValues(attributes);
        return values.isEmpty() ? null : Map.of("en", String.join("\n\n", values));
    }

    private static List<String> titleValues(DataCiteAttributes attributes) {
        if (attributes.titles == null || attributes.titles.isEmpty()) {
            return List.of();
        }
        return attributes.titles.stream().map(t -> t.title).filter(Objects::nonNull).toList();
    }

    private static List<String> abstractValues(DataCiteAttributes attributes) {
        if (attributes.descriptions == null) {
            return List.of();
        }
        return attributes.descriptions.stream()
                .filter(d -> "Abstract".equals(d.descriptionType))
                .map(d -> d.description)
                .filter(Objects::nonNull)
                .toList();
    }

    static List<ProductAllOfTopics> topics(DataCiteAttributes attributes) {
        if (attributes.subjects == null || attributes.subjects.isEmpty()) {
            return null;
        }
        List<ProductAllOfTopics> topics = new ArrayList<>();
        for (DataCiteSubject subject : attributes.subjects) {
            if (subject.subject == null) {
                continue;
            }
            String lang = subject.lang != null ? subject.lang : "none";
            // DataCite subjects have no external identifier system behind them, so this is
            // always an otf id - there's nothing more stable to hang it off.
            ProductAllOfTerm term = new Topic()
                    .localIdentifier(MapperTextUtils.otf(attributes.doi, subject.subject))
                    .entityType(Topic.EntityTypeEnum.TOPIC)
                    .labels(Map.of(lang, subject.subject));
            topics.add(new ProductAllOfTopics().term(term));
        }
        return topics.isEmpty() ? null : topics;
    }
}
