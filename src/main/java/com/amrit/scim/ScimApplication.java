package com.amrit.scim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the SCIM 2.0 Provisioning Service. */
@SpringBootApplication
public class ScimApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScimApplication.class, args);
    }
}
