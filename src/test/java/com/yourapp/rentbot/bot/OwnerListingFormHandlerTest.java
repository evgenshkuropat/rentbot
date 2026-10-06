package com.yourapp.rentbot.bot;

import com.yourapp.rentbot.domain.OwnerListing;
import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.i18n.Language;
import com.yourapp.rentbot.repo.RegionRepo;
import com.yourapp.rentbot.service.OwnerListingInputParser;
import com.yourapp.rentbot.service.OwnerListingMessages;
import com.yourapp.rentbot.service.OwnerListingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnerListingFormHandlerTest {

    @Mock
    private RegionRepo regions;
    @Mock
    private OwnerListingService ownerListings;

    @Test
    void completesFormAndSavesOnlyAfterPhotoAndConfirmation() {
        Region praha = new Region();
        praha.setTitle("Praha");
        praha.setCode("Praha");
        OwnerListing saved = new OwnerListing();
        OwnerListingFormHandler handler = new OwnerListingFormHandler(
                new OwnerListingInputParser(regions), new OwnerListingMessages(), ownerListings);
        when(regions.findAll()).thenReturn(List.of(praha));
        when(ownerListings.savePending(org.mockito.ArgumentMatchers.any(OwnerListing.class))).thenReturn(saved);

        handler.start(42L, "owner", Language.EN);
        handler.handleText(42L, "Praha", Language.EN);
        handler.handleText(42L, "Praha 2", Language.EN);
        handler.handleText(42L, "2", Language.EN);
        handler.handleText(42L, "20000", Language.EN);
        handler.handleText(42L, "Bright flat", Language.EN);
        handler.handleText(42L, "-", Language.EN);
        handler.handleText(42L, "+420123456789", Language.EN);
        OwnerListingFormHandler.Result preview = handler.handlePhoto(42L, "telegram-photo", Language.EN);

        assertThat(preview.keyboard()).isEqualTo(OwnerListingFormHandler.ReplyKeyboard.CONFIRM);
        assertThat(handler.submit(42L, Language.EN).submittedListing()).isSameAs(saved);

        ArgumentCaptor<OwnerListing> listing = ArgumentCaptor.forClass(OwnerListing.class);
        verify(ownerListings).savePending(listing.capture());
        assertThat(listing.getValue().getCreatedByTelegramId()).isEqualTo(42L);
        assertThat(listing.getValue().getLayout()).isEqualTo("2");
        assertThat(listing.getValue().getPhotoFileId()).isEqualTo("telegram-photo");
    }

    @Test
    void doesNotAcceptPhotoBeforeItsStep() {
        OwnerListingFormHandler handler = new OwnerListingFormHandler(
                new OwnerListingInputParser(regions), new OwnerListingMessages(), ownerListings);
        handler.start(43L, "owner", Language.RU);

        OwnerListingFormHandler.Result result = handler.handlePhoto(43L, "telegram-photo", Language.RU);

        assertThat(result.text()).contains("последнем шаге");
    }
}
