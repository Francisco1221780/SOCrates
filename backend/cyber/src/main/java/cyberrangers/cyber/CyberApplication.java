package cyberrangers.cyber;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CyberApplication {

    public static void main(String[] args) {
        SpringApplication.run(CyberApplication.class, args);
    }

}
