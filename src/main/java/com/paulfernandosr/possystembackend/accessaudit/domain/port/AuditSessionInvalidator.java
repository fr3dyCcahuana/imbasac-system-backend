package com.paulfernandosr.possystembackend.accessaudit.domain.port;
public interface AuditSessionInvalidator { void invalidate(String accessSessionKey, String username); }
