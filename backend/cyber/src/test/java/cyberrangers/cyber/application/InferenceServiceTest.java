package cyberrangers.cyber.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cyberrangers.cyber.domain.SaudeMotor;
import java.util.List;
import org.junit.jupiter.api.Test;

class InferenceServiceTest {

    /**
     * Cria um motor falso com o nome e o estado dados; o infere nao e usado nestes testes.
     * @param nome
     * @param pronto
     * @return
     */
    private static MotorInferencia falso(String nome, boolean pronto) {
        return new MotorInferencia() {
            @Override
            public String nome() {
                return nome;
            }

            @Override
            public String infere(String pedidoJson) {
                return "{}";
            }

            @Override
            public SaudeMotor estado() {
                return new SaudeMotor(nome, pronto, null, List.of());
            }
        };
    }

    /**
     * Verifica que getMotor devolve o motor com o nome pedido.
     */
    @Test
    void encontraOMotorPeloNome() {
        MotorInferencia prolog = falso("prolog", true);
        InferenceService servico = new InferenceService(List.of(falso("drools", true), prolog));
        assertSame(prolog, servico.getMotor("prolog").orElseThrow());
    }

    /**
     * Verifica que um nome que nenhum motor tem da Optional vazio (na API sera um 404).
     */
    @Test
    void motorDesconhecidoDaVazio() {
        InferenceService servico = new InferenceService(List.of(falso("drools", true)));
        assertTrue(servico.getMotor("xyz").isEmpty());
    }

    /**
     * Verifica que os estados vem ordenados pelo nome do motor, seja qual for a ordem em que o Spring entrega os
     * motores, e que cada estado e o do seu motor.
     */
    @Test
    void estadosVemOrdenadosPeloNome() {
        InferenceService servico = new InferenceService(List.of(falso("prolog", false), falso("drools", true)));
        List<SaudeMotor> estados = servico.estados();
        assertEquals(List.of("drools", "prolog"), estados.stream().map(SaudeMotor::motor).toList());
        assertTrue(estados.get(0).pronto());
        assertFalse(estados.get(1).pronto());
    }

    /**
     * Verifica que dois motores com o mesmo nome impedem o servico de ser criado, em vez de um substituir o outro.
     */
    @Test
    void nomesRepetidosFalhamNoArranque() {
        List<MotorInferencia> repetidos = List.of(falso("drools", true), falso("drools", false));
        assertThrows(IllegalStateException.class, () -> new InferenceService(repetidos));
    }

    /**
     * Verifica que sem motores o servico fica vazio, sem erro.
     */
    @Test
    void semMotoresNaoHaEstados() {
        InferenceService servico = new InferenceService(List.of());
        assertTrue(servico.estados().isEmpty());
        assertTrue(servico.getMotor("drools").isEmpty());
    }
}
