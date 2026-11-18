package com.paulfernandosr.possystembackend.sunatfiles.infrastructure;

import com.paulfernandosr.possystembackend.sunatfiles.application.PrivateSunatFileService;
import com.paulfernandosr.possystembackend.sunatfiles.domain.SunatFileException;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/security/sunat-files")
public class PrivateSunatFileRestController {
    private final PrivateSunatFileService files;
    public PrivateSunatFileRestController(PrivateSunatFileService files) { this.files=files; }
    @GetMapping("/{kind}/{filename}") public ResponseEntity<byte[]> download(@PathVariable String kind,@PathVariable String filename) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null||!auth.isAuthenticated()) throw new SunatFileException(SunatFileException.Code.SUNAT_FILE_FORBIDDEN);
        Set<String> permissions=new HashSet<>();auth.getAuthorities().forEach(p->permissions.add(p.getAuthority()));
        byte[] data=files.download(kind,filename,permissions);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(filename,StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","default-src 'none'; sandbox")
                .contentLength(data.length).body(data);
    }
}
