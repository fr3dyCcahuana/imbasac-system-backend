package com.paulfernandosr.possystembackend.sunatfiles.domain;
public class SunatFileException extends RuntimeException {
    public enum Code { SUNAT_FILES_DISABLED,SUNAT_FILE_INVALID,SUNAT_FILE_FORBIDDEN,SUNAT_FILE_NOT_FOUND,SUNAT_FILE_TOO_LARGE,SUNAT_FILE_UNAVAILABLE }
    private final Code code;
    public SunatFileException(Code code) { super(code.name());this.code=code; }
    public Code getCode() { return code; }
}
