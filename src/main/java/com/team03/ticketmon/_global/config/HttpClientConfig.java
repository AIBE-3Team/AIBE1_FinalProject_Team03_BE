package com.team03.ticketmon._global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(AiServiceProperties.class)
public class HttpClientConfig {

     @Bean
     public RestTemplate restTemplate() {
         RestTemplate restTemplate = new RestTemplate();

         // 타임아웃 설정
         HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
         factory.setConnectionRequestTimeout(5000);
         factory.setConnectTimeout(5000);
         factory.setReadTimeout(30000);

         restTemplate.setRequestFactory(factory);
         return restTemplate;
     }
 }