package cyberrangers.cyber.api;

import cyberrangers.cyber.application.BaseCarregada;
import cyberrangers.cyber.application.InferenceService;
import cyberrangers.cyber.domain.SaudeSistema;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SystemController {
    private final InferenceService inferenceService;
    private final BaseCarregada baseCarregada;

    public SystemController(InferenceService inferenceService, BaseCarregada baseCarregada) {
        this.inferenceService = inferenceService;
        this.baseCarregada = baseCarregada;
    }

    @GetMapping(path = "/cenarios", produces = MediaType.APPLICATION_JSON_VALUE)
    public String cenarios() {
        return baseCarregada.cenariosJson();

    }

    @GetMapping(path = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public SaudeSistema health() {
        return inferenceService.saude();
    }
}
