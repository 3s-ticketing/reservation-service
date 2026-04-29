package org.ticketing.reservation.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Spring Security Context 에서 현재 인증된 사용자 정보를 제공한다.
 * JPA Auditing(@CreatedBy / @LastModifiedBy) 및 소프트 딜리트 처리에 활용한다.
 */
@Component
public class SecurityContextProvider {

    /**
     * 현재 요청의 인증 주체(username)를 반환한다.
     * 인증 정보가 없으면 "system" 을 반환한다.
     */
    public String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "system";
        }
        return auth.getName();
    }
}
