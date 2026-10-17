package cyberrangers.cyber.application;

import cyberrangers.cyber.domain.Pedido;
import cyberrangers.cyber.domain.Resultado;
import cyberrangers.cyber.domain.SaudeMotor;
import cyberrangers.cyber.domain.TipoResultado;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Escolhe o motor de inferencia pelo nome, valida o pedido antes de lho passar e diz o que aconteceu, para a API
 * escolher o codigo HTTP. Junta tambem o estado de todos os motores (para o /api/health).
 */
@Service
public class InferenceService {

    private final Map<String, MotorInferencia> motores;
    private final RequestValidator requestValidator;
    private final BaseCarregada base;
    private final JsonMapper jsonMapper;

    public InferenceService(List<MotorInferencia> motores, RequestValidator requestValidator,
                            BaseCarregada base, JsonMapper jsonMapper) {
        this.motores = motores.stream().collect(Collectors.toUnmodifiableMap(MotorInferencia::nome, m -> m));
        this.requestValidator = requestValidator;
        this.base = base;
        this.jsonMapper = jsonMapper;
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

    /**
     * Infere o pedido no motor dado, depois de verificar que o motor existe e que o pedido e valido. O motor recebe o
     * corpo original; o resultado diz o que aconteceu e traz sempre JSON no formato do contrato.
     * @param nomeMotor
     * @param corpo
     * @return
     */
    public Resultado infere(String nomeMotor, String corpo) {
        MotorInferencia motor = motores.get(nomeMotor);
        if (motor == null) {
            String nomes = String.join(", ", motores.keySet().stream().sorted().toList());
            return new Resultado(TipoResultado.MOTOR_INEXISTENTE,
                    erro(nomeMotor, "motor desconhecido: " + nomeMotor + " (disponiveis: " + nomes + ")"));
        }
        Pedido pedido;
        try {
            pedido = jsonMapper.readValue(corpo, Pedido.class);
        } catch (JacksonException e) {
            return new Resultado(TipoResultado.PEDIDO_INVALIDO,
                    erro(nomeMotor, "pedido JSON invalido: " + e.getOriginalMessage()));
        }

        List<String> erros = requestValidator.validaPedido(pedido, base.versaoKB());
        if (!erros.isEmpty()) {
            return new Resultado(TipoResultado.PEDIDO_INVALIDO, erro(nomeMotor, String.join("; ", erros)));
        }
        String resposta = motor.infere(corpo);
        return classifica(nomeMotor, resposta);
    }

    /**
     * Classifica a resposta do motor pelo estado e pelo erro. Se a resposta nao for um objeto JSON, troca-a por um
     * erro do contrato, para o cliente receber sempre JSON.
     * @param nomeMotor
     * @param resposta
     * @return
     */
    private Resultado classifica(String nomeMotor, String resposta) {
        JsonNode r;
        try {
            r = jsonMapper.readTree(resposta);
        } catch (JacksonException e) {
            return new Resultado(TipoResultado.FALHA_MOTOR,
                    erro(nomeMotor, "resposta do motor nao e JSON: " + e.getOriginalMessage()));
        }
        if (!r.isObject()) {
            return new Resultado(TipoResultado.FALHA_MOTOR, erro(nomeMotor, "resposta do motor nao e um objeto JSON"));
        }
        if (!"erro".equals(r.path("estado").asString())) {
            return new Resultado(TipoResultado.OK, resposta);
        }
        String erro = r.path("erro").asString();
        if (erro.equals("timeout")) {
            return new Resultado(TipoResultado.TIMEOUT, resposta);
        }
        if (erro.startsWith("ocupado")) {
            return new Resultado(TipoResultado.OCUPADO, resposta);
        }
        return new Resultado(TipoResultado.FALHA_MOTOR, resposta);
    }

    /**
     * Resposta de erro no formato do contrato, com a versao da base carregada.
     * @param motor
     * @param mensagem
     * @return
     */
    private String erro(String motor, String mensagem) {
        return jsonMapper.writeValueAsString(jsonMapper.createObjectNode()
                .put("estado", "erro")
                .put("motor", motor)
                .put("versaoKB", base.versaoKB())
                .put("erro", mensagem));
    }
}
