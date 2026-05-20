package org.ticketing.reservation.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.ticketing.reservation.domain.service.PaymentStatusProvider;

/**
 * PaymentStatusProvider 빈 설정.
 *
 * <p>Feign 클라이언트가 구현되지 않은 동안 {@link PaymentStatusProvider#STUB} 을 사용한다.
 * 추후 Feign 클라이언트 구현 시 {@code @ConditionalOnMissingBean} 으로 인해 자동 교체된다.
 */
@Configuration
public class PaymentClientConfig {

    /**
     * Feign 클라이언트 구현이 없을 때 Stub을 빈으로 등록한다.
     *
     * <p>Feign 기반 {@code PaymentStatusProvider} 빈이 존재하면 이 메서드는 호출되지 않는다.
     */
    @Bean
    @ConditionalOnMissingBean(PaymentStatusProvider.class)
    public PaymentStatusProvider paymentStatusProviderStub() {
        return PaymentStatusProvider.STUB;
    }
}
