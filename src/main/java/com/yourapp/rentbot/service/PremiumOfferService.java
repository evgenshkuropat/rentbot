package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.PremiumEvent;
import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.i18n.Language;
import com.yourapp.rentbot.repo.PremiumEventRepo;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/** Sends a helpful Premium prompt only when a free user has shown clear intent. */
@Service
public class PremiumOfferService {

    public enum Trigger { FAVORITES, FREE_NOTIFICATION_LIMIT }

    private static final Duration COOLDOWN = Duration.ofDays(90);

    private final PremiumEventRepo events;
    private final PremiumService premiumService;
    private final PremiumMetricsService metrics;
    private final PremiumViewService views;
    private final NotificationService notifications;

    public PremiumOfferService(PremiumEventRepo events,
                               PremiumService premiumService,
                               PremiumMetricsService metrics,
                               PremiumViewService views,
                               NotificationService notifications) {
        this.events = events;
        this.premiumService = premiumService;
        this.metrics = metrics;
        this.views = views;
        this.notifications = notifications;
    }

    public boolean showIfEligible(UserFilter user, Trigger trigger) {
        if (user == null || user.getTelegramUserId() == null || premiumService.isActive(user)) {
            return false;
        }

        Instant cooldownStartedAt = Instant.now().minus(COOLDOWN);
        if (events.existsByTelegramUserIdAndTypeAndCreatedAtAfter(
                user.getTelegramUserId(), PremiumEvent.Type.CONTEXTUAL_OFFER_SHOWN, cooldownStartedAt)) {
            return false;
        }

        Language language = user.getLanguage() != null ? user.getLanguage() : Language.UA;
        String text = trigger == Trigger.FREE_NOTIFICATION_LIMIT
                ? views.contextualOfferAfterFreeLimit(language)
                : views.contextualOfferAfterFavorites(language);

        if (!notifications.sendPremiumOffer(user, text, language)) {
            return false;
        }

        metrics.record(user.getTelegramUserId(), PremiumEvent.Type.CONTEXTUAL_OFFER_SHOWN);
        return true;
    }
}
