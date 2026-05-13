package com.amrit.scim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the SCIM 2.0 Provisioning Service.
 * <p>
 * {@code @SpringBootApplication} is a convenience annotation that combines
 * {@code @Configuration}, {@code @EnableAutoConfiguration}, and
 * {@code @ComponentScan} — it tells Spring Boot to auto-configure everything
 * (web server, JPA, security) and scan this package for beans.
 */
@SpringBootApplication
public class ScimApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScimApplication.class, args);
    }
}
