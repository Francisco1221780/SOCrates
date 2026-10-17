package cyberrangers.cyber.api;

import cyberrangers.cyber.domain.TipoResultado;
import org.springframework.http.HttpStatus;

public class HttpResponseUtils {
    public static HttpStatus codigoHttp(TipoResultado tipo) {
        return switch (tipo) {
            case OK -> HttpStatus.OK;
            case MOTOR_INEXISTENTE -> HttpStatus.NOT_FOUND;
            case PEDIDO_INVALIDO -> HttpStatus.BAD_REQUEST;
            case OCUPADO -> HttpStatus.SERVICE_UNAVAILABLE;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case FALHA_MOTOR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
