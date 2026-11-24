package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.repo.SentLogRepo;
import com.yourapp.rentbot.repo.UserFilterRepo;
import com.yourapp.rentbot.service.dto.ListingDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private TelegramClient telegramClient;
    @Mock
    private SentLogRepo sentLogRepo;
    @Mock
    private UserFilterRepo userFilterRepo;
    @Mock
    private ListingCacheService listingCacheService;
    @Mock
    private RealFreeParser realFreeParser;

    @Test
    void doesNotSendListingThatWasAlreadySentToUser() {
        NotificationService service = service();
        UserFilter user = activeUser(101L);
        ListingDto listing = listing("https://example.test/listing/1");
        when(sentLogRepo.existsByTelegramUserIdAndListingKey(101L, listing.link())).thenReturn(true);

        assertThat(service.sendIfNotSent(user, listing)).isFalse();

        verifyNoInteractions(telegramClient, listingCacheService, userFilterRepo);
    }

    @Test
    void doesNotSendToUserWithStoppedMainSearch() {
        NotificationService service = service();
        UserFilter user = activeUser(102L);
        user.setActive(false);

        assertThat(service.sendIfNotSent(user, listing("https://example.test/listing/2"))).isFalse();

        verifyNoInteractions(telegramClient, sentLogRepo, listingCacheService, userFilterRepo);
    }

    @Test
    void doesNotDeliverExpiredRealFreeEvenWhenTitleAlreadyHasLayout() {
        var listing = realFreeListing();
        when(realFreeParser.availabilityFor(listing)).thenReturn(RealFreeParser.Availability.INACTIVE);
        assertThat(service().sendIfNotSent(activeUser(103L), listing)).isFalse();
        verifyNoInteractions(telegramClient, listingCacheService, userFilterRepo);
        verify(sentLogRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void unknownRealFreeStatusDoesNotMarkListingAsSent() {
        var listing = realFreeListing();
        when(realFreeParser.availabilityFor(listing)).thenReturn(RealFreeParser.Availability.UNKNOWN);
        assertThat(service().sendIfNotSent(activeUser(104L), listing)).isFalse();
        verifyNoInteractions(telegramClient, listingCacheService, userFilterRepo);
        verify(sentLogRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void deliversVerifiedActiveRealFreeAndRecordsSuccessfulSend() throws Exception {
        var listing = realFreeListing();
        when(realFreeParser.availabilityFor(listing)).thenReturn(RealFreeParser.Availability.ACTIVE);
        when(listingCacheService.put(listing)).thenReturn("token");
        assertThat(service().sendIfNotSent(activeUser(105L), listing)).isTrue();
        verify(telegramClient).execute(any(org.telegram.telegrambots.meta.api.methods.send.SendMessage.class));
        verify(sentLogRepo).save(any(com.yourapp.rentbot.domain.SentLog.class));
    }

    @Test
    void skipsRealFreeNetworkCheckForAlreadySentListingAndForOtherSources() {
        var listing = realFreeListing();
        when(sentLogRepo.existsByTelegramUserIdAndListingKey(106L, listing.link())).thenReturn(true);
        assertThat(service().sendIfNotSent(activeUser(106L), listing)).isFalse();
        assertThat(service().isAvailableForDelivery(listing("https://example.test/other"))).isTrue();
        verifyNoInteractions(realFreeParser, telegramClient);
    }

    private static ListingDto realFreeListing() {
        return new ListingDto("Byt 2+kk", 15000, "https://realfree.cz/inzerat/flat", "2+kk", "Praha", "", "RealFree", null);
    }

    private NotificationService service() {
        return new NotificationService(telegramClient, sentLogRepo, userFilterRepo, listingCacheService, realFreeParser);
    }

    private static UserFilter activeUser(long id) {
        UserFilter user = new UserFilter();
        user.setTelegramUserId(id);
        user.setActive(true);
        return user;
    }

    private static ListingDto listing(String link) {
        return new ListingDto(
                "Pronájem bytu 2+kk",
                20_000,
                link,
                "2",
                "Praha",
                null,
                "Sreality",
                LocalDateTime.now()
        );
    }
}
