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
        assertTrue(new SaudeMotor("x", true, null, null).erros().isEmpty());
    }

    /**
     * Verifica que a lista de erros e uma copia imutavel: mudar a lista original nao muda o estado, e o estado nao
     * se pode mudar por fora.
     */
    @Test
    void errosSaoUmaCopiaImutavel() {
        List<String> original = new ArrayList<>(List.of("a"));
        SaudeMotor e = new SaudeMotor("x", false, null, original);
        original.add("b");
        assertEquals(List.of("a"), e.erros());
        assertThrows(UnsupportedOperationException.class, () -> e.erros().add("c"));
    }
}
