package org.ticketing.reservation.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * reservation-service 보안 설정.
 *
 * <h3>인증 모델</h3>
 * <p>OAuth2 Resource Server 로 동작한다. Gateway 단에서 1차 검증된 Keycloak JWT 가
 * Authorization 헤더로 그대로 전달되며, 본 서비스에서도 재검증 후 SecurityContext 에
 * {@link org.springframework.security.oauth2.jwt.Jwt} 로 바인딩한다.
 * 컨트롤러에서는 {@code @AuthenticationPrincipal Jwt} 로 주입받아 {@code jwt.getSubject()}
 * (Keycloak 의 {@code sub} 클레임 = userId) 를 꺼내 사용한다.
 *
 * <h3>경로별 권한</h3>
 * <ul>
 *   <li>{@code /actuator/health} — Eureka health URL, 무인증</li>
 *   <li>{@code /swagger-ui/**}, {@code /v3/api-docs/**} — API 문서, 무인증</li>
 *   <li>{@code /internal/**} — 서비스 간 호출, 게이트웨이가 외부 노출 차단</li>
 *   <li>그 외 — 인증 필요 (JWT 검증)</li>
 * </ul>
 *
 * <h3>JWT issuer 설정</h3>
 * <p>{@code spring.security.oauth2.resourceserver.jwt.issuer-uri} 가 config-server 에서
 * 내려와야 한다. 누락 시 부팅 단계에서 명확한 에러로 실패하므로 운영상 안전.
 *
 * <h3>SessionCreationPolicy.STATELESS</h3>
 * <p>JWT 만으로 인증을 결정하므로 서버 측 세션을 만들지 않는다 — 수평 확장에 유리.
 */
@Configuration
public class ReservationSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain reservationFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/**")
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/internal/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                // OAuth2 Resource Server — Keycloak JWT 검증
                // issuer-uri 는 config-server 에서 내려오는 yaml 에 명시되어야 한다.
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .build();
    }
}
