package com.example.yfin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
@EnableReactiveMongoRepositories(basePackages = "com.example.yfin.repo")
public class YfinJavaLiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(YfinJavaLiteApplication.class, args);
    }

}
