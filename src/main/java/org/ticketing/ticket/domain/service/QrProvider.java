package org.ticketing.ticket.domain.service;

import java.util.UUID;

/**
 * 외부 QR 발급 서비스 추상.
 *
 * <p>도메인 계층은 본 인터페이스만 알며, 실제 발급 방식(URL 서명 / 외부 API 호출 등)은
 * infrastructure.provider 의 구현체가 결정한다.
 */
public interface QrProvider {

    /**
     * 티켓에 부여할 QR 문자열(일반적으로 URL 또는 서명된 토큰)을 발급한다.
     *
     * @param reservationId 발급 대상 예매 ID
     * @param userId        발급 대상 유저 ID
     * @return 길이 300 이하의 QR 문자열
     */
    String issueQr(UUID reservationId, UUID userId);
}
