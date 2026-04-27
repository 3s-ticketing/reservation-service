package org.ticketing.reservation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * reservation-service 부트스트랩.
 *
 * <p>본 서비스는 reservation, reservationseat, ticket 세 개의 어그리게이트 루트를
 * 각각 별도 패키지({@code org.ticketing.reservation}, {@code org.ticketing.reservationseat},
 * {@code org.ticketing.ticket})로 분리해 운영한다.
 * 따라서 컴포넌트 스캔 베이스를 {@code org.ticketing} 으로 끌어올려
 * 세 어그리게이트와 common-module(설정·유틸)을 모두 스캔한다.
 *
 * <p>{@code @EnableJpaAuditing} 은 common-module 의 {@code JPAConfig} 에서 이미 선언된다.
 */
@SpringBootApplication(scanBasePackages = "org.ticketing")
@EnableDiscoveryClient
@EnableScheduling
public class ReservationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReservationServiceApplication.class, args);
	}

}
