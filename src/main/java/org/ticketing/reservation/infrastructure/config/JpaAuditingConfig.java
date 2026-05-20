package org.ticketing.reservation.infrastructure.config;

/**
 * JPA Auditing 설정은 common-module 에서 일괄 처리된다.
 *
 * <ul>
 *   <li>{@code @EnableJpaAuditing} — common-module {@code JPAConfig}</li>
 *   <li>{@code AuditorAware<String> auditorProvider} — common-module {@code SecurityConfig}</li>
 * </ul>
 *
 * 이 서비스에서는 별도 설정이 필요하지 않다.
 */
public final class JpaAuditingConfig {
    private JpaAuditingConfig() {}
}

