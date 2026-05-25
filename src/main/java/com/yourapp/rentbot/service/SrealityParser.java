package com.yourapp.rentbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourapp.rentbot.service.dto.ListingDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SrealityParser {

    private static final Logger log = LoggerFactory.getLogger(SrealityParser.class);

    private static final Pattern LAYOUT_PATTERN =
            Pattern.compile("(\\d+\\s*\\+\\s*(kk|\\d+))", Pattern.CASE_INSENSITIVE);

    private static final int MAX_PAGES = 30;
    private static final int PER_PAGE = 20;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    public static Integer getDistrictId(String regionCode) {
        if (regionCode == null || regionCode.isBlank()) {
            return null;
        }

        return SREALITY_DISTRICT_IDS.get(regionCode.toUpperCase());
    }

    public static Integer getRegionId(String regionCode) {
        if (regionCode == null || regionCode.isBlank()) {
            return null;
        }

        return SREALITY_REGION_IDS.get(regionCode.toUpperCase());
    }

    public List<ListingDto> fetchListings(Integer srealityDistrictId) throws IOException {
        return fetchListings(null, srealityDistrictId);
    }

    public List<ListingDto> fetchListings(String regionCode, Integer srealityDistrictId) throws IOException {
        String runId = UUID.randomUUID().toString().substring(0, 8);

        if (srealityDistrictId == null) {
            log.info("Sreality run={} skipped: srealityDistrictId is null", runId);
            return List.of();
        }

        try {
            List<ListingDto> result = new ArrayList<>();

            Integer srealityRegionId = getRegionId(regionCode);

            log.info("Sreality run={} started, regionCode={}, districtId={}, regionId={}",
                    runId, regionCode, srealityDistrictId, srealityRegionId);

            for (int page = 1; page <= MAX_PAGES; page++) {
                String apiUrl = buildApiUrl(srealityDistrictId, srealityRegionId, page);

                log.debug("Sreality run={} page={} url={}", runId, page, apiUrl);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .timeout(Duration.ofSeconds(20))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                        .header("Accept", "application/json, text/plain, */*")
                        .header("Accept-Language", "cs-CZ,cs;q=0.9,en;q=0.8")
                        .header("Referer", "https://www.sreality.cz/")
                        .GET()
                        .build();

                HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());

                if (resp.statusCode() != 200) {
                    String body = resp.body() == null ? "" : new String(resp.body(), StandardCharsets.UTF_8);

                    log.warn(
                            "Sreality run={} page={} non-200 status={}, body={}",
                            runId,
                            page,
                            resp.statusCode(),
                            body.substring(0, Math.min(300, body.length()))
                    );

                    break;
                }

                String json = new String(resp.body(), StandardCharsets.UTF_8);
                JsonNode root = mapper.readTree(json);
                JsonNode estates = root.path("_embedded").path("estates");

                if (!estates.isArray()) {
                    log.warn("Sreality run={} page={} has no estates array", runId, page);
                    break;
                }

                log.info("Sreality run={} page={} parsed={} listings", runId, page, estates.size());

                if (estates.isEmpty()) {
                    if (page == 1) {
                        log.warn("Sreality run={} first page empty, districtId={}", runId, srealityDistrictId);
                    }
                    break;
                }

                for (JsonNode estate : estates) {
                    String title = estate.path("name").asText("");
                    int priceCzk = extractPrice(estate);
                    String link = buildLink(estate);
                    String layout = extractLayout(title);
                    String photoUrl = extractPhotoUrl(estate);
                    String locality = estate.path("locality").asText("");

                    if (link == null || link.isBlank() || link.equals("https://www.sreality.cz")) {
                        continue;
                    }

                    result.add(new ListingDto(
                            title,
                            priceCzk,
                            link,
                            layout,
                            locality,
                            photoUrl,
                            "Sreality",
                            LocalDateTime.now()
                    ));
                }

                if (estates.size() < PER_PAGE) {
                    break;
                }
            }

            List<ListingDto> deduped = dedupeByLink(result);

            log.info("Sreality run={} finished, raw={}, deduped={}", runId, result.size(), deduped.size());

            return deduped;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching Sreality", e);
        }
    }

    private String buildApiUrl(Integer srealityDistrictId, Integer srealityRegionId, int page) {
        long tms = System.currentTimeMillis();

        StringBuilder url = new StringBuilder("https://www.sreality.cz/api/cs/v2/estates")
                .append("?category_main_cb=1")
                .append("&category_type_cb=2")
                .append("&locality_country_id=10001")
                .append("&no_auction=1")
                .append("&page=").append(page)
                .append("&per_page=").append(PER_PAGE);

        if (srealityRegionId != null) {
            url.append("&locality_region_id=").append(srealityRegionId);
        } else {
            url.append("&locality_district_id=").append(srealityDistrictId);
        }

        return url.append("&tms=").append(tms)
                .toString();
    }

    private int extractPrice(JsonNode estate) {
        JsonNode priceCzkNode = estate.path("price_czk");

        if (priceCzkNode.isInt() || priceCzkNode.isLong()) {
            return priceCzkNode.asInt();
        }

        JsonNode valueRaw = priceCzkNode.path("value_raw");
        if (valueRaw.isInt() || valueRaw.isLong()) {
            return valueRaw.asInt();
        }

        JsonNode price = estate.path("price");
        if (price.isInt() || price.isLong()) {
            return price.asInt();
        }

        return 0;
    }

    private List<ListingDto> dedupeByLink(List<ListingDto> input) {
        Map<String, ListingDto> map = new LinkedHashMap<>();

        for (ListingDto dto : input) {
            if (dto == null || dto.link() == null || dto.link().isBlank()) {
                continue;
            }

            map.putIfAbsent(dto.link(), dto);
        }

        return new ArrayList<>(map.values());
    }

    private String buildLink(JsonNode estate) {
        String hash = estate.path("hash_id").asText("");
        String locality = estate.path("seo").path("locality").asText("");
        String categorySubCb = estate.path("seo").path("category_sub_cb").asText("");

        if (hash.isBlank()) {
            return null;
        }

        if (!categorySubCb.isBlank() && !locality.isBlank()) {
            return "https://www.sreality.cz/detail/pronajem/byt/"
                    + categorySubCb + "/"
                    + locality + "/"
                    + hash;
        }

        if (!locality.isBlank()) {
            return "https://www.sreality.cz/detail/pronajem/byt/"
                    + locality + "/"
                    + hash;
        }

        return "https://www.sreality.cz/detail/pronajem/byt/" + hash;
    }

    private String extractLayout(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }

        String lower = title.toLowerCase();

        if (lower.contains("spolubydlení")
                || lower.contains("spolubydleni")
                || lower.contains("samostatný pokoj")
                || lower.contains("samostatny pokoj")
                || lower.contains("pronájem pokoje")
                || lower.contains("pronajem pokoje")
                || lower.contains("pokoj k pronájmu")
                || lower.contains("pokoj k pronajmu")
                || lower.matches(".*\\bpokoj\\b.*")) {
            return "ROOM";
        }

        Matcher m = LAYOUT_PATTERN.matcher(title);
        if (!m.find()) {
            return null;
        }

        return m.group(1).toLowerCase().replaceAll("\\s+", "");
    }

    private String extractPhotoUrl(JsonNode estate) {
        JsonNode links = estate.path("_links");

        if (links.isObject()) {
            JsonNode images = links.path("images");

            if (images.isArray() && !images.isEmpty()) {
                String href = images.get(0).path("href").asText("");

                if (!href.isBlank()) {
                    return href;
                }
            }
        }

        JsonNode embedded = estate.path("_embedded");

        if (embedded.isObject()) {
            JsonNode images = embedded.path("images");

            if (images.isArray() && !images.isEmpty()) {
                String href = images.get(0).path("href").asText("");

                if (!href.isBlank()) {
                    return href;
                }
            }
        }

        return null;
    }

    private static final Map<String, Integer> SREALITY_REGION_IDS = Map.ofEntries(
            Map.entry("BENESOV", 11),
            Map.entry("BEROUN", 11),
            Map.entry("BLANSKO", 1),
            Map.entry("BRNO", 1),
            Map.entry("BRNO_VENKOV", 1),
            Map.entry("BRUNTAL", 7),
            Map.entry("BRECLAV", 1),
            Map.entry("CESKA_LIPA", 4),
            Map.entry("CESKE_BUDEJOVICE", 13),
            Map.entry("CESKY_KRUMLOV", 13),
            Map.entry("DECIN", 9),
            Map.entry("DOMAZLICE", 5),
            Map.entry("FRYDEK_MISTEK", 7),
            Map.entry("HAVLICKUV_BROD", 14),
            Map.entry("HODONIN", 1),
            Map.entry("HRADEC_KRALOVE", 3),
            Map.entry("CHEB", 2),
            Map.entry("CHOMUTOV", 9),
            Map.entry("CHRUDIM", 6),
            Map.entry("JABLONEC_NAD_NISOU", 4),
            Map.entry("JABLONEC", 4),
            Map.entry("JESENIK", 12),
            Map.entry("JICIN", 3),
            Map.entry("JIHLAVA", 14),
            Map.entry("JINDRICHUV_HRADEC", 13),
            Map.entry("KARLOVY_VARY", 2),
            Map.entry("KARVINA", 7),
            Map.entry("KLADNO", 11),
            Map.entry("KLATOVY", 5),
            Map.entry("KOLIN", 11),
            Map.entry("KROMERIZ", 15),
            Map.entry("KUTNA_HORA", 11),
            Map.entry("LIBEREC", 4),
            Map.entry("LITOMERICE", 9),
            Map.entry("LOUNY", 9),
            Map.entry("MELNIK", 11),
            Map.entry("MLADA_BOLESLAV", 11),
            Map.entry("MOST", 9),
            Map.entry("NACHOD", 3),
            Map.entry("NOVY_JICIN", 7),
            Map.entry("NYMBURK", 11),
            Map.entry("OLOMOUC", 12),
            Map.entry("OPAVA", 7),
            Map.entry("OSTRAVA", 7),
            Map.entry("PARDUBICE", 6),
            Map.entry("PELHRIMOV", 14),
            Map.entry("PISEK", 13),
            Map.entry("PLZEN", 5),
            Map.entry("PLZEN_JIH", 5),
            Map.entry("PLZEN_SEVER", 5),
            Map.entry("PRAHA", 10),
            Map.entry("PRAHA_1", 10),
            Map.entry("PRAHA_2", 10),
            Map.entry("PRAHA_3", 10),
            Map.entry("PRAHA_4", 10),
            Map.entry("PRAHA_5", 10),
            Map.entry("PRAHA_6", 10),
            Map.entry("PRAHA_7", 10),
            Map.entry("PRAHA_8", 10),
            Map.entry("PRAHA_9", 10),
            Map.entry("PRAHA_10", 10),
            Map.entry("PRAHA_VYCHOD", 11),
            Map.entry("PRAHA_ZAPAD", 11),
            Map.entry("PRACHATICE", 13),
            Map.entry("PROSTEJOV", 12),
            Map.entry("PREROV", 12),
            Map.entry("PRIBRAM", 11),
            Map.entry("RAKOVNIK", 11),
            Map.entry("ROKYCANY", 5),
            Map.entry("RYCHNOV_NAD_KNEZNOU", 3),
            Map.entry("SEMILY", 4),
            Map.entry("SOKOLOV", 2),
            Map.entry("STRAKONICE", 13),
            Map.entry("SVITAVY", 6),
            Map.entry("SUMPERK", 12),
            Map.entry("TABOR", 13),
            Map.entry("TACHOV", 5),
            Map.entry("TEPLICE", 9),
            Map.entry("TRUTNOV", 3),
            Map.entry("TREBIC", 14),
            Map.entry("UHERSKE_HRADISTE", 15),
            Map.entry("USTI_NAD_LABEM", 9),
            Map.entry("USTI_NAD_ORLICI", 6),
            Map.entry("VSETIN", 15),
            Map.entry("VYSKOV", 1),
            Map.entry("ZLIN", 15),
            Map.entry("ZNOJMO", 1),
            Map.entry("ZDAR_NAD_SAZAVOU", 14)
    );

    private static final Map<String, Integer> SREALITY_DISTRICT_IDS = Map.ofEntries(
            Map.entry("BENESOV", 48),
            Map.entry("BEROUN", 49),
            Map.entry("BLANSKO", 71),
            Map.entry("BRNO", 72),
            Map.entry("BRNO_VENKOV", 73),
            Map.entry("BRUNTAL", 60),
            Map.entry("BRECLAV", 74),
            Map.entry("CESKA_LIPA", 18),
            Map.entry("CESKE_BUDEJOVICE", 1),
            Map.entry("CESKY_KRUMLOV", 2),
            Map.entry("DECIN", 19),
            Map.entry("DOMAZLICE", 8),
            Map.entry("FRYDEK_MISTEK", 61),
            Map.entry("HAVLICKUV_BROD", 66),
            Map.entry("HODONIN", 75),
            Map.entry("HRADEC_KRALOVE", 28),
            Map.entry("CHEB", 9),
            Map.entry("CHOMUTOV", 20),
            Map.entry("CHRUDIM", 29),
            Map.entry("JABLONEC_NAD_NISOU", 21),
            Map.entry("JABLONEC", 21),
            Map.entry("JESENIK", 46),
            Map.entry("JICIN", 30),
            Map.entry("JIHLAVA", 67),
            Map.entry("JINDRICHUV_HRADEC", 3),
            Map.entry("KARLOVY_VARY", 10),
            Map.entry("KARVINA", 62),
            Map.entry("KLADNO", 50),
            Map.entry("KLATOVY", 11),
            Map.entry("KOLIN", 51),
            Map.entry("KROMERIZ", 39),
            Map.entry("KUTNA_HORA", 52),
            Map.entry("LIBEREC", 22),
            Map.entry("LITOMERICE", 23),
            Map.entry("LOUNY", 24),
            Map.entry("MELNIK", 54),
            Map.entry("MLADA_BOLESLAV", 53),
            Map.entry("MOST", 25),
            Map.entry("NACHOD", 31),
            Map.entry("NOVY_JICIN", 63),
            Map.entry("NYMBURK", 55),
            Map.entry("OLOMOUC", 42),
            Map.entry("OPAVA", 64),
            Map.entry("OSTRAVA", 65),
            Map.entry("PARDUBICE", 32),
            Map.entry("PELHRIMOV", 68),
            Map.entry("PISEK", 4),
            Map.entry("PLZEN", 12),
            Map.entry("PLZEN_JIH", 13),
            Map.entry("PLZEN_SEVER", 14),
            Map.entry("PRAHA", 10),
            Map.entry("PRAHA_1", 5001),
            Map.entry("PRAHA_2", 5002),
            Map.entry("PRAHA_3", 5003),
            Map.entry("PRAHA_4", 5004),
            Map.entry("PRAHA_5", 5005),
            Map.entry("PRAHA_6", 5006),
            Map.entry("PRAHA_7", 5007),
            Map.entry("PRAHA_8", 5008),
            Map.entry("PRAHA_9", 5009),
            Map.entry("PRAHA_10", 5010),
            Map.entry("PRAHA_VYCHOD", 56),
            Map.entry("PRAHA_ZAPAD", 57),
            Map.entry("PRACHATICE", 5),
            Map.entry("PROSTEJOV", 40),
            Map.entry("PREROV", 43),
            Map.entry("PRIBRAM", 58),
            Map.entry("RAKOVNIK", 59),
            Map.entry("ROKYCANY", 15),
            Map.entry("RYCHNOV_NAD_KNEZNOU", 33),
            Map.entry("SEMILY", 34),
            Map.entry("SOKOLOV", 16),
            Map.entry("STRAKONICE", 6),
            Map.entry("SVITAVY", 35),
            Map.entry("SUMPERK", 44),
            Map.entry("TABOR", 7),
            Map.entry("TACHOV", 17),
            Map.entry("TEPLICE", 26),
            Map.entry("TRUTNOV", 36),
            Map.entry("TREBIC", 69),
            Map.entry("UHERSKE_HRADISTE", 41),
            Map.entry("USTI_NAD_LABEM", 27),
            Map.entry("USTI_NAD_ORLICI", 37),
            Map.entry("VSETIN", 45),
            Map.entry("VYSKOV", 76),
            Map.entry("ZLIN", 38),
            Map.entry("ZNOJMO", 77),
            Map.entry("ZDAR_NAD_SAZAVOU", 70)
    );
}
