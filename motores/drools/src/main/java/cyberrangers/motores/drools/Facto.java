package cyberrangers.motores.drools;

import java.util.List;

/** Um facto genérico: predicado + argumentos. */
public class Facto {

    private String predicado;
    private List<Object> args;

    public Facto() {
    }

    public Facto(String predicado, List<Object> args) {
        this.predicado = predicado;
        this.args = args;
    }

    public String getPredicado() {
        return predicado;
    }

    public void setPredicado(String predicado) {
        this.predicado = predicado;
    }

    public List<Object> getArgs() {
        return args;
    }

    public void setArgs(List<Object> args) {
        this.args = args;
    }
}
