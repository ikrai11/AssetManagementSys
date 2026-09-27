package com.ikrai.project.config;

import lombok.Data;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Component
@ConfigurationProperties(prefix = "ams")
public class AmsProperties {

    private final Borrow borrow = new Borrow();
    private final Init init = new Init();

    @Data
    public static class Borrow {
        private int maxDays = 90;
    }

    @Data
    public static class Init {
        private String adminPassword;
        private String userPassword;
    }
}
