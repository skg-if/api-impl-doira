package org.skgif.doi.crossref.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.skgif.doi.crossref.dto.CrossrefWork;
import org.skgif.doi.generated.model.ProductAllOfTerm;
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
        if (work.title() == null || work.title().isEmpty()) {
            return null;
        }
        List<String> values = work.title().stream().filter(Objects::nonNull).toList();
        return values.isEmpty() ? null : Map.of("en", values);
    }

    /**
     * Crossref's {@code abstract} is a single JATS-XML-tagged string (e.g. {@code
     * &lt;jats:p&gt;...&lt;/jats:p&gt;}), not plain text like DataCite's - this strips the tags
     * rather than attempting to preserve any structure, since SKG-IF's {@code abstracts} field is
     * plain text.
     *
     * @param work the Crossref work record to read the abstract from
     * @return the abstract, plain-text and tag-stripped, keyed by "en"; null if absent/empty
     */
    static Map<String, List<String>> abstracts(CrossrefWork work) {
        if (work.abstractText() == null) {
            return null;
        }
        String stripped = work.abstractText().replaceAll("<[^>]+>", "").trim();
        return stripped.isEmpty() ? null : Map.of("en", List.of(stripped));
    }

    static List<ProductAllOfTopics> topics(CrossrefWork work) {
        if (work.subject() == null || work.subject().isEmpty()) {
            return null;
        }
        List<ProductAllOfTopics> topics = new ArrayList<>();
        for (String subject : work.subject()) {
            if (subject == null) {
                continue;
            }
            // Crossref subjects (Sci-Val controlled vocabulary) have no external identifier
            // system behind them, so this is always an otf id - same as DataCite subjects.
            ProductAllOfTerm term = new Topic()
                    .localIdentifier(MapperTextUtils.otf(work.doi(), subject))
                    .entityType(Topic.EntityTypeEnum.TOPIC)
                    .labels(Map.of("en", subject));
            topics.add(new ProductAllOfTopics().term(term));
        }
        return topics.isEmpty() ? null : topics;
    }
}
