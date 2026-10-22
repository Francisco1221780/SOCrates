package socrates.backend.config;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import socrates.backend.application.BaseCarregada;

/**
 * Junta a versao da base em uso a descricao do Swagger UI, em vez de a escrever a mao na anotacao do
 * SocratesApplication: a versao vem do cenarios.json gerado (BaseCarregada), que a tira do cabecalho do soc_factos.txt.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenApiCustomizer versaoDaBase(BaseCarregada baseCarregada) {
        return openApi -> {
            String descricao = openApi.getInfo().getDescription();
            openApi.getInfo().setDescription(descricao + " Base de conhecimento em uso: versao " + baseCarregada.versaoKB()
                    + " (os pedidos levam essa versaoKB).");
        };
    }
}
