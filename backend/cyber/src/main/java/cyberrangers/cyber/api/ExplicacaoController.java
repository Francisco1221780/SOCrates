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
 * POST /api/{motor}/como, /porque, /porque-pergunta e /whynot: as explicacoes de uma resposta. Cada pedido traz os
 * passos (como o /infer) e o que se quer explicar; o servidor corre os passos no motor. O como e os porques montam-se a
 * partir da resposta; o whynot e o motor que o faz, sobre os factos da sessao.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Explicacoes", description = "Como?, porque?, porque? de uma pergunta e porque nao?")
public class ExplicacaoController {

    /**
     * Os codigos HTTP que as explicacoes partilham com o /infer.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "400", description = "Pedido invalido: JSON, versaoKB, passos, factos, termos ou os "
            + "campos da explicacao")
    @ApiResponse(responseCode = "404", description = "Motor desconhecido")
    @ApiResponse(responseCode = "415", description = "O Content-Type nao e application/json")
    @ApiResponse(responseCode = "500", description = "O motor falhou (no como e nos porques, tambem uma resposta de "
            + "outra versaoKB que as regras)")
    @ApiResponse(responseCode = "503", description = "Todos os processos do motor estao ocupados; tentar mais tarde")
    @ApiResponse(responseCode = "504", description = "O motor nao respondeu dentro do timeout")
    @interface RespostasDeErro {
    }

    /**
     * O 422 das explicacoes que se montam a partir da resposta (o como e os porques): o que se pediu nao esta la.
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "422", description = "O facto ou a pergunta pedidos nao estao na resposta do motor")
    @interface SemAlvoNaResposta {
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
    @SemAlvoNaResposta
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
    @SemAlvoNaResposta
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
    @SemAlvoNaResposta
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
     * Corre o pedido no motor dado e explica porque o facto pedido nao se concluiu.
     * @param motor
     * @param corpo
     * @return
     */
    @Operation(summary = "Porque nao? (porque um facto nao se concluiu)",
            description = "O corpo e o do /infer com o campo facto, {\"predicado\", \"args\"} (sem numero: o facto "
                    + "nao existe), em que os args podem ter variaveis ({\"var\": \"Ev\"}), e opcionalmente a "
                    + "profundidade (1 a 8, 4 por omissao). A resposta e {estado, motor, versaoKB, facto, explicacao, "
                    + "erro}: explicacao e uma lista de linhas {nivel, tipo, termo?, regra?, numero?}; as linhas regra "
                    + "trazem o se (cada condicao com verificada) e o entao. Por agora so no Drools: o Prolog responde "
                    + "501.")
    @ApiResponse(responseCode = "200", description = "As linhas da explicacao")
    @ApiResponse(responseCode = "501", description = "O motor ainda nao tem o porque nao (o Prolog, por agora)")
    @RespostasDeErro
    @PostMapping(path = "/{motor}/whynot",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> porqueNao(
            @Parameter(description = "Motor de inferencia", schema = @Schema(allowableValues = {"drools", "prolog"}))
            @PathVariable String motor,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "versaoKB, passos, facto e "
                    + "profundidade")
            @RequestBody(required = false) String corpo) {
        return responde(inferenceService::porqueNao, motor, corpo);
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
