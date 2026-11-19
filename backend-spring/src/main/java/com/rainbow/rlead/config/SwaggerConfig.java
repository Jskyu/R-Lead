package com.rainbow.rlead.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger/OpenAPI 설정.
 */
@Configuration
public class SwaggerConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("R-Lead API")
                        .version("1.0.0")
                        .description("""
                                명함 → 리드 자동화 웹 프로그램 REST API

                                ## 주요 기능
                                - 명함 업로드 (단일/다중)
                                - AI 자동 추출 (Gemini/OpenAI/Claude)
                                - 리드 관리 (조회/수정/승인/거부)
                                - 일괄 처리 (재추출/삭제)
                                - 신뢰도 계산 (0-99)
                                - 중복 체크
                                - CRM 통합

                                ## 백엔드 선택
                                이 프로젝트는 FastAPI(Python)와 Spring Boot(Java) 두 가지 백엔드를 제공합니다.
                                프론트엔드는 환경 변수로 백엔드를 선택할 수 있습니다.
                                """)
                        .contact(new Contact()
                                .name("R-Lead Team")
                                .email("noreply@example.com"))
                        .license(new License()
                                .name("Internal Use Only")
                                .url("https://example.com")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("로컬 개발 서버"),
                        new Server()
                                .url("http://localhost:8000")
                                .description("FastAPI 백엔드 (참고)")
                ));
    }
}
