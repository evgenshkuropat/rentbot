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
import java.util.List;

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

    @Test
    void rejectedRequestNeverActivatesPremium() {
        PremiumPaymentRequest request = new PremiumPaymentRequest();
        request.setTelegramUserId(18L);
        PremiumPaymentService service = new PremiumPaymentService(paymentRequests, users, premiumService);

        when(paymentRequests.findById(6L)).thenReturn(Optional.of(request));

        assertThat(service.reject(6L)).contains(18L);
        assertThat(service.reject(6L)).isEmpty();

        verify(premiumService, never()).activate(any(), any(Integer.class));
        verify(paymentRequests).save(request);
    }

    @Test
    void exposesPendingAndRecentPaymentRequestsForAdminReview() {
        PremiumPaymentRequest pending = new PremiumPaymentRequest();
        pending.setTelegramUserId(19L);
        PremiumPaymentRequest processed = new PremiumPaymentRequest();
        processed.setTelegramUserId(20L);
        processed.setStatus(PremiumPaymentRequest.Status.APPROVED);
        PremiumPaymentService service = new PremiumPaymentService(paymentRequests, users, premiumService);

        when(paymentRequests.findByStatusOrderByCreatedAtAsc(PremiumPaymentRequest.Status.PENDING))
                .thenReturn(List.of(pending));
        when(paymentRequests.findTop20ByOrderByIdDesc()).thenReturn(List.of(processed, pending));

        assertThat(service.pendingRequests()).containsExactly(pending);
        assertThat(service.recentRequests()).containsExactly(processed, pending);
    }
}
