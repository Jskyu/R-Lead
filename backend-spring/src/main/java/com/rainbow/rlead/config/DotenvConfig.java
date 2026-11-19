package com.rainbow.rlead.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * .env 파일을 로드하는 설정 클래스.
 */
public class DotenvConfig implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        try {
            // 루트(R-Lead/.env)는 FastAPI용(PostgreSQL)이므로, IDE에서 루트를 작업 디렉터리로
            // 실행해도 backend-spring/.env 를 우선 사용한다.
            Path envPath = Paths.get("backend-spring", ".env");
            if (!Files.exists(envPath)) {
                envPath = Paths.get(".env");
            }
            if (!Files.exists(envPath)) {
                System.out.println("⚠️  .env file not found, using system environment variables only");
                return;
            }

            Map<String, Object> envMap = new HashMap<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(envPath.toFile()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    // Skip comments and empty lines
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }

                    // Parse KEY=VALUE
                    int index = line.indexOf('=');
                    if (index > 0) {
                        String key = line.substring(0, index).trim();
                        String value = line.substring(index + 1).trim();

                        // Remove surrounding quotes if present, otherwise strip an inline " # comment"
                        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                            value = value.substring(1, value.length() - 1);
                        } else {
                            int hash = value.indexOf(" #");
                            if (hash >= 0) {
                                value = value.substring(0, hash).trim();
                            }
                        }

                        envMap.put(key, value);
                    }
                }
            }

            // Add to Spring environment
            MapPropertySource propertySource = new MapPropertySource("dotenv", envMap);
            applicationContext.getEnvironment().getPropertySources().addFirst(propertySource);

            System.out.println("✅ Loaded " + envMap.size() + " variables from .env file");

        } catch (Exception e) {
            System.err.println("❌ Failed to load .env file: " + e.getMessage());
        }
    }
}
