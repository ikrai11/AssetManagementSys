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
    private final Excel excel = new Excel();
    private final Mail mail = new Mail();
    private final FileStorage file = new FileStorage();

    @Data
    public static class Borrow {
        private int maxDays = 90;
    }

    @Data
    public static class Init {
        private String adminPassword;
        private String userPassword;
    }

    @Data
    public static class Excel {
        private int importMaxRows = 2000;
        private int exportMaxRows = 10000;
    }

    @Data
    public static class Mail {
        private boolean enabled = false;
        private String from = "";
    }

    @Data
    public static class FileStorage {
        private String dir = "data/asset-files";
    }
}
