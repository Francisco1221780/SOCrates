package cyberrangers.cyber.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.JsonNode;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Facto(String predicado, List<JsonNode> args) { }
