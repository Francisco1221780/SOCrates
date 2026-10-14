package cyberrangers.cyber.engine.drools;

import cyberrangers.cyber.application.MotorInferencia;
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
}
