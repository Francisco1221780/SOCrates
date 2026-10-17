package cyberrangers.cyber.engine.drools;

import cyberrangers.cyber.application.MotorInferencia;
import cyberrangers.cyber.domain.SaudeMotor;
import cyberrangers.motores.drools.EstadoMotor;
import cyberrangers.motores.drools.MotorDrools;
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
            return new SaudeMotor(nome(), estado.pronto(), MotorDrools.VERSAO_KB, estado.erros());
    }
}
