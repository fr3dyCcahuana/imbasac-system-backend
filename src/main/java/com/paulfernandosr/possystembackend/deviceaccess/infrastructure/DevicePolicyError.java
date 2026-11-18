package com.paulfernandosr.possystembackend.deviceaccess.infrastructure;
import com.paulfernandosr.possystembackend.deviceaccess.domain.DevicePolicyException;
public record DevicePolicyError(int status, String code, String error) {
    public static DevicePolicyError from(DevicePolicyException exception) {
        int status=exception.getCode() == DevicePolicyException.Code.DEVICE_POLICY_UNAVAILABLE ? 503 : 403;
        return new DevicePolicyError(status, exception.getCode().name(), exception.getMessage());
    }
}
