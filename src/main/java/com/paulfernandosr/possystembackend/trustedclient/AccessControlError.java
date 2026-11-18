package com.paulfernandosr.possystembackend.trustedclient;
import com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlException;
public record AccessControlError(int status,String code,String error) {
    public static AccessControlError from(AccessControlException exception) {
        boolean unavailable=exception.getCode()==AccessControlException.Code.ACCESS_CONTROL_UNAVAILABLE || exception.getCode()==AccessControlException.Code.ACCESS_CONTROL_DISABLED;
        return new AccessControlError(unavailable?503:403,exception.getCode().name(),unavailable?
                "No se pudo comprobar el estado del acceso. Intenta nuevamente.":
                exception.getCode()==AccessControlException.Code.ACCESS_ACCOUNT_BLOCKED?
                "Tu usuario tiene un bloqueo vigente. Contacta al administrador.":"Esta computadora o sesión no está autorizada para tu usuario.");
    }
}
