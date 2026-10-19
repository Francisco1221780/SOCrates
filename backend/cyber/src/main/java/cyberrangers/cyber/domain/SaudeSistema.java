package cyberrangers.cyber.domain;

import java.util.List;

public record SaudeSistema(boolean pronto, String versaoKB, List<SaudeMotor> motores) {
    public SaudeSistema {
        motores = List.copyOf(motores);
    }
}
