package socrates.backend.api;

import socrates.backend.application.BaseCarregada;
import socrates.backend.application.InferenceService;
import socrates.backend.domain.SaudeSistema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * O que o sistema tem carregado: o catalogo de cenarios, as regras da base e o estado dos motores.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Sistema", description = "Catalogo de cenarios, regras da base e estado dos motores")
public class SystemController {
    private final InferenceService inferenceService;
    private final BaseCarregada baseCarregada;

    public SystemController(InferenceService inferenceService, BaseCarregada baseCarregada) {
        this.inferenceService = inferenceService;
        this.baseCarregada = baseCarregada;
    }

    /**
     * O cenarios.json tal como foi gerado a partir da base.
     * @return
     */
    @Operation(summary = "Catalogo de cenarios",
            description = "O cenarios.json gerado a partir da base: versaoKB, cada cenario com o passo pronto a "
                    + "enviar ao infer, as dependencias e alternativas, e as sequencias (base, atualizacoes, normal).")
    @ApiResponse(responseCode = "200", description = "O catalogo",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object")))
    @GetMapping(path = "/cenarios", produces = MediaType.APPLICATION_JSON_VALUE)
    public String cenarios() {
        return baseCarregada.cenariosJson();
    }

    /**
     * As regras da base, tal como o gera_json.pl as escreve no regras.json.
     * @return
     */
    @Operation(summary = "Regras da base",
            description = "Todas as regras do soc_regras.txt como dados: {versaoKB, regras: [{id, se, entao}]}. Cada "
                    + "condicao do se e um termo, {\"nao\": termo} ou {\"avalia\": termo} (condicao calculada); as "
                    + "variaveis sao {\"var\": nome}, com os nomes do soc_regras.txt. Cada regra tem o formato da "
                    + "regraCompleta do /como?regras=true.")
    @ApiResponse(responseCode = "200", description = "As regras",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object")))
    @GetMapping(path = "/regras", produces = MediaType.APPLICATION_JSON_VALUE)
    public String regras() {
        return baseCarregada.regrasJson();
    }

    /**
     * O estado do sistema e de cada motor.
     * @return
     */
    @Operation(summary = "Estado do sistema",
            description = "pronto e true quando todos os motores estao prontos; versaoKB e a versao da base que os "
                    + "pedidos tem de usar; cada motor diz se esta pronto e porque nao. Responde sempre 200.")
    @GetMapping(path = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public SaudeSistema health() {
        return inferenceService.saude();
    }
}
