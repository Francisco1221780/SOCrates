package socrates.backend;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@OpenAPIDefinition(info = @Info(
        title = "SOCrates API",
        version = "0.0.1",
        description = "Analista virtual de SOC: o mesmo pedido em dois motores de inferencia, SWI-Prolog e Drools, "
                + "com a mesma base de conhecimento (v6) e o mesmo formato de resposta."))
public class SocratesApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocratesApplication.class, args);
    }

}
