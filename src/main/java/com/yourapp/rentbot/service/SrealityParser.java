package com.yourapp.rentbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourapp.rentbot.service.dto.ListingDto;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SrealityParser {

    private static final Pattern LAYOUT_PATTERN =
            Pattern.compile("(\\d+\\s*\\+\\s*(kk|\\d+))", Pattern.CASE_INSENSITIVE);

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public List<ListingDto> fetchListings(Integer srealityRegionId) throws IOException {
        try {
            List<ListingDto> result = new ArrayList<>();

            for (int page = 1; page <= 10; page++) {
                String apiUrl = buildApiUrl(srealityRegionId, page);

                System.out.println("SREALITY URL = " + apiUrl);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("User-Agent", "Mozilla/5.0")
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

                System.out.println("SREALITY STATUS = " + resp.statusCode());

                if (resp.statusCode() != 200) {
                    String body = resp.body() == null ? "" : resp.body();
                    System.out.println("SREALITY NON-200 RESPONSE, STOP PAGE = " + page);
                    System.out.println("SREALITY BODY = " + body.substring(0, Math.min(300, body.length())));
                    break;
                }

                String json = resp.body();
                JsonNode root = mapper.readTree(json);
                JsonNode estates = root.path("_embedded").path("estates");

                if (!estates.isArray()) {
                    System.out.println("SREALITY page " + page + " has no estates array");
                    break;
                }

                System.out.println("SREALITY page " + page + " parsed " + estates.size() + " listings");

                if (estates.isEmpty()) {
                    if (page == 1) {
                        System.out.println("SREALITY first page empty, regionId = " + srealityRegionId);
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

            return dedupeByLink(result);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching Sreality", e);
        }
    }

    private String buildApiUrl(Integer srealityRegionId, int page) {
        StringBuilder url = new StringBuilder("https://www.sreality.cz/api/cs/v2/estates")
                .append("?category_main_cb=1")
                .append("&category_type_cb=2")
                .append("&per_page=20")
                .append("&page=").append(page);

        if (srealityRegionId != null) {
            url.append("&locality_region_id=").append(srealityRegionId);
        }

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