package cyberrangers.cyber.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SaudeMotorTest {

    /**
     * Verifica que erros a null passa a lista vazia, para quem le o estado nunca ter de tratar null.
     */
    @Test
    void errosNullFicaListaVazia() {
        assertTrue(new SaudeMotor("x", true, null, false, Metaconhecimento.DESCONHECIDO, null).erros().isEmpty());
    }

    /**
     * Verifica que a lista de erros e uma copia imutavel: mudar a lista original nao muda o estado, e o estado nao
     * se pode mudar por fora.
     */
    @Test
    void errosSaoUmaCopiaImutavel() {
        List<String> original = new ArrayList<>(List.of("a"));
        SaudeMotor e = new SaudeMotor("x", false, null, false, Metaconhecimento.DESCONHECIDO, original);
        original.add("b");
        assertEquals(List.of("a"), e.erros());
        assertThrows(UnsupportedOperationException.class, () -> e.erros().add("c"));
    }

    /**
     * Verifica que naoPronto devolve o mesmo estado, nao pronto e com o erro acrescentado, sem mudar o original.
     */
    @Test
    void naoProntoAcrescentaOErro() {
        SaudeMotor e = new SaudeMotor("x", true, "5", true, Metaconhecimento.EM_DIA, List.of("a"));
        SaudeMotor n = e.naoPronto("b");
        assertEquals(new SaudeMotor("x", false, "5", true, Metaconhecimento.EM_DIA, List.of("a", "b")), n);
        assertTrue(e.pronto());
        assertEquals(List.of("a"), e.erros());
    }
}
