package com.yourapp.rentbot.service.dto;

public record ParserRunStats(
        int srealityRaw,
        int idnesRaw,
        int bezrealitkyRaw,
        int bazosRaw,

        int afterDedupeByLink,
        int afterDedupeBySignature,

        int filteredBaseTotal,
        int filteredBaseSreality,
        int filteredBaseIdnes,
        int filteredBaseBezrealitky,
        int filteredBaseBazos,

        int finalFiltered,
        int finalSreality,
        int finalIdnes,
        int finalBezrealitky,
        int finalBazos
) {
}