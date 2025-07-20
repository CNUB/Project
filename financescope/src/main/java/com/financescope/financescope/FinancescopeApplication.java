// FinancescopeApplication.java
package com.financescope.financescope;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.context.annotation.ComponentScan;

@EnableAsync
@SpringBootApplication(exclude = {
    org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
    org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class
})
@ComponentScan(basePackages = {
    "com.financescope.financescope",
    "com.financescope.financescope.service",
    "com.financescope.financescope.service.external"
})
public class FinancescopeApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinancescopeApplication.class, args);
    }
}