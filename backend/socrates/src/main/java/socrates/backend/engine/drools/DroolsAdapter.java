package socrates.backend.engine.drools;

import socrates.backend.application.MotorInferencia;
import socrates.backend.domain.Metaconhecimento;
import socrates.backend.domain.SaudeMotor;
import socrates.motores.drools.EstadoMotor;
import socrates.motores.drools.MotorDrools;
import org.springframework.stereotype.Component;

@Component
public class DroolsAdapter implements MotorInferencia {

    private final MotorDrools motor = new MotorDrools();

    @Override
    public String nome() {
        return "drools";
    }

    @Override
    public String infere(String pedidoJson) {
        return motor.infere(pedidoJson);
    }

    @Override
    public SaudeMotor estado() {
        EstadoMotor estado = motor.estado();
        boolean baseCarregada = estado.regrasCarregadas() && estado.factosIniciais() > 0;
        return new SaudeMotor(nome(), estado.pronto(), MotorDrools.VERSAO_KB, baseCarregada,
                Metaconhecimento.NAO_APLICAVEL, estado.erros());
    }
}
