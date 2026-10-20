package de.elternstunden;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// No generated default user: nobody logs in with a password except the config-managed master (D-24, M1)
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class ElternstundenApplication {

    public static void main(String[] args) {
        SpringApplication.run(ElternstundenApplication.class, args);
    }
}
