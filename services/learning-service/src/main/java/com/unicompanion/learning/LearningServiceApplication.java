package com.unicompanion.learning;

import com.unicompanion.learning.config.GeminiProperties;
import com.unicompanion.learning.lms.LmsSyncProperties;
import com.unicompanion.learning.lms.MoodleProperties;
import com.unicompanion.learning.config.JwtProperties;
import com.unicompanion.learning.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        JwtProperties.class,
        StorageProperties.class,
        GeminiProperties.class,
        MoodleProperties.class,
        LmsSyncProperties.class,
        com.unicompanion.learning.config.IngestProperties.class,
        com.unicompanion.learning.config.AiRateLimitProperties.class
})
public class LearningServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearningServiceApplication.class, args);
    }
}
