package socrates.backend.application;

/**
 * Monta as explicacoes a partir da resposta de um motor, sem correr nada. E o mesmo para os dois motores, porque as
 * respostas tem o mesmo formato; entra e sai em texto JSON, no formato do contrato.
 */
public interface Explicador {

    /**
     * A arvore de uma conclusao da resposta ("como?"), ou com a regra completa em cada no ("porque?").
     * @param resposta
     * @param facto
     * @param comRegras
     * @return
     */
    String como(String resposta, String facto, boolean comRegras);

    /**
     * A regra que gerou uma pergunta pendente da resposta e as regras que usam a resposta a essa pergunta.
     * @param resposta
     * @param ativo
     * @param evento
     * @param pergunta
     * @return
     */
    String porquePergunta(String resposta, String ativo, String evento, String pergunta);
}
