package socrates.backend.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * O estado de um motor no /api/health (AC13).
 *
 * @param motor         nome do motor
 * @param pronto        se o motor pode responder a pedidos com a base carregada
 * @param versaoKB      a versao da base que o motor tem carregada; null se nao a conseguiu carregar
 * @param baseCarregada se a base (regras e factos iniciais) carregou sem erros
 * @param erros         porque o motor nao esta pronto; vazia quando esta
 */
public record SaudeMotor(String motor, boolean pronto, String versaoKB, boolean baseCarregada, List<String> erros) {
    public SaudeMotor {
        erros = erros == null ? List.of() : List.copyOf(erros);
    }

    /**
     * O mesmo estado, mas nao pronto e com mais um erro (ex.: a versao do motor nao e a do catalogo).
     * @param erro
     * @return
     */
    public SaudeMotor naoPronto(String erro) {
        List<String> todos = new ArrayList<>(erros);
        todos.add(erro);
        return new SaudeMotor(motor, false, versaoKB, baseCarregada, todos);
    }
}
