package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.PremiumPaymentRequest;
import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.repo.PremiumPaymentRequestRepo;
import com.yourapp.rentbot.repo.UserFilterRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PremiumPaymentServiceTest {

    @Mock
    private PremiumPaymentRequestRepo paymentRequests;
    @Mock
    private UserFilterRepo users;
    @Mock
    private PremiumService premiumService;

    @Test
    void approvesPendingRequestOnlyOnce() {
        PremiumPaymentRequest request = new PremiumPaymentRequest();
        request.setTelegramUserId(17L);
        request.setPaymentMethod("RAIFFEISEN");
        UserFilter user = new UserFilter();
        user.setTelegramUserId(17L);
        PremiumPaymentService service = new PremiumPaymentService(paymentRequests, users, premiumService);

        when(paymentRequests.findById(5L)).thenReturn(Optional.of(request));
        when(users.findFullById(17L)).thenReturn(Optional.of(user));

        assertThat(service.approve(5L)).contains(17L);
        assertThat(service.approve(5L)).isEmpty();

        verify(premiumService).activate(user, 30);
        verify(paymentRequests).save(request);
        verify(users, never()).save(any());
    }
}
