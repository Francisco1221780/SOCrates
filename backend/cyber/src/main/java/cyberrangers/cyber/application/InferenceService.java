package cyberrangers.cyber.application;

import cyberrangers.cyber.domain.SaudeMotor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class InferenceService {

    private final Map<String, MotorInferencia> motores;

    public InferenceService(List<MotorInferencia> motores) {
        this.motores = motores.stream().collect(Collectors.toUnmodifiableMap(MotorInferencia::nome, m -> m));
    }

    /**
     * Devolve o motor de inferência com o nome dado, se existir.
     * @param nome
     * @return
     */
    public Optional<MotorInferencia> getMotor(String nome) {
        return Optional.ofNullable(motores.get(nome));
    }

    /**
     * Devolve a lista de estados de todos os motores de inferência, ordenada pelo nome do motor.
     * @return
     */
    public List<SaudeMotor> estados() {
        return motores.values().stream()
                .map(MotorInferencia::estado)
                .sorted(Comparator.comparing(SaudeMotor::motor)).toList();
    }
}
