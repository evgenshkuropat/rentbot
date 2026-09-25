package com.yourapp.rentbot.service;

import com.yourapp.rentbot.domain.PremiumPaymentRequest;
import com.yourapp.rentbot.domain.UserFilter;
import com.yourapp.rentbot.repo.PremiumPaymentRequestRepo;
import com.yourapp.rentbot.repo.UserFilterRepo;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Optional;

@Service
public class PremiumPaymentService {
    private final PremiumPaymentRequestRepo repo; private final UserFilterRepo users; private final PremiumService premium;
    public PremiumPaymentService(PremiumPaymentRequestRepo repo, UserFilterRepo users, PremiumService premium) { this.repo = repo; this.users = users; this.premium = premium; }
    @Transactional public PremiumPaymentRequest submit(Long userId, String method) {
        PremiumPaymentRequest request = repo.findFirstByTelegramUserIdAndStatusOrderByIdDesc(userId, PremiumPaymentRequest.Status.PENDING).orElseGet(PremiumPaymentRequest::new);
        request.setTelegramUserId(userId); request.setPaymentMethod(method); return repo.save(request);
    }
    @Transactional public Optional<Long> approve(Long requestId) {
        PremiumPaymentRequest request = repo.findById(requestId).orElse(null);
        if (request == null || request.getStatus() != PremiumPaymentRequest.Status.PENDING) return Optional.empty();
        UserFilter user = users.findFullById(request.getTelegramUserId()).orElse(null);
        if (user == null) return Optional.empty();
        request.setStatus(PremiumPaymentRequest.Status.APPROVED); request.setProcessedAt(Instant.now()); repo.save(request);
        premium.activate(user, 30); return Optional.of(user.getTelegramUserId());
    }
    @Transactional public Optional<Long> reject(Long requestId) {
        PremiumPaymentRequest request = repo.findById(requestId).orElse(null);
        if (request == null || request.getStatus() != PremiumPaymentRequest.Status.PENDING) return Optional.empty();
        request.setStatus(PremiumPaymentRequest.Status.REJECTED); request.setProcessedAt(Instant.now()); repo.save(request);
        return Optional.of(request.getTelegramUserId());
    }
}
