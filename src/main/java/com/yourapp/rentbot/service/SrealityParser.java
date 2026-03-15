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
import java.util.List;
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
            int regionId = (srealityRegionId == null) ? 10 : srealityRegionId;

            List<ListingDto> result = new ArrayList<>();

            for (int page = 1; page <= 10; page++) {
                String apiUrl =
                        "https://www.sreality.cz/api/cs/v2/estates" +
                                "?category_main_cb=1" +
                                "&category_type_cb=2" +
                                "&locality_region_id=" + regionId +
                                "&per_page=20" +
                                "&page=" + page;

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("User-Agent", "Mozilla/5.0")
                        .GET()
                        .build();

                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                String json = resp.body();

                JsonNode root = mapper.readTree(json);
                JsonNode estates = root.path("_embedded").path("estates");

                if (!estates.isArray() || estates.isEmpty()) {
                    break;
                }

                for (JsonNode estate : estates) {
                    String title = estate.path("name").asText("");
                    int priceCzk = estate.path("price_czk").path("value_raw").asInt();
                    String link = buildLink(estate);
                    String layout = extractLayout(title);
                    String photoUrl = extractPhotoUrl(estate);
                    String locality = estate.path("locality").asText("");

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

            return result.stream()
                    .filter(x -> x.link() != null && !x.link().isBlank())
                    .collect(java.util.stream.Collectors.toMap(
                            ListingDto::link,
                            x -> x,
                            (a, b) -> a
                    ))
                    .values()
                    .stream()
                    .toList();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching sreality", e);
        }
    }

    private String buildLink(JsonNode estate) {
        String hash = estate.path("hash_id").asText();
        String locality = estate.path("seo").path("locality").asText();

        String title = estate.path("name").asText("");
        String layout = extractLayout(title);

        if (hash == null || hash.isBlank()) {
            return "https://www.sreality.cz";
        }

        if (locality == null || locality.isBlank()) {
            return "https://www.sreality.cz/detail/pronajem/byt/" + hash;
        }

        if (layout == null || layout.isBlank()) {
            return "https://www.sreality.cz/detail/pronajem/byt/" + locality + "/" + hash;
        }

        return "https://www.sreality.cz/detail/pronajem/byt/"
                + layout + "/"
                + locality + "/"
                + hash;
    }

    private String extractLayout(String title) {
        if (title == null) {
            return null;
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
                String href = first.path("href").asText();
                if (href != null && !href.isBlank()) {
                    return href;
                }
            }
        }

        JsonNode embedded = estate.path("_embedded");
        if (embedded.isObject()) {
            JsonNode images = embedded.path("images");

            if (images.isArray() && !images.isEmpty()) {
                JsonNode first = images.get(0);
                String href = first.path("href").asText();
                if (href != null && !href.isBlank()) {
                    return href;
                }
            }
        }

        return null;
    }
}