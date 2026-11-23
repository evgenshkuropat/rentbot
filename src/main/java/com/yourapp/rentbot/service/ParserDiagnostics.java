package com.yourapp.rentbot.service;

import java.net.URI;

final class ParserDiagnostics {
    private ParserDiagnostics() { }

    static String url(String value) {
        try {
            URI uri = URI.create(value);
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null).toString();
        } catch (Exception e) {
            return "invalid_url";
        }
    }

    static String text(String value) {
        if (value == null) return "";
        String singleLine = value.replaceAll("[\\p{Z}\\s\\p{Cc}]+", " ").trim();
        return singleLine.substring(0, Math.min(singleLine.length(), 160));
    }

    static String httpReason(int status) {
        return switch (status) {
            case 403 -> "access_denied";
            case 404, 410 -> "catalog_or_listing_not_found";
            case 429 -> "rate_limited";
            default -> status >= 500 ? "server_error" : status >= 400 ? "http_error" : "ok";
        };
    }
}
