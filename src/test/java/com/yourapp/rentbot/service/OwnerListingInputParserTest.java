package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.Region;
import com.yourapp.rentbot.repo.RegionRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnerListingInputParserTest {
    @Mock private RegionRepo regions;

    @Test
    void recognizesRegionLayoutPriceAndConfirmationAcrossLanguages() {
        Region praha = new Region();
        praha.setTitle("Praha");
        praha.setCode("praha");
        when(regions.findAll()).thenReturn(List.of(praha));
        OwnerListingInputParser parser = new OwnerListingInputParser(regions);

        assertThat(parser.findRegion(" Praha ")).contains(praha);
        assertThat(parser.layout("Pokoj")).isEqualTo("ROOM");
        assertThat(parser.layout("2+kk")).isEqualTo("2");
        assertThat(parser.price("18 500 Kč")).isEqualTo(18500);
        assertThat(parser.required(" - ")).isNull();
        assertThat(parser.isSubmit("Ano")).isTrue();
        assertThat(parser.isCancel("Скасувати")).isTrue();
    }
}
