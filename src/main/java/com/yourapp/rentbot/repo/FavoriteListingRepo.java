package com.yourapp.rentbot.repo;

import com.yourapp.rentbot.domain.FavoriteListing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteListingRepo extends JpaRepository<FavoriteListing, Long> {

    List<FavoriteListing> findAllByTelegramUserIdOrderByCreatedAtDesc(Long telegramUserId);

    boolean existsByTelegramUserIdAndLink(Long telegramUserId, String link);

    Optional<FavoriteListing> findByTelegramUserIdAndLink(Long telegramUserId, String link);

    void deleteByTelegramUserIdAndLink(Long telegramUserId, String link);
}