package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.PremiumSearch;
import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.i18n.Language;
import com.yourapp.rentbot.repo.PremiumSearchRepo;
import com.yourapp.rentbot.repo.UserFilterRepo;
import com.yourapp.rentbot.ui.Keyboards;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PremiumServiceTest {

    @Mock
    private UserFilterRepo userFilterRepo;
    @Mock
    private PremiumSearchRepo premiumSearchRepo;

    @Test
    void activatesThirtyDayPremiumAccess() {
        PremiumService service = new PremiumService(userFilterRepo, premiumSearchRepo);
        UserFilter user = user(11L);
        when(userFilterRepo.save(any(UserFilter.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.activate(user, 30);

        assertThat(service.isActive(user)).isTrue();
        assertThat(Duration.between(Instant.now(), user.getPremiumUntil()).toDays()).isBetween(29L, 30L);
    }

    @Test
    void createsSecondSearchAndConvertsItToIndependentFilter() {
        PremiumService service = new PremiumService(userFilterRepo, premiumSearchRepo);
        UserFilter user = user(12L);
        Region brno = new Region();
        brno.setCode("BRNO");
        brno.setTitle("Brno");

        when(premiumSearchRepo.findByTelegramUserId(12L)).thenReturn(Optional.empty());
        when(premiumSearchRepo.save(any(PremiumSearch.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PremiumSearch search = service.getOrCreateSearch(user);
        search.setRegion(brno);
        search.setLayout("2");
        search.setMaxPrice(20_000);
        search.setActive(true);

        UserFilter independent = service.asFilter(user, search);

        assertThat(independent.getTelegramUserId()).isEqualTo(12L);
        assertThat(independent.getRegion().getCode()).isEqualTo("BRNO");
        assertThat(independent.getLayout()).isEqualTo("2");
        assertThat(independent.getMaxPrice()).isEqualTo(20_000);
        assertThat(independent.isActive()).isTrue();
    }

    @Test
    void rendersPremiumSetupAction() {
        var callbacks = Keyboards.premiumActiveKeyboard(Language.CZ).getKeyboard().stream()
                .flatMap(java.util.Collection::stream)
                .map(button -> button.getCallbackData())
                .toList();

        assertThat(callbacks).containsExactly("PREMIUM:SETUP");
    }

    private static UserFilter user(long id) {
        UserFilter user = new UserFilter();
        user.setTelegramUserId(id);
        user.setLanguage(Language.CZ);
        return user;
    }
}
