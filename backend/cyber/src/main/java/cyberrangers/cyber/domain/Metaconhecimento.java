package cyberrangers.cyber.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Se o metaconhecimento de um motor (o indice facto_dispara_regras/2 do soc_suporte.txt) esta de acordo com as regras
 * (AC13). Um motor que nao usa esse indice (o Drools, que tem o Rete) diz nao_aplicavel.
 */
public enum Metaconhecimento {
    @JsonProperty("em_dia") EM_DIA,
    @JsonProperty("desatualizado") DESATUALIZADO,
    @JsonProperty("desconhecido") DESCONHECIDO,
    @JsonProperty("nao_aplicavel") NAO_APLICAVEL
}
