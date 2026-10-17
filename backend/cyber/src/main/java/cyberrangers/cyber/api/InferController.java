package cyberrangers.cyber.api;

import cyberrangers.cyber.application.InferenceService;
import cyberrangers.cyber.domain.Resultado;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static cyberrangers.cyber.api.HttpResponseUtils.codigoHttp;

@RestController
@RequestMapping("/api")
public class InferController {

    private final InferenceService inferenceService;

    public InferController(InferenceService inferenceService) {
        this.inferenceService = inferenceService;
    }

    @PostMapping(path = "/{motor}/infer", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> infere(@PathVariable String motor, @RequestBody(required = false) String corpo) {
        Resultado resultado = inferenceService.infere(motor, corpo == null ? "" : corpo);
        return ResponseEntity.status(codigoHttp(resultado.tipoResultado())).body(resultado.jsonResultado());
    }
}
