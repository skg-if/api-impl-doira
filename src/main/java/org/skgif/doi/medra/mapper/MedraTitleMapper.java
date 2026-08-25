package org.skgif.doi.medra.mapper;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        List<MedraTitle> titles = work.titles();
        if (titles == null) {
            return Map.of();
        }
        return titles.stream()
                .collect(groupingBy(
                        title -> title.language() != null ? title.language() : "en",
                        LinkedHashMap::new,
                        mapping(MedraTitle::text, toList())));
    }

    static Map<String, List<String>> abstracts(MedraWork work) {
        return Optional.ofNullable(work.abstractText())
                .map(MedraTitleMapper::toSingleEnglishValue)
                .orElseGet(Map::of);
    }

    // Extracted to a named method (rather than a lambda) with an explicit String parameter -
    // PMD 7.7.0's ConfusingArgumentToVarargsMethod check can't resolve the inferred type of a
    // lambda parameter passed straight into List.of/Map.of, and flags a false positive.
    private static Map<String, List<String>> toSingleEnglishValue(String value) {
        return Map.of("en", List.of(value));
    }
}
