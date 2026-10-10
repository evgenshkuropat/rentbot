package com.yourapp.rentbot.service;

import com.yourapp.rentbot.service.dto.ListingDto;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Detects cautious, text-based signs that a listing may be offered directly by an owner.
 * This is deliberately not a verification: only listings added and approved in the bot are verified.
 */
public final class OwnerSignalDetector {

    public static final String POSSIBLE_OWNER_MARKER = "|possible-owner";

    private static final List<String> DIRECT_OWNER_PHRASES = List.of(
            "primy majitel", "primo od majitele", "od majitele",
            "majitel pronajme", "majitel pronajima", "pronajima majitel",
            "soukromy pronajem", "bez realitni kancelare", "bez realitky", "bez rk"
    );

    private static final List<String> AGENCY_PHRASES = List.of(
            "realitni kancelar", "rk zprostredkuje", "zprostredkuje pronajem",
            "nase spolecnost", "realitni makler", "makler nabizi", "provize rk"
    );

    private OwnerSignalDetector() {
    }

    public static boolean hasPossibleOwnerSignal(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return false;
        }

        boolean directOwnerPhrase = DIRECT_OWNER_PHRASES.stream().anyMatch(normalized::contains);
        boolean agencyPhrase = AGENCY_PHRASES.stream().anyMatch(normalized::contains);
        return directOwnerPhrase && !agencyPhrase;
    }

    public static String sourceWithPossibleOwnerSignal(String source, String text) {
        String safeSource = source == null ? "" : source;
        if (isVerifiedOwnerSource(safeSource) || !hasPossibleOwnerSignal(text)) {
            return safeSource;
        }
        return safeSource + POSSIBLE_OWNER_MARKER;
    }

    public static boolean isPossibleOwnerListing(ListingDto listing) {
        return listing != null && listing.source() != null
                && listing.source().toLowerCase(Locale.ROOT).contains(POSSIBLE_OWNER_MARKER);
    }

    public static boolean isVerifiedOwnerListing(ListingDto listing) {
        return listing != null && isVerifiedOwnerSource(listing.source());
    }

    public static String visibleSource(String source) {
        if (source == null) {
            return "";
        }
        int markerIndex = source.toLowerCase(Locale.ROOT).indexOf(POSSIBLE_OWNER_MARKER);
        return markerIndex >= 0 ? source.substring(0, markerIndex).trim() : source;
    }

    public static boolean isVerifiedOwnerSource(String source) {
        if (source == null) {
            return false;
        }
        String normalized = source.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("owner") || normalized.startsWith("owner:") || normalized.equals("власник");
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
