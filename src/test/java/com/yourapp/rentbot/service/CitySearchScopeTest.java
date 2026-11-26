package com.yourapp.rentbot.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CitySearchScopeTest {
    @Test void acceptsCityPartsAndStreetBeforeCity() {
        assertThat(CitySearchScope.matches("Brno - Kralovo Pole", "Brno")).isTrue();
        assertThat(CitySearchScope.matches("Dlouha 10, 280 02 Kolin", "Kolin")).isTrue();
        assertThat(CitySearchScope.matches("Praha 5 - Smichov", "Praha")).isTrue();
        assertThat(CitySearchScope.matches("Novy Jicin", "NOVY_JICIN")).isTrue();
    }

    @Test void rejectsDistrictOnlyAndSimilarCityNames() {
        assertThat(CitySearchScope.matches("Ricany, Praha-vychod", "Praha")).isFalse();
        assertThat(CitySearchScope.matches("Zdanice, okres Hodonin", "Hodonin")).isFalse();
        assertThat(CitySearchScope.matches("Brno-venkov", "Brno")).isFalse();
        assertThat(CitySearchScope.matches("Stary Kolin", "Kolin")).isFalse();
        assertThat(CitySearchScope.matches("", "Kolin")).isFalse();
    }
}
