package com.yourapp.rentbot.repo;

import com.yourapp.rentbot.domain.PremiumPaymentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PremiumPaymentRequestRepo extends JpaRepository<PremiumPaymentRequest, Long> {
    Optional<PremiumPaymentRequest> findFirstByTelegramUserIdAndStatusOrderByIdDesc(Long userId, PremiumPaymentRequest.Status status);
    List<PremiumPaymentRequest> findByStatusOrderByCreatedAtAsc(PremiumPaymentRequest.Status status);
    List<PremiumPaymentRequest> findTop20ByOrderByIdDesc();
}
