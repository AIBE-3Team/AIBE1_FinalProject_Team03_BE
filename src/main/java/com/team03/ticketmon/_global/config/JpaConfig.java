package com.team03.ticketmon._global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing // JPA Auditing 기능을 활성화하는 어노테이션
public class JpaConfig {
}