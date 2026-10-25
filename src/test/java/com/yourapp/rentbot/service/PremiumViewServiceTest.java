package com.yourapp.rentbot.service;

import com.yourapp.rentbot.i18n.Language;
import com.yourapp.rentbot.ui.Keyboards;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PremiumViewServiceTest {

    private final PremiumViewService view = new PremiumViewService();

    @Test
    void paymentInstructionsAreShortAndClearInEveryLanguage() {
        for (Language language : Language.values()) {
            String text = view.paymentInstructions(language, "RAIFFEISEN");

            assertThat(text).contains("99 Kč", "972026002/5500", "1️⃣", "2️⃣");
            assertThat(text).doesNotContain("Telegram ID");
        }
    }

    @Test
    void paymentConfirmationKeepsOneClearSubmissionAction() {
        var keyboard = Keyboards.premiumPaymentConfirmationKeyboard("Revolut", "https://example.test/pay", Language.CZ);
        var buttons = keyboard.getKeyboard().stream().flatMap(java.util.Collection::stream).toList();

        assertThat(buttons).anySatisfy(button -> {
            assertThat(button.getCallbackData()).isEqualTo("PREMIUM:PAID:REVOLUT");
            assertThat(button.getText()).contains("aktivovat Premium");
        });
    }
}
