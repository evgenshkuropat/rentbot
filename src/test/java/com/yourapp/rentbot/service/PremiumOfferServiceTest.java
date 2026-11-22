package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.PremiumEvent;
import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.i18n.Language;
import com.yourapp.rentbot.repo.PremiumEventRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PremiumOfferServiceTest {

    @Mock private PremiumEventRepo events;
    @Mock private PremiumService premiumService;
    @Mock private PremiumMetricsService metrics;
    @Mock private PremiumViewService views;
    @Mock private NotificationService notifications;

    @Test
    void doesNotRepeatOfferDuringCooldown() {
        UserFilter user = user(42L);
        PremiumOfferService service = service();
        when(premiumService.isActive(user)).thenReturn(false);
        when(events.existsByTelegramUserIdAndTypeAndCreatedAtAfter(
                eq(42L), eq(PremiumEvent.Type.CONTEXTUAL_OFFER_SHOWN), any(Instant.class)))
                .thenReturn(true);

        assertThat(service.showIfEligible(user, PremiumOfferService.Trigger.FAVORITES)).isFalse();

        verify(notifications, never()).sendPremiumOffer(any(), any(), any());
        verify(metrics, never()).record(any(), any());
    }

    @Test
    void sendsOfferWhenFreeUserReachesNotificationLimit() {
        UserFilter user = user(84L);
        PremiumOfferService service = service();
        when(premiumService.isActive(user)).thenReturn(false);
        when(events.existsByTelegramUserIdAndTypeAndCreatedAtAfter(
                eq(84L), eq(PremiumEvent.Type.CONTEXTUAL_OFFER_SHOWN), any(Instant.class)))
                .thenReturn(false);
        when(views.contextualOfferAfterFreeLimit(Language.UA)).thenReturn("Premium offer");
        when(notifications.sendPremiumOffer(user, "Premium offer", Language.UA)).thenReturn(true);

        assertThat(service.showIfEligible(user, PremiumOfferService.Trigger.FREE_NOTIFICATION_LIMIT)).isTrue();

        verify(metrics).record(84L, PremiumEvent.Type.CONTEXTUAL_OFFER_SHOWN);
    }

    private PremiumOfferService service() {
        return new PremiumOfferService(events, premiumService, metrics, views, notifications);
    }

    private UserFilter user(long id) {
        UserFilter user = new UserFilter();
        user.setTelegramUserId(id);
        user.setLanguage(Language.UA);
        return user;
    }
}
