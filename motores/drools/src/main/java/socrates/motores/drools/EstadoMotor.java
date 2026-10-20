package socrates.motores.drools;

import java.util.List;

/**
 * Se o motor esta pronto a inferir (AC13): as regras compilaram e os factos iniciais carregaram.
 * Quando nao esta, {@code erros} diz porque.
 */
public record EstadoMotor(boolean pronto, boolean regrasCarregadas, int factosIniciais, List<String> erros) {

    public EstadoMotor {
        erros = List.copyOf(erros);
    }
}
