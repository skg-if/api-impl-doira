package org.skgif.doi.crossref.mapper;

import static java.util.function.Predicate.not;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.generated.model.ProductAllOfTopics;
import org.skgif.doi.generated.model.Topic;
import org.skgif.doi.util.MapperTextUtils;

/**
 * Maps a Crossref work record's title/abstract/subject fields onto {@code Product.titles}/{@code
 * abstracts}/{@code topics}. Split out of {@code CrossrefToSkgIfMapper} to keep that class down
 * to orchestration.
 */
final class CrossrefTitleMapper {

    private CrossrefTitleMapper() {
    }

    static Map<String, List<String>> titles(CrossrefWork work) {
        List<String> values = Optional.ofNullable(work.title())
                .orElseGet(List::of)
                .stream()
                .filter(t -> t != null && !t.isBlank())
                .toList();

        return values.isEmpty() ? Map.of() : Map.of("en", values);
    }

    /**
     * Crossref's {@code abstract} is a single JATS-XML-tagged string (e.g. {@code
     * <jats:p>...</jats:p>}), not plain text like DataCite's - this strips the tags
     * rather than attempting to preserve any structure, since SKG-IF's {@code abstracts} field is
     * plain text.
     *
     * @param work the Crossref work record to read the abstract from
     * @return the abstract, plain-text and tag-stripped, keyed by "en"; empty map if absent/empty
     */
    static Map<String, List<String>> abstracts(CrossrefWork work) {
        return Optional.ofNullable(work.abstractText())
                .map(text -> text.replaceAll("<[^>]+>", "").trim())
                .filter(not(String::isEmpty))
                .map(CrossrefTitleMapper::toSingleEnglishValue)
                .orElseGet(Map::of);
    }

    // Extracted to a named method (rather than a lambda) with an explicit String parameter -
    // PMD 7.7.0's ConfusingArgumentToVarargsMethod check can't resolve the inferred type of a
    // lambda parameter passed straight into List.of/Map.of, and flags a false positive.
    private static Map<String, List<String>> toSingleEnglishValue(String value) {
        return Map.of("en", List.of(value));
    }

    static List<ProductAllOfTopics> topics(CrossrefWork work) {
        return Optional.ofNullable(work.subject()).orElseGet(List::of).stream()
                .filter(Objects::nonNull)
                // Crossref subjects (Sci-Val controlled vocabulary) have no external identifier
                // system behind them, so this is always an otf id - same as DataCite subjects.
                .map(subject -> new ProductAllOfTopics()
                        .term(new Topic()
                                .localIdentifier(MapperTextUtils.otf(work.doi(), subject))
                                .entityType(Topic.EntityTypeEnum.TOPIC)
                                .labels(Map.of("en", subject))))
                .toList();
    }
}
