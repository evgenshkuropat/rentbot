package com.yourapp.rentbot.service;

import com.yourapp.rentbot.service.dto.ListingDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class BezrealitkyParser {

    private static final String URL =
            "https://www.bezrealitky.cz/vypis/nabidka/pronajem/byt/praha";

    public List<ListingDto> fetchListings() throws IOException {

        List<ListingDto> result = new ArrayList<>();

        Document doc = Jsoup.connect(URL)
                .userAgent("Mozilla/5.0")
                .get();

        Elements listings = doc.select("article");

        for (Element e : listings) {
            String title = e.select("h2").text();

            String priceText = e.select(".price").text()
                    .replaceAll("[^0-9]", "");

            int price = 0;
            if (!priceText.isEmpty()) {
                price = Integer.parseInt(priceText);
            }

            String link = "https://www.bezrealitky.cz" + e.select("a").attr("href");
            String photo = e.select("img").attr("src");

            // Пока locality и layout можно не вытаскивать точно
            String locality = title;
            String layout = null;

            result.add(new ListingDto(
                    title,
                    price,
                    link,
                    layout,
                    locality,
                    photo
            ));
        }

        return result;
    }
}