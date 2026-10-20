package socrates.backend.application;

import socrates.backend.domain.*;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Escolhe o motor de inferencia pelo nome, valida o pedido antes de lho passar e diz o que aconteceu, para a API
 * escolher o codigo HTTP. As explicacoes ("como?", "porque?", "porque?" de uma pergunta) correm o mesmo pedido e
 * montam-se a partir da resposta, com o Explicador. Junta tambem o estado de todos os motores (para o /api/health).
 */
@Service
public class InferenceService {

    private static final List<String> ERROS_DO_PEDIDO = List.of(
            "pedido JSON invalido", "o pedido tem de", "versaoKB desconhecida", "o passo ",
            "facto invalido no passo", "termo invalido no passo");

    private static final int PROFUNDIDADE_POR_OMISSAO = 4;

    private static final List<String> ERROS_SEM_ALVO = List.of("esse facto nao esta", "essa pergunta nao esta");

    private final Map<String, MotorInferencia> motores;
    private final RequestValidator requestValidator;
    private final BaseCarregada base;
    private final Explicador explicador;
    private final JsonMapper jsonMapper;

    public InferenceService(List<MotorInferencia> motores, RequestValidator requestValidator,
                            BaseCarregada base, Explicador explicador, JsonMapper jsonMapper) {
        this.motores = motores.stream().collect(Collectors.toUnmodifiableMap(MotorInferencia::nome, m -> m));
        this.requestValidator = requestValidator;
        this.base = base;
        this.explicador = explicador;
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
        return corre(nomeMotor, corpo, pedido -> List.of(), (motor, pedido) -> motor.infere(corpo), null);
    }

    /**
     * "Como?": corre o pedido e devolve a arvore de factos do facto pedido (campo facto, por numero ou por predicado e
     * args). NAO_ENCONTRADO se o facto nao esta na resposta.
     * @param nomeMotor
     * @param corpo
     * @return
     */
    public Resultado como(String nomeMotor, String corpo) {
        return arvore(nomeMotor, corpo, false);
    }

    /**
     * "Porque?": o mesmo que o como, e cada no concluido traz a regra completa (regraCompleta).
     * @param nomeMotor
     * @param corpo
     * @return
     */
    public Resultado porque(String nomeMotor, String corpo) {
        return arvore(nomeMotor, corpo, true);
    }

    /**
     * "Porque?" de uma pergunta: corre o pedido e devolve a regra que gerou a pergunta pendente (campos ativo, evento
     * e pergunta) e as regras que usam a resposta. NAO_ENCONTRADO se a pergunta nao esta por responder.
     * @param nomeMotor
     * @param corpo
     * @return
     */
    public Resultado porquePergunta(String nomeMotor, String corpo) {
        return corre(nomeMotor, corpo, requestValidator::validaPergunta, (motor, pedido) -> motor.infere(corpo),
                (pedido, resposta) -> explicador.porquePergunta(resposta, pedido.get("ativo").asString(),
                        pedido.get("evento").asString(), pedido.get("pergunta").asString()));
    }

    /**
     * "Porque nao?": corre o pedido no motor e explica porque o facto pedido (predicado e args, com variaveis) nao se
     * concluiu, ate a profundidade do pedido (4 por omissao). Ao contrario das outras explicacoes, nao se monta a
     * partir da resposta: e o motor que testa as regras contra os factos da sessao. NAO_SUPORTADO num motor que ainda
     * nao o faz.
     * @param nomeMotor
     * @param corpo
     * @return
     */
    public Resultado porqueNao(String nomeMotor, String corpo) {
        return corre(nomeMotor, corpo, requestValidator::validaPorqueNao,
                (motor, pedido) -> motor.porqueNao(corpo, jsonMapper.writeValueAsString(pedido.get("facto")),
                        profundidade(pedido)),
                null);
    }

    /**
     * A profundidade do pedido do "porque nao?", ou a por omissao se nao vier.
     * @param pedido
     * @return
     */
    private static int profundidade(JsonNode pedido) {
        JsonNode p = pedido.get("profundidade");
        return p == null || p.isNull() ? PROFUNDIDADE_POR_OMISSAO : p.asInt();
    }

    /**
     * O como e o porque: a arvore do facto pedido, com ou sem as regras completas.
     * @param nomeMotor
     * @param corpo
     * @param comRegras
     * @return
     */
    private Resultado arvore(String nomeMotor, String corpo, boolean comRegras) {
        return corre(nomeMotor, corpo, requestValidator::validaFacto, (motor, pedido) -> motor.infere(corpo),
                (pedido, resposta) -> explicador.como(resposta, jsonMapper.writeValueAsString(pedido.get("facto")),
                        comRegras));
    }

    /**
     * Verifica que o motor existe e que o pedido e valido (os passos e os campos da explicacao), e so depois chama o
     * motor (executa), com o corpo original. Sem explicacao, o resultado e o do motor; com explicacao, e o que ela
     * monta a partir da resposta, se o motor respondeu sem erro.
     * @param nomeMotor
     * @param corpo
     * @param validaCampos
     * @param executa
     * @param explica
     * @return
     */
    private Resultado corre(String nomeMotor, String corpo, Function<JsonNode, List<String>> validaCampos,
                            BiFunction<MotorInferencia, JsonNode, String> executa,
                            BiFunction<JsonNode, String, String> explica) {
        MotorInferencia motor = motores.get(nomeMotor);
        if (motor == null) {
            String nomes = String.join(", ", motores.keySet().stream().sorted().toList());
            return new Resultado(TipoResultado.MOTOR_INEXISTENTE,
                    erro(nomeMotor, "motor desconhecido: " + nomeMotor + " (disponiveis: " + nomes + ")"));
        }
        JsonNode arvore;
        Pedido pedido;
        try {
            arvore = leCorpo(corpo);
            pedido = lePedido(arvore);
        } catch (PedidoIlegivel e) {
            return new Resultado(TipoResultado.PEDIDO_INVALIDO, erro(nomeMotor, e.getMessage()));
        }

        List<String> erros = new ArrayList<>(requestValidator.validaPedido(pedido, base.versaoKB()));
        erros.addAll(validaCampos.apply(arvore));
        if (!erros.isEmpty()) {
            return new Resultado(TipoResultado.PEDIDO_INVALIDO, erro(nomeMotor, String.join("; ", erros)));
        }
        Resultado resultado = classifica(nomeMotor, executa.apply(motor, arvore));
        if (explica == null || resultado.tipoResultado() != TipoResultado.OK) {
            return resultado;
        }
        return classifica(nomeMotor, explica.apply(arvore, resultado.jsonResultado()));
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
        if (erro.startsWith("nao suportado")) {
            return new Resultado(TipoResultado.NAO_SUPORTADO, resposta);
        }
        if (ERROS_SEM_ALVO.stream().anyMatch(erro::startsWith)) {
            return new Resultado(TipoResultado.NAO_ENCONTRADO, resposta);
        }
        if (ERROS_DO_PEDIDO.stream().anyMatch(erro::startsWith)) {
            return new Resultado(TipoResultado.PEDIDO_INVALIDO, resposta);
        }
        return new Resultado(TipoResultado.FALHA_MOTOR, resposta);
    }

    /**
     * Le o corpo como um objeto JSON. Os erros dizem o que esta mal sem detalhes internos do Jackson (nomes de classes
     * Java, "Source: REDACTED"): sintaxe com a linha e a coluna, ou um JSON que nao e um objeto.
     * @param corpo
     * @return
     */
    private JsonNode leCorpo(String corpo) {
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
        return arvore;
    }

    /**
     * Converte o corpo ja lido em Pedido; um campo com o tipo errado da o caminho dele, sem detalhes do Jackson.
     * @param arvore
     * @return
     */
    private Pedido lePedido(JsonNode arvore) {
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
