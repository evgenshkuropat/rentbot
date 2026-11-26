package com.yourapp.rentbot.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Shared city-only scope; a source's district search is not proof of city membership. */
final class CitySearchScope {
    private CitySearchScope() { }

    static boolean matches(String locality, String city) {
        if (city == null || city.isBlank()) return true;
        if (locality == null || locality.isBlank()) return false;
        String expected = normalize(city).replace('_', ' ');
        String place = normalize(locality).replaceFirst("^hlavni mesto\\s+", "");
        // A district name does not establish that a listing belongs to its central city.
        if (place.matches(".*\\b(okres|okresu)\\s+" + Pattern.quote(expected) + "\\b.*")) return false;
        if ("praha".equals(expected)
                && place.matches(".*\\bpraha\\s*-?\\s*(vychod|zapad)\\b.*")) return false;
        if ("brno".equals(expected) && place.matches(".*\\bbrno\\s*-?\\s*venkov\\b.*")) return false;
        // Address fields may put the street before the city, separated by a comma.
        for (String part : place.split(",")) {
            String value = part.trim().replaceFirst("^\\d{3}\\s*\\d{2}\\s+", "");
            if (value.equals(expected) || value.startsWith(expected + " -")
                    || value.startsWith(expected + "-")
                    || value.matches(Pattern.quote(expected) + "\\s+\\d+\\b.*")) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replaceAll("\\p{Cf}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[–—−]", "-")
                .replaceAll("[\\p{Z}\\s]+", " ").trim();
    }
}
