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

    private NotificationService service() {
        return new NotificationService(telegramClient, sentLogRepo, userFilterRepo, listingCacheService);
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
