package com.paulfernandosr.possystembackend.sunatfiles;

import com.paulfernandosr.possystembackend.sunatfiles.domain.SunatFileReferences.Context;
import com.paulfernandosr.possystembackend.sunatfiles.infrastructure.PostgresSunatFileReferences;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="CERTIFICATES_TEST_JDBC_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/certificates_test")
class PostgresSunatFileReferencesIntegrationTest {
    private DriverManagerDataSource admin;private com.zaxxer.hikari.HikariDataSource data;private JdbcTemplate jdbc;private PostgresSunatFileReferences references;private String schema;
    private final String filename="20500000001-01-F001-00000001.xml";
    @BeforeEach void setup() {
        String url=System.getenv("CERTIFICATES_TEST_JDBC_URL");admin=new DriverManagerDataSource(url,"postgres",System.getenv("CERTIFICATES_TEST_PASSWORD"));
        schema="sunat_files_test_"+UUID.randomUUID().toString().replace("-","");new JdbcTemplate(admin).execute("CREATE SCHEMA "+schema);
        data=new com.zaxxer.hikari.HikariDataSource();data.setJdbcUrl(url+"?currentSchema="+schema);data.setUsername("postgres");data.setPassword(System.getenv("CERTIFICATES_TEST_PASSWORD"));data.setMaximumPoolSize(2);data.setMinimumIdle(0);
        jdbc=new JdbcTemplate(data);for(String table:List.of("sale","credit_note","contract_sunat_draft"))jdbc.execute("CREATE TABLE "+table+"(id BIGINT PRIMARY KEY,sunat_xml_path TEXT,sunat_cdr_path TEXT)");
        jdbc.execute("CREATE TABLE permissions(id BIGSERIAL PRIMARY KEY,name TEXT,domain TEXT,description TEXT)");references=new PostgresSunatFileReferences(JdbcClient.create(data));
    }
    @AfterEach void cleanup() {if(data!=null)data.close();if(schema!=null&&schema.matches("sunat_files_test_[a-f0-9]{32}"))new JdbcTemplate(admin).execute("DROP SCHEMA "+schema+" CASCADE");}
    @Test void saleAndCreditNoteReferencesAllowOnlyTheMatchingFileKind() {
        jdbc.update("INSERT INTO sale VALUES(1,?,NULL)","http://127.0.0.1/API_SUNAT/files/facturacion_electronica/FIRMA/"+filename);
        assertThat(references.contexts("xml",filename)).containsExactly(Context.SALE);assertThat(references.contexts("cdr",filename)).isEmpty();
        jdbc.update("INSERT INTO credit_note VALUES(1,NULL,?)","/API_SUNAT/files/facturacion_electronica/CDR/"+filename);
        assertThat(references.contexts("cdr",filename)).containsExactly(Context.SALE);
    }
    @Test void contractReferencesDoNotBecomeSalesReferencesAndWrongDirectoriesDoNotCount() {
        jdbc.update("INSERT INTO contract_sunat_draft VALUES(1,?,NULL)","/API_SUNAT/files/facturacion_electronica/FIRMA/"+filename);
        jdbc.update("INSERT INTO sale VALUES(1,?,NULL)","/API_SUNAT/files/facturacion_electronica/CDR/"+filename);
        assertThat(references.contexts("xml",filename)).containsExactly(Context.CONTRACT);
    }
    @Test void sqlMetacharactersCannotSelectOtherDocuments() {
        jdbc.update("INSERT INTO sale VALUES(1,?,NULL)","/API_SUNAT/files/facturacion_electronica/FIRMA/"+filename);
        assertThat(references.contexts("xml","' OR TRUE --.xml")).isEmpty();assertThat(references.contexts("xml","%"+filename)).isEmpty();
    }
    @Test void permissionMigrationIsRepeatableAndDoesNotGrantAccess() throws Exception {
        String sql=new String(new ClassPathResource("db/scripts/20261030_private_sunat_files.sql").getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        jdbc.execute(sql);jdbc.execute(sql);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM permissions WHERE name='VIEW_SUNAT_DOCUMENTS'",Integer.class)).isEqualTo(1);
    }
}
