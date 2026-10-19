package cyberrangers.cyber.domain;

import java.util.List;

public record SaudeMotor(String motor, boolean pronto, String versaoKB, List<String> erros) {
    public SaudeMotor {
        erros = erros == null ? List.of() : List.copyOf(erros);
    }
}
