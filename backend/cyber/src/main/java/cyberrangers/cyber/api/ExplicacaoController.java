package cyberrangers.cyber.api;

import cyberrangers.cyber.application.InferenceService;
import cyberrangers.cyber.domain.Resultado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.BiFunction;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cyberrangers.cyber.api.HttpResponseUtils.codigoHttp;

/**
 * POST /api/{motor}/como, /porque e /porque-pergunta: as explicacoes de uma resposta. Cada pedido traz os passos (como
 * o /infer) e o que se quer explicar; o servidor corre os passos no motor e monta a explicacao a partir da resposta.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Explicacoes", description = "Como?, porque? e porque? de uma pergunta, nos dois motores")
public class ExplicacaoController {

    /**
     * Os codigos HTTP que as tres explicacoes partilham com o /infer, e o 422.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "400", description = "Pedido invalido: JSON, versaoKB, passos, factos, termos ou os "
            + "campos da explicacao")
    @ApiResponse(responseCode = "404", description = "Motor desconhecido")
    @ApiResponse(responseCode = "415", description = "O Content-Type nao e application/json")
    @ApiResponse(responseCode = "422", description = "O facto ou a pergunta pedidos nao estao na resposta do motor")
    @ApiResponse(responseCode = "500", description = "O motor falhou, ou a resposta e de outra versaoKB que as regras")
    @ApiResponse(responseCode = "503", description = "Todos os processos do motor estao ocupados; tentar mais tarde")
    @ApiResponse(responseCode = "504", description = "O motor nao respondeu dentro do timeout")
    @interface RespostasDeErro {
    }

    private final InferenceService inferenceService;

    public ExplicacaoController(InferenceService inferenceService) {
        this.inferenceService = inferenceService;
    }

    /**
     * Corre o pedido no motor dado e devolve a arvore de factos do facto pedido.
     * @param motor
     * @param corpo
     * @return
     */
    @Operation(summary = "Como? (a arvore de uma conclusao)",
            description = "O corpo e o do /infer (versaoKB e passos) com o campo facto: {\"numero\": N} ou "
                    + "{\"predicado\", \"args\"}, nunca os dois. A resposta e {estado, motor, versaoKB, facto, arvore, "
                    + "erro}; cada no da arvore e {numero, predicado, args, regra, jaExplicado, premissas}, e cada "
                    + "premissa {tipo: facto, no}, {tipo: nao, termo} ou {tipo: calculada, termo}.")
    @ApiResponse(responseCode = "200", description = "A arvore do facto")
    @RespostasDeErro
    @PostMapping(path = "/{motor}/como",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> como(
            @Parameter(description = "Motor de inferencia", schema = @Schema(allowableValues = {"drools", "prolog"}))
            @PathVariable String motor,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "versaoKB, passos e facto")
            @RequestBody(required = false) String corpo) {
        return responde(inferenceService::como, motor, corpo);
    }

    /**
     * Corre o pedido no motor dado e devolve a arvore do facto pedido, com a regra completa em cada no concluido.
     * @param motor
     * @param corpo
     * @return
     */
    @Operation(summary = "Porque? (a arvore com as regras)",
            description = "O mesmo pedido e a mesma resposta do /como, e cada no concluido por uma regra traz "
                    + "regraCompleta: {id, se, entao}, com os nomes das variaveis do soc_regras.txt.")
    @ApiResponse(responseCode = "200", description = "A arvore do facto, com as regras")
    @RespostasDeErro
    @PostMapping(path = "/{motor}/porque",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> porque(
            @Parameter(description = "Motor de inferencia", schema = @Schema(allowableValues = {"drools", "prolog"}))
            @PathVariable String motor,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "versaoKB, passos e facto")
            @RequestBody(required = false) String corpo) {
        return responde(inferenceService::porque, motor, corpo);
    }

    /**
     * Corre o pedido no motor dado e explica porque se faz a pergunta pendente pedida.
     * @param motor
     * @param corpo
     * @return
     */
    @Operation(summary = "Porque? de uma pergunta ao analista",
            description = "O corpo e o do /infer com os campos ativo, evento e pergunta de uma pergunta pendente da "
                    + "resposta. A resposta e {estado, motor, versaoKB, pergunta, geradaPor, regrasQueUsamAResposta, "
                    + "erro}: geradaPor e a arvore (com regras) do facto que gerou a pergunta, ou null se foi "
                    + "introduzida a mao; regrasQueUsamAResposta sao os numeros das regras que usam a resposta.")
    @ApiResponse(responseCode = "200", description = "A explicacao da pergunta")
    @RespostasDeErro
    @PostMapping(path = "/{motor}/porque-pergunta",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> porquePergunta(
            @Parameter(description = "Motor de inferencia", schema = @Schema(allowableValues = {"drools", "prolog"}))
            @PathVariable String motor,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "versaoKB, passos, ativo, evento e "
                    + "pergunta")
            @RequestBody(required = false) String corpo) {
        return responde(inferenceService::porquePergunta, motor, corpo);
    }

    /**
     * Chama a explicacao e converte o resultado num codigo HTTP, com o JSON do resultado no corpo.
     * @param explicacao
     * @param motor
     * @param corpo
     * @return
     */
    private static ResponseEntity<String> responde(BiFunction<String, String, Resultado> explicacao, String motor,
                                                   String corpo) {
        Resultado resultado = explicacao.apply(motor, corpo == null ? "" : corpo);
        return ResponseEntity.status(codigoHttp(resultado.tipoResultado())).body(resultado.jsonResultado());
    }
}
