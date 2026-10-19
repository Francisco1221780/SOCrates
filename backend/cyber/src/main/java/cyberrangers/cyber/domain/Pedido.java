package cyberrangers.cyber.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Pedido(String versaoKB, List<Passo> passos) { }
