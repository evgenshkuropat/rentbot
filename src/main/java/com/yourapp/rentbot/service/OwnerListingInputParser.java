package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.repo.RegionRepo;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Optional;

@Service
public class OwnerListingInputParser {
    private final RegionRepo regionRepo;

    public OwnerListingInputParser(RegionRepo regionRepo) {
        this.regionRepo = regionRepo;
    }

    public Optional<Region> findRegion(String input) {
        String normalized = normalize(input);
        if (normalized.isBlank()) return Optional.empty();
        return regionRepo.findAll().stream()
                .filter(region -> normalize(region.getTitle()).equals(normalized)
                        || normalize(region.getCode()).equals(normalized))
                .findFirst()
                .or(() -> regionRepo.findAll().stream()
                        .filter(region -> normalize(region.getTitle()).contains(normalized)
                                || normalized.contains(normalize(region.getTitle())))
                        .findFirst());
    }

    public String layout(String text) {
        if (text == null) return null;
        String normalized = normalize(text);
        if (normalized.equals("room") || normalized.contains("kimnata") || normalized.contains("komnata") || normalized.contains("pokoj")) return "ROOM";
        if (normalized.startsWith("1")) return "1";
        if (normalized.startsWith("2")) return "2";
        if (normalized.startsWith("3")) return "3";
        if (normalized.startsWith("4")) return "4";
        return null;
    }

    public Integer price(String text) {
        if (text == null) return null;
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isBlank()) return null;
        try {
            int price = Integer.parseInt(digits);
            return price > 0 ? price : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String required(String text) {
        return text == null || text.isBlank() || "-".equals(text.trim()) ? null : text.trim();
    }

    public boolean isSubmit(String text) {
        String lower = text == null ? "" : text.trim().toLowerCase();
        if (lower.equals("так") || lower.equals("та") || lower.equals("да") || lower.equals("отправить")
                || lower.equals("надіслати") || lower.equals("відправити") || lower.equals("відправ") || lower.equals("odeslat")) return true;
        String normalized = normalize(text);
        return normalized.equals("tak") || normalized.equals("ta") || normalized.equals("yes") || normalized.equals("y")
                || normalized.equals("da") || normalized.equals("ano") || normalized.equals("ok") || normalized.equals("send")
                || normalized.equals("submit") || normalized.equals("nadislat") || normalized.equals("vidpravyty")
                || normalized.equals("odeslat") || normalized.equals("otpravit");
    }

    public boolean isCancel(String text) {
        String lower = text == null ? "" : text.trim().toLowerCase();
        if (lower.equals("ні") || lower.equals("нет") || lower.equals("скасувати") || lower.equals("отмена")
                || lower.equals("отменить") || lower.equals("zrušit") || lower.equals("zrusit")) return true;
        String normalized = normalize(text);
        return normalized.equals("ni") || normalized.equals("no") || normalized.equals("ne") || normalized.equals("net")
                || normalized.equals("cancel") || normalized.equals("skasuvaty") || normalized.equals("otmena");
    }

    private String normalize(String value) {
        if (value == null) return "";
        String noAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.toLowerCase().replaceAll("[^a-z0-9]+", "");
    }
}
