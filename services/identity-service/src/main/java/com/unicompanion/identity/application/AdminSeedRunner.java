package com.unicompanion.identity.application;

import com.unicompanion.identity.config.IdentityProperties;
import com.unicompanion.identity.domain.Role;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
public class AdminSeedRunner {

    @Bean
    ApplicationRunner seedCourseAdmin(AccountService accounts, IdentityProperties properties) {
        return args -> {
            IdentityProperties.Seed seed = properties.seed();
            accounts.seedCourseAdmin(seed.adminEmail(), seed.adminPassword(), seed.adminDisplayName());
            accounts.seedAccount("admin", "admin", "Admin", Set.of(Role.ADMIN, Role.COURSE_ADMIN));
            accounts.seedAccount("student", "student", "Student", Set.of(Role.STUDENT));
            accounts.seedAccount("student1", "student1", "Student 1", Set.of(Role.STUDENT));
            accounts.seedAccount("student2", "student2", "Student 2", Set.of(Role.STUDENT));
            accounts.seedAccount("student3", "student3", "Student 3", Set.of(Role.STUDENT));
            accounts.seedAccount("student4", "student4", "Student 4", Set.of(Role.STUDENT));
            accounts.seedAccount("professor", "professor", "Professor", Set.of(Role.COURSE_ADMIN));
        };
    }
}
