package cyberrangers.cyber.api;

import cyberrangers.cyber.domain.TipoResultado;
import org.springframework.http.HttpStatus;

final class HttpResponseUtils {

    private HttpResponseUtils() {}

    static HttpStatus codigoHttp(TipoResultado tipo) {
        return switch (tipo) {
            case OK -> HttpStatus.OK;
            case MOTOR_INEXISTENTE -> HttpStatus.NOT_FOUND;
            case PEDIDO_INVALIDO -> HttpStatus.BAD_REQUEST;
            case NAO_ENCONTRADO -> HttpStatus.UNPROCESSABLE_CONTENT;
            case OCUPADO -> HttpStatus.SERVICE_UNAVAILABLE;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case FALHA_MOTOR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
