package com.yourapp.rentbot.service.dto;

/** Raw counts describe one fetch; filter counts describe one user's search. */
public record ParserRunStats(
        int srealityRaw, int idnesRaw, int bezrealitkyRaw, int bazosRaw, int digirealityRaw,
        int afterDedupeByLink, int afterDedupeBySignature, int realfreeRaw,
        ListingSourceCounts filteredBaseSources, ListingSourceCounts finalSources
) {
    public static ParserRunStats empty() {
        return new ParserRunStats(0, 0, 0, 0, 0, 0, 0, 0, ListingSourceCounts.empty(), ListingSourceCounts.empty());
    }

    public ParserRunStats withFilterCounts(ListingSourceCounts base, ListingSourceCounts result) {
        return new ParserRunStats(srealityRaw, idnesRaw, bezrealitkyRaw, bazosRaw, digirealityRaw,
                afterDedupeByLink, afterDedupeBySignature, realfreeRaw, base, result);
    }
}
