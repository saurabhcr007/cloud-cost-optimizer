package com.cloudcost;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableJpaRepositories(basePackages = "com.cloudcost.repository")
@EnableScheduling
public class CloudCostOptimizerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudCostOptimizerApplication.class, args);
    }
}