package com.smartlibrary;

import com.smartlibrary.config.LibraryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(LibraryProperties.class)
public class SmartLibraryApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartLibraryApplication.class, args);
    }
}