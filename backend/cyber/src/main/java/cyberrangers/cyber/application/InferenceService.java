package cyberrangers.cyber.application;

import cyberrangers.cyber.domain.*;
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

    private static final List<String> ERROS_DO_PEDIDO = List.of(
            "pedido JSON invalido", "o pedido tem de", "versaoKB desconhecida", "o passo ",
            "facto invalido no passo", "termo invalido no passo");

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
            pedido = lePedido(corpo);
        } catch (PedidoIlegivel e) {
            return new Resultado(TipoResultado.PEDIDO_INVALIDO, erro(nomeMotor, e.getMessage()));
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
        if (ERROS_DO_PEDIDO.stream().anyMatch(erro::startsWith)) {
            return new Resultado(TipoResultado.PEDIDO_INVALIDO, resposta);
        }
        return new Resultado(TipoResultado.FALHA_MOTOR, resposta);
    }

    /**
     * Le o corpo como Pedido. Os erros dizem o que esta mal sem detalhes internos do Jackson (nomes de classes Java,
     * "Source: REDACTED"): sintaxe com a linha e a coluna, um JSON que nao e um objeto, ou o campo com o tipo errado.
     * @param corpo
     * @return
     */
    private Pedido lePedido(String corpo) {
        JsonNode arvore;
        try {
            arvore = jsonMapper.readTree(corpo);
        } catch (JacksonException e) {
            throw new PedidoIlegivel("pedido JSON invalido na linha " + e.getLocation().getLineNr()
                    + ", coluna " + e.getLocation().getColumnNr() + ": " + semLocalizacao(e.getOriginalMessage()));
        }
        if (arvore == null || arvore.isMissingNode()) {
            throw new PedidoIlegivel("pedido JSON invalido: o corpo esta vazio");
        }
        if (!arvore.isObject()) {
            throw new PedidoIlegivel("o pedido tem de ser um objeto JSON");
        }
        try {
            return jsonMapper.treeToValue(arvore, Pedido.class);
        } catch (JacksonException e) {
            String campo = caminho(e);
            throw new PedidoIlegivel(campo.isEmpty() ? "o pedido tem a forma errada"
                    : "o campo " + campo + " tem o tipo errado");
        }
    }

    /**
     * Tira da mensagem do Jackson a parte que descreve a fonte ("(start marker at [Source: ...])", " at [Source...").
     * @param mensagem
     * @return
     */
    private static String semLocalizacao(String mensagem) {
        for (String corte : List.of(" (start marker at", " at [Source")) {
            int i = mensagem.indexOf(corte);
            if (i >= 0) {
                mensagem = mensagem.substring(0, i);
            }
        }
        return mensagem;
    }

    /**
     * O caminho do campo onde o Jackson falhou, como no JSON: passos[0].factos[1].args.
     * @param e
     * @return
     */
    private static String caminho(JacksonException e) {
        StringBuilder sb = new StringBuilder();
        for (JacksonException.Reference r : e.getPath()) {
            if (r.getPropertyName() != null) {
                sb.append(sb.isEmpty() ? "" : ".").append(r.getPropertyName());
            } else if (r.getIndex() >= 0) {
                sb.append('[').append(r.getIndex()).append(']');
            }
        }
        return sb.toString();
    }

    /**
     * O corpo do pedido nao se le como Pedido; a mensagem vai para o campo erro da resposta.
     */
    private static final class PedidoIlegivel extends RuntimeException {
        PedidoIlegivel(String mensagem) {
            super(mensagem);
        }
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

    /**
     * Devolve o estado do sistema, com a versao da base carregada e a lista de estados de todos os motores.
     * @return
     */
    public SaudeSistema saude() {
        List<SaudeMotor> estados = estados().stream().map(this::comparaVersao).toList();
        boolean pronto = !estados.isEmpty() && estados.stream().allMatch(SaudeMotor::pronto);
        return new SaudeSistema(pronto, base.versaoKB(), estados);
    }

    /**
     * O estado do motor, mas nao pronto se estiver pronto com uma versao da base diferente da do catalogo.
     * @param estado
     * @return
     */
    private SaudeMotor comparaVersao(SaudeMotor estado) {
        if (!estado.pronto() || base.versaoKB().equals(estado.versaoKB())) {
            return estado;
        }
        return estado.naoPronto("o motor tem a versaoKB " + estado.versaoKB() + " e o catalogo de cenarios a "
                + base.versaoKB());
    }
}
