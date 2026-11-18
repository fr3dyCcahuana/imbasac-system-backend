package com.paulfernandosr.possystembackend.sunatfiles.application;

import com.paulfernandosr.possystembackend.sunatfiles.domain.*;
import com.paulfernandosr.possystembackend.sunatfiles.domain.SunatFileReferences.Context;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.channels.Channels;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import static com.paulfernandosr.possystembackend.sunatfiles.domain.SunatFileException.Code.*;

@Service
public class PrivateSunatFileService {
    public static final int MAX_BYTES=16*1024*1024;
    private final SunatFileReferences references;
    private final boolean enabled;
    private final Path xmlDirectory,cdrDirectory;
    public PrivateSunatFileService(SunatFileReferences references,@Value("${security.sunat-files.enabled:false}") boolean enabled,
            @Value("${security.sunat-files.xml-directory:/var/www/apache/API_SUNAT/files/facturacion_electronica/FIRMA}") String xmlDirectory,
            @Value("${security.sunat-files.cdr-directory:/var/www/apache/API_SUNAT/files/facturacion_electronica/CDR}") String cdrDirectory) {
        this.references=references;this.enabled=enabled;this.xmlDirectory=Path.of(xmlDirectory);this.cdrDirectory=Path.of(cdrDirectory);
    }
    public byte[] download(String kind,String filename,Set<String> permissions) {
        if(permissions==null||!permissions.contains("VIEW_SUNAT_DOCUMENTS")||
                !(permissions.contains("MANAGE_SALES_LIST")||permissions.contains("MANAGE_CONTRACTS_LIST"))) fail(SUNAT_FILE_FORBIDDEN);
        if(!enabled) fail(SUNAT_FILES_DISABLED);
        if(kind==null||!Set.of("xml","cdr").contains(kind)||filename==null||filename.length()>200||filename.contains("..")||
                !filename.matches("[A-Za-z0-9][A-Za-z0-9_.-]*\\.xml")) fail(SUNAT_FILE_INVALID);
        Set<Context> contexts;
        try { contexts=references.contexts(kind,filename); }
        catch(DataAccessException ignored) { throw new SunatFileException(SUNAT_FILE_UNAVAILABLE); }
        if(contexts.isEmpty()) fail(SUNAT_FILE_NOT_FOUND);
        if(!(contexts.contains(Context.SALE)&&permissions.contains("MANAGE_SALES_LIST"))&&
                !(contexts.contains(Context.CONTRACT)&&permissions.contains("MANAGE_CONTRACTS_LIST"))) fail(SUNAT_FILE_FORBIDDEN);
        try {
            Path base=("xml".equals(kind)?xmlDirectory:cdrDirectory).toRealPath();
            Path file=base.resolve(filename).normalize();
            if(!file.startsWith(base)||!file.getParent().equals(base)) fail(SUNAT_FILE_INVALID);
            BasicFileAttributes attributes=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(!attributes.isRegularFile()||attributes.isSymbolicLink()) fail(SUNAT_FILE_NOT_FOUND);
            if(attributes.size()>MAX_BYTES) fail(SUNAT_FILE_TOO_LARGE);
            try(var channel=Files.newByteChannel(file,Set.of(StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS));
                InputStream input=Channels.newInputStream(channel)) {
                byte[] bytes=input.readNBytes(MAX_BYTES+1);
                if(bytes.length>MAX_BYTES) fail(SUNAT_FILE_TOO_LARGE);
                return bytes;
            }
        } catch(NoSuchFileException ignored) { throw new SunatFileException(SUNAT_FILE_NOT_FOUND); }
        catch(IOException|SecurityException ignored) { throw new SunatFileException(SUNAT_FILE_UNAVAILABLE); }
    }
    private static void fail(SunatFileException.Code code) { throw new SunatFileException(code); }
}
