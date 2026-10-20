package cyberrangers.cyber.engine.drools;

import cyberrangers.cyber.application.MotorInferencia;
import cyberrangers.cyber.domain.Metaconhecimento;
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
    public String porqueNao(String pedidoJson, String factoJson, int profundidade) {
        return motor.porqueNao(pedidoJson, factoJson, profundidade);
    }

    @Override
    public SaudeMotor estado() {
        EstadoMotor estado = motor.estado();
        boolean baseCarregada = estado.regrasCarregadas() && estado.factosIniciais() > 0;
        return new SaudeMotor(nome(), estado.pronto(), MotorDrools.VERSAO_KB, baseCarregada,
                Metaconhecimento.NAO_APLICAVEL, estado.erros());
    }
}
