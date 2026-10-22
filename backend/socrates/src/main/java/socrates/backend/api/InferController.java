package socrates.backend.api;

import socrates.backend.application.InferenceService;
import socrates.backend.domain.Resultado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static socrates.backend.api.HttpResponseUtils.codigoHttp;

/**
 * POST /api/{motor}/infer: passa o corpo original ao InferenceService e converte o resultado num codigo HTTP.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Inferencia", description = "O mesmo pedido em qualquer dos dois motores, com o mesmo formato de resposta")
public class InferController {

    private final InferenceService inferenceService;

    public InferController(InferenceService inferenceService) {
        this.inferenceService = inferenceService;
    }

    /**
     * Corre o pedido no motor dado e devolve a resposta do motor, ou um erro no formato do contrato.
     * @param motor
     * @param corpo
     * @return
     */
    @Operation(summary = "Corre um pedido num motor",
            description = "O pedido e uma lista ordenada de passos (telemetria de um cenario ou respostas do "
                    + "analista). Para cada passo o motor injeta os factos e corre ate nao haver conclusoes novas. "
                    + "Uma pergunta ao analista da estado aguarda_resposta; a resposta entra como um passo novo com "
                    + "resposta_analista. O servidor nao guarda estado: cada pedido traz todos os passos.")
    @ApiResponse(responseCode = "200", description = "Resposta do motor: concluido ou aguarda_resposta",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object"),
                    examples = @ExampleObject(name = "Cenario 7", value = ExemplosResposta.INFER_200)))
    @ApiResponse(responseCode = "400", description = "Pedido invalido: JSON, versaoKB, passos, factos ou termos",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object"),
                    examples = @ExampleObject(name = "versaoKB errada", value = ExemplosResposta.ERRO_400)))
    @ApiResponse(responseCode = "404", description = "Motor desconhecido",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object"),
                    examples = @ExampleObject(name = "Motor desconhecido", value = ExemplosResposta.ERRO_404)))
    @ApiResponse(responseCode = "415", description = "O Content-Type nao e application/json",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object"),
                    examples = @ExampleObject(name = "Content-Type errado", value = ExemplosResposta.ERRO_415)))
    @ApiResponse(responseCode = "500", description = "O motor falhou",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object"),
                    examples = @ExampleObject(name = "swipl nao arrancou", value = ExemplosResposta.ERRO_500)))
    @ApiResponse(responseCode = "503", description = "Todos os processos do motor estao ocupados; tentar mais tarde",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object"),
                    examples = @ExampleObject(name = "Ocupado", value = ExemplosResposta.ERRO_503)))
    @ApiResponse(responseCode = "504", description = "O motor nao respondeu dentro do timeout",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object"),
                    examples = @ExampleObject(name = "Timeout", value = ExemplosResposta.ERRO_504)))
    @PostMapping(path = "/{motor}/infer",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> infere(
            @Parameter(description = "Motor de inferencia", schema = @Schema(allowableValues = {"drools", "prolog"}))
            @PathVariable String motor,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Pedido: versaoKB e passos",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object"),
                    examples = {@ExampleObject(name = "Cenario 7", value = ExemplosPedido.INFER),
                            @ExampleObject(name = "Cenario 7 com a resposta do analista", value = ExemplosPedido.INFER_COM_RESPOSTA)}))
            @RequestBody(required = false) String corpo) {
        Resultado resultado = inferenceService.infere(motor, corpo == null ? "" : corpo);
        return ResponseEntity.status(codigoHttp(resultado.tipoResultado())).body(resultado.jsonResultado());
    }
}
