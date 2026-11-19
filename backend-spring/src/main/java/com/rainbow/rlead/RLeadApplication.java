package com.rainbow.rlead;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * R-Lead 애플리케이션 메인 클래스.
 * 명함 → 리드 자동화 웹 프로그램
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class RLeadApplication {

    public static void main(String[] args) {
        SpringApplication.run(RLeadApplication.class, args);
    }
}
