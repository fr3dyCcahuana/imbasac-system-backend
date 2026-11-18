package com.paulfernandosr.possystembackend.sunatfiles;

import com.paulfernandosr.possystembackend.sunatfiles.application.PrivateSunatFileService;
import com.paulfernandosr.possystembackend.sunatfiles.domain.*;
import com.paulfernandosr.possystembackend.sunatfiles.domain.SunatFileReferences.Context;
import com.paulfernandosr.possystembackend.sunatfiles.infrastructure.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.io.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.paulfernandosr.possystembackend.sunatfiles.domain.SunatFileException.Code.*;

class PrivateSunatFileTest {
    @TempDir Path temp;
    private final SunatFileReferences references=mock(SunatFileReferences.class);
    private final Set<String> salePermissions=Set.of("VIEW_SUNAT_DOCUMENTS","MANAGE_SALES_LIST");
    private PrivateSunatFileService service;
    private Path xml,cdr;
    private final String name="20500000001-01-F001-00000001.xml";
    @BeforeEach void setup() throws Exception {
        xml=Files.createDirectory(temp.resolve("xml"));cdr=Files.createDirectory(temp.resolve("cdr"));
        service=new PrivateSunatFileService(references,true,xml.toString(),cdr.toString());
    }
    @AfterEach void clear() {SecurityContextHolder.clearContext();}
    @Test void permissionsAreCheckedBeforeReferencesOrFilesystem() {
        assertCode(()->service.download("xml",name,Set.of("MANAGE_SALES_LIST")),SUNAT_FILE_FORBIDDEN);
        assertCode(()->service.download("xml",name,Set.of("VIEW_SUNAT_DOCUMENTS")),SUNAT_FILE_FORBIDDEN);
        verifyNoInteractions(references);
    }
    @Test void disabledFeatureHasNoPublicFallbackOrReferenceQuery() {
        var disabled=new PrivateSunatFileService(references,false,"not-configured","not-configured");
        assertCode(()->disabled.download("xml",name,salePermissions),SUNAT_FILES_DISABLED);verifyNoInteractions(references);
    }
    @ParameterizedTest @ValueSource(strings={"../secret.xml","..\\secret.xml","%2e%2e.xml","%252e.xml","/file.xml","file.xml?token=secret","file.xml#fragment","file\r\n.xml","archivo-ñ.xml","file.txt","foo..xml","file%20.xml"})
    void traversalEncodingAndNonBasenamesAreRejectedBeforeTheDatabase(String filename) {
        assertCode(()->service.download("xml",filename,salePermissions),SUNAT_FILE_INVALID);verifyNoInteractions(references);
    }
    @Test void unknownKindAndOverlongNameAreRejected() {
        assertCode(()->service.download("pdf",name,salePermissions),SUNAT_FILE_INVALID);
        assertCode(()->service.download("xml","a".repeat(201)+".xml",salePermissions),SUNAT_FILE_INVALID);verifyNoInteractions(references);
    }
    @Test void physicalFileWithoutAnErpReferenceIsNeverDownloaded() throws Exception {
        Files.writeString(xml.resolve(name),"private fixture");when(references.contexts("xml",name)).thenReturn(Set.of());
        assertCode(()->service.download("xml",name,salePermissions),SUNAT_FILE_NOT_FOUND);
    }
    @Test void SalesPermissionCannotReadAContractOnlyReferenceAndViceVersa() {
        when(references.contexts("xml",name)).thenReturn(Set.of(Context.CONTRACT));
        assertCode(()->service.download("xml",name,salePermissions),SUNAT_FILE_FORBIDDEN);
        when(references.contexts("xml",name)).thenReturn(Set.of(Context.SALE));
        assertCode(()->service.download("xml",name,Set.of("VIEW_SUNAT_DOCUMENTS","MANAGE_CONTRACTS_LIST")),SUNAT_FILE_FORBIDDEN);
    }
    @Test void fileKindSelectsOnlyItsConfiguredDirectory() throws Exception {
        when(references.contexts(anyString(),eq(name))).thenReturn(Set.of(Context.SALE));
        Files.writeString(xml.resolve(name),"<invoice/>");Files.writeString(cdr.resolve(name),"<response/>");
        assertThat(new String(service.download("xml",name,salePermissions),java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("<invoice/>");
        assertThat(new String(service.download("cdr",name,salePermissions),java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("<response/>");
    }
    @Test void missingFileAndLostDatabaseAreSanitized() {
        when(references.contexts("xml",name)).thenReturn(Set.of(Context.SALE));
        assertCode(()->service.download("xml",name,salePermissions),SUNAT_FILE_NOT_FOUND);
        when(references.contexts("xml",name)).thenThrow(new DataAccessResourceFailureException("secret database address"));
        assertCode(()->service.download("xml",name,salePermissions),SUNAT_FILE_UNAVAILABLE);
    }
    @Test void oversizedDocumentsAreRejectedBeforeAllocatingTheirContents() throws Exception {
        when(references.contexts("xml",name)).thenReturn(Set.of(Context.SALE));
        try(var file=new RandomAccessFile(xml.resolve(name).toFile(),"rw")) {file.setLength(PrivateSunatFileService.MAX_BYTES+1L);}
        assertCode(()->service.download("xml",name,salePermissions),SUNAT_FILE_TOO_LARGE);
    }
    @Test void symbolicLinksCannotEscapeTheConfiguredDirectory() throws Exception {
        when(references.contexts("xml",name)).thenReturn(Set.of(Context.SALE));Path link=xml.resolve(name);
        Path outside=Files.writeString(temp.resolve("outside.xml"),"outside configured directory");
        try {Files.createSymbolicLink(link,outside);assertCode(()->service.download("xml",name,salePermissions),SUNAT_FILE_NOT_FOUND);}
        catch(UnsupportedOperationException|java.nio.file.FileSystemException unavailable) {
            // Windows may deny symlink creation. Exercise the same NOFOLLOW attributes branch without requiring administrator changes.
            var attributes=mock(BasicFileAttributes.class);when(attributes.isSymbolicLink()).thenReturn(true);
            try(var files=mockStatic(Files.class,CALLS_REAL_METHODS)) {
                files.when(()->Files.readAttributes(link,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS)).thenReturn(attributes);
                assertCode(()->service.download("xml",name,salePermissions),SUNAT_FILE_NOT_FOUND);
            }
        }
    }
    @Test void successfulHttpDownloadIsAnAttachmentAndCannotBeCachedOrSniffed() throws Exception {
        when(references.contexts("xml",name)).thenReturn(Set.of(Context.SALE));Files.writeString(xml.resolve(name),"<invoice/>");permissions(salePermissions);
        mvc().perform(get("/security/sunat-files/xml/"+name)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("attachment;")))
                .andExpect(content().contentType("application/xml")).andExpect(content().string("<invoice/>"));
    }
    @Test void authenticatedUserWithoutDocumentPermissionCannotUseTheController() throws Exception {
        permissions(Set.of("MANAGE_SALES_LIST"));mvc().perform(get("/security/sunat-files/xml/"+name)).andExpect(status().isForbidden());verifyNoInteractions(references);
    }
    @Test void privateErrorsNeverContainServerPaths() throws Exception {
        permissions(salePermissions);when(references.contexts("xml",name)).thenReturn(Set.of(Context.SALE));
        mvc().perform(get("/security/sunat-files/xml/"+name)).andExpect(status().isNotFound()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(temp.toString()))));
    }
    private MockMvc mvc() {return MockMvcBuilders.standaloneSetup(new PrivateSunatFileRestController(service)).setControllerAdvice(new PrivateSunatFileExceptionHandler()).build();}
    private static void permissions(Set<String> values) {SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin",null,values.stream().map(SimpleGrantedAuthority::new).toList()));}
    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action,SunatFileException.Code code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(SunatFileException.class,e->assertThat(e.getCode()).isEqualTo(code));
    }
}
