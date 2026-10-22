package socrates.backend.api;

/**
 * Exemplos de pedido para o Swagger UI (OpenAPI), um por endpoint POST. Os controladores recebem o corpo como texto
 * (para o validar e passar ao motor tal como veio), por isso o OpenAPI so mostraria "string": estes exemplos dizem o
 * que enviar. Todos usam o cenario 7 (sensor do host em baixo), que da perguntas ao analista. A versaoKB tem de ser a
 * da base carregada (GET /api/health); estes exemplos sao da base 6.1.
 */
final class ExemplosPedido {

    private ExemplosPedido() {
    }

    /** Os passos do cenario 7, tal como vem no GET /api/cenarios. */
    private static final String PASSO_C7 = """
            {"id": "c7", "factos": [
                {"predicado": "estado_sensor", "args": ["web_srv_04", "agente_host", "offline"]},
                {"predicado": "alerta_ids", "args": ["ev_e1", "web_srv_04", "sig_apache_path_traversal"]},
                {"predicado": "origem", "args": ["ev_e1", "ip_198_51_100_23"]},
                {"predicado": "origem_zona", "args": ["ev_e1", "internet"]},
                {"predicado": "tempo", "args": ["ev_e1", 3000]}]}""";

    /** POST /api/{motor}/infer: o cenario 7; a resposta fica a aguardar as perguntas shell_servico e vulnerabilidade_tratada. */
    static final String INFER = """
            {"versaoKB": "6.1",
             "passos": [
              """ + PASSO_C7 + """
            ]}""";

    /** POST /api/{motor}/infer: o cenario 7 e a resposta do analista a pergunta shell_servico, como um passo novo. */
    static final String INFER_COM_RESPOSTA = """
            {"versaoKB": "6.1",
             "passos": [
              """ + PASSO_C7 + """
            ,
              {"id": "resposta_07", "factos": [
                {"predicado": "resposta_analista", "args": ["web_srv_04", "ev_e1", "shell_servico", "nao"]}]}
            ]}""";

    /** POST /api/{motor}/como: a arvore da pergunta ao analista do cenario 7 (o facto tambem pode ser {"numero": N}). */
    static final String COMO = """
            {"versaoKB": "6.1",
             "passos": [
              """ + PASSO_C7 + """
            ],
             "facto": {"predicado": "pergunta_analista", "args": ["web_srv_04", "ev_e1", "shell_servico"]}}""";

    /** POST /api/{motor}/porque-pergunta: porque se faz a pergunta shell_servico em web_srv_04. */
    static final String PORQUE_PERGUNTA = """
            {"versaoKB": "6.1",
             "passos": [
              """ + PASSO_C7 + """
            ],
             "ativo": "web_srv_04", "evento": "ev_e1", "pergunta": "shell_servico"}""";

    /** POST /api/{motor}/whynot: porque nao se concluiu o compromisso de web_srv_04 (Ev e uma variavel). */
    static final String WHYNOT = """
            {"versaoKB": "6.1",
             "passos": [
              """ + PASSO_C7 + """
            ],
             "facto": {"predicado": "compromisso_confirmado", "args": ["web_srv_04", {"var": "Ev"}]},
             "profundidade": 4}""";
}
