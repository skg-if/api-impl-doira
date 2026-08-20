package org.skgif.doi.medra.mapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.skgif.doi.medra.dto.MedraTitle;
import org.skgif.doi.medra.dto.MedraWork;

/**
 * Maps a mEDRA ONIX-for-DOI record's title/abstract fields onto {@code Product.titles}/{@code
 * abstracts}. Split out of {@code MedraToSkgIfMapper} to keep that class down to orchestration.
 */
final class MedraTitleMapper {

    private MedraTitleMapper() {
    }

    /**
     * Groups {@code ContentItem}-level titles by their {@code language} attribute (defaulting to
     * {@code "en"} when absent, same convention as {@code CrossrefToSkgIfMapper.titles}), keeping
     * every {@code TitleType} for that language in document order - mEDRA gives no field to
     * distinguish "full" vs. "abbreviated" title once inside {@code Product.titles} anyway.
     *
     * @param work the mEDRA record to read ContentItem-level titles from
     * @return the titles grouped by language, or an empty map if work has none
     */
    static Map<String, List<String>> titles(MedraWork work) {
        return Optional.ofNullable(work.titles())
                .orElseGet(List::of)
                .stream()
                .collect(Collectors.groupingBy(
                        title -> title.language() != null ? title.language() : "en",
                        LinkedHashMap::new,
                        Collectors.mapping(MedraTitle::text, Collectors.toList())));
    }

    static Map<String, List<String>> abstracts(MedraWork work) {
        return Optional.ofNullable(work.abstractText())
                .map(text -> Map.of("en", List.of(text)))
                .orElseGet(Map::of);
    }
}
