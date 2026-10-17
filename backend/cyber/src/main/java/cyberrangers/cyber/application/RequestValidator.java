package cyberrangers.cyber.application;

import cyberrangers.cyber.domain.Facto;
import cyberrangers.cyber.domain.Passo;
import cyberrangers.cyber.domain.Pedido;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Valida a forma de um pedido antes de chegar a um motor (AC17): versaoKB igual a carregada, passos nao vazios e
 * factos com predicado e args. Os termos dentro de args sao validados pelos motores, com as mesmas mensagens nos dois.
 */
@Component
public class RequestValidator {

    /**
     * Devolve todos os erros do pedido de uma vez, com a posicao (a contar de 1) do passo e do facto; vazia se o
     * pedido for valido.
     * @param pedido
     * @param versaoCarregada
     * @return
     */
    public List<String> validaPedido(Pedido pedido, String versaoCarregada) {
        if (pedido == null) {
            return List.of("o pedido esta vazio");
        }
        List<String> erros = new ArrayList<>();
        if (pedido.versaoKB() == null || pedido.versaoKB().isBlank()) {
            erros.add("falta a versaoKB");
        } else if (!pedido.versaoKB().equals(versaoCarregada)) {
            erros.add("versaoKB desconhecida: " + pedido.versaoKB() + " (carregada: " + versaoCarregada + ")");
        }
        List<Passo> passos = pedido.passos();
        if (passos == null || passos.isEmpty()) {
            erros.add("o pedido nao tem passos");
            return erros;
        }
        for (int i = 0; i < passos.size(); i++) {
            validaPasso(passos.get(i), "passo " + (i + 1), erros);
        }
        return erros;
    }

    /**
     * Acrescenta aos erros o que falta no passo e nos seus factos.
     * @param passo
     * @param onde
     * @param erros
     */
    private static void validaPasso(Passo passo, String onde, List<String> erros) {
        if (passo == null) {
            erros.add(onde + ": o passo esta vazio");
            return;
        }
        List<Facto> factos = passo.factos();
        if (factos == null) {
            erros.add(onde + ": falta a lista de factos");
            return;
        }
        for (int j = 0; j < factos.size(); j++) {
            Facto facto = factos.get(j);
            String aqui = onde + ", facto " + (j + 1);
            if (facto == null) {
                erros.add(aqui + ": o facto esta vazio");
                continue;
            }
            if (facto.predicado() == null || facto.predicado().isBlank()) {
                erros.add(aqui + ": falta o predicado");
            }
            if (facto.args() == null) {
                erros.add(aqui + ": falta args");
            }
        }
    }
}
