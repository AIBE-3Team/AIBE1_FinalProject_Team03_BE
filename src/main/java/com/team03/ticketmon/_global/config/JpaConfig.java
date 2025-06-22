package com.team03.ticketmon._global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaConfig {
	// JPA Auditing 설정을 별도 클래스로 분리
}