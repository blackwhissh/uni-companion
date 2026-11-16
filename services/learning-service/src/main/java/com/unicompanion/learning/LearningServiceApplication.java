package com.unicompanion.learning;

import com.unicompanion.learning.config.GeminiProperties;
import com.unicompanion.learning.config.JwtProperties;
import com.unicompanion.learning.config.RagProperties;
import com.unicompanion.learning.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        JwtProperties.class,
        StorageProperties.class,
        GeminiProperties.class,
        RagProperties.class
})
public class LearningServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearningServiceApplication.class, args);
    }
}
