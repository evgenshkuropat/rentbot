package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.FavoriteListing;
import com.yourapp.rentbot.repo.FavoriteListingRepo;
import com.yourapp.rentbot.service.dto.ListingDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class FavoriteService {

    private final FavoriteListingRepo favoriteListingRepo;

    public FavoriteService(FavoriteListingRepo favoriteListingRepo) {
        this.favoriteListingRepo = favoriteListingRepo;
    }

    public boolean addFavorite(Long userId, ListingDto dto) {
        if (dto == null || dto.link() == null || dto.link().isBlank()) {
            return false;
        }

        if (favoriteListingRepo.existsByTelegramUserIdAndLink(userId, dto.link())) {
            return false;
        }

        FavoriteListing fav = new FavoriteListing();
        fav.setTelegramUserId(userId);
        fav.setTitle(dto.title());
        fav.setPriceCzk(dto.priceCzk());
        fav.setLink(dto.link());
        fav.setLayout(dto.layout());
        fav.setLocality(dto.locality());
        fav.setPhotoUrl(dto.photoUrl());
        fav.setSource(dto.source());
        fav.setCreatedAt(Instant.now());

        favoriteListingRepo.save(fav);
        return true;
    }

    public List<FavoriteListing> getFavorites(Long userId) {
        return favoriteListingRepo.findAllByTelegramUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public boolean removeFavorite(Long userId, String link) {
        if (link == null || link.isBlank()) {
            return false;
        }

        if (!favoriteListingRepo.existsByTelegramUserIdAndLink(userId, link)) {
            return false;
        }

        favoriteListingRepo.deleteByTelegramUserIdAndLink(userId, link);
        return true;
    }

    public long countAll() {
        return favoriteListingRepo.count();
    }
}