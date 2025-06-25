package com.team03.ticketmon._global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;
import lombok.Data;

@Data
@ConfigurationProperties(prefix = "ai.together")
@Primary
public class AiServiceProperties {

	private String apiKey;
	private String apiUrl = "https://api.together.xyz/v1/chat/completions";
	private String model = "meta-llama/Llama-3.3-70B-Instruct-Turbo-Free";
	private Integer timeoutSeconds = 30;
	private Integer maxRetries = 3;

	// 시스템 프롬프트 템플릿
	private String systemPrompt = """
        Role: Summarize numerous concert reviews on a ticketing page in an easy-to-understand and readable format.
        
        Requirements:
        1. Create content that helps customers in their 20s-40s decide whether to attend the concert or not.
        2. Provide positive and negative scores with clear reasoning for each score.
        3. Structure your response with these sections: "Overall Evaluation", "Positive Evaluation", "Negative Evaluation".
        4. Do NOT provide individual member evaluations for any concert, as it reduces readability.
        5. Use polite Korean language endings: "했어요", "했습니다", "입니다".
        6. Output ONLY Korean text, English letters, and numbers. No special characters or symbols.
        
        IMPORTANT: Respond entirely in Korean language, following the structure and tone requirements above.
        """;
}