package com.paulfernandosr.possystembackend.sunatfiles.domain;
import java.util.Set;
public interface SunatFileReferences {
    enum Context { SALE, CONTRACT }
    Set<Context> contexts(String kind,String filename);
}
