package com.paulfernandosr.possystembackend.sunatfiles.infrastructure;

import com.paulfernandosr.possystembackend.sunatfiles.domain.SunatFileReferences;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class PostgresSunatFileReferences implements SunatFileReferences {
    private final JdbcClient jdbc;
    public PostgresSunatFileReferences(JdbcClient jdbc) { this.jdbc=jdbc; }
    @Override public Set<Context> contexts(String kind,String filename) {
        String column,suffix;
        switch(kind) {
            case "xml"->{column="sunat_xml_path";suffix="/API_SUNAT/files/facturacion_electronica/FIRMA/"+filename;}
            case "cdr"->{column="sunat_cdr_path";suffix="/API_SUNAT/files/facturacion_electronica/CDR/"+filename;}
            default->throw new IllegalArgumentException("Unknown document kind");
        }
        // Table and column names come only from fixed code. A matching basename is insufficient without the proper internal path.
        String sql="SELECT 'SALE' context WHERE EXISTS(SELECT 1 FROM sale WHERE RIGHT("+column+",LENGTH(:suffix))=:suffix) "+
                "OR EXISTS(SELECT 1 FROM credit_note WHERE RIGHT("+column+",LENGTH(:suffix))=:suffix) "+
                "UNION ALL SELECT 'CONTRACT' WHERE EXISTS(SELECT 1 FROM contract_sunat_draft WHERE RIGHT("+column+",LENGTH(:suffix))=:suffix)";
        return new HashSet<>(jdbc.sql(sql).param("suffix",suffix).query((rs,n)->Context.valueOf(rs.getString(1))).list());
    }
}
