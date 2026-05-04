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

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    public List<ListingDto> fetchListings(Integer srealityRegionId) throws IOException {
        String runId = UUID.randomUUID().toString().substring(0, 8);

        try {
            List<ListingDto> result = new ArrayList<>();

            log.info("Sreality run={} started, regionId={}", runId, srealityRegionId);

            for (int page = 1; page <= 10; page++) {
                String apiUrl = buildApiUrl(srealityRegionId, page);

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

                int status = resp.statusCode();

                if (status != 200) {
                    String body = resp.body() == null ? "" : new String(resp.body(), StandardCharsets.UTF_8);

                    log.warn(
                            "Sreality run={} page={} non-200 status={}, body={}",
                            runId,
                            page,
                            status,
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
                        log.warn("Sreality run={} first page empty, regionId={}", runId, srealityRegionId);
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
                            "Sreality"
                    ));
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

    private String buildApiUrl(Integer srealityRegionId, int page) {
        long tms = System.currentTimeMillis();

        StringBuilder url = new StringBuilder("https://www.sreality.cz/api/cs/v2/estates")
                .append("?category_main_cb=1")
                .append("&category_type_cb=2")
                .append("&locality_region_id=").append(srealityRegionId == null ? 10 : srealityRegionId)
                .append("&page=").append(page)
                .append("&per_page=20")
                .append("&tms=").append(tms);

        return url.toString();
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
                JsonNode first = images.get(0);
                String href = first.path("href").asText("");

                if (!href.isBlank()) {
                    return href;
                }
            }
        }

        JsonNode embedded = estate.path("_embedded");

        if (embedded.isObject()) {
            JsonNode images = embedded.path("images");

            if (images.isArray() && !images.isEmpty()) {
                JsonNode first = images.get(0);
                String href = first.path("href").asText("");

                if (!href.isBlank()) {
                    return href;
                }
            }
        }

        return null;
    }
}