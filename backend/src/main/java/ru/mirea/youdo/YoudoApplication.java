package ru.mirea.youdo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(exclude=UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class YoudoApplication {
    public static void main(String[] args) { SpringApplication.run(YoudoApplication.class, args); }
}
