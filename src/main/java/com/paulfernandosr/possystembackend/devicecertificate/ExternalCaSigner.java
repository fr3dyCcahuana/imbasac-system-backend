package com.paulfernandosr.possystembackend.devicecertificate;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import static com.paulfernandosr.possystembackend.devicecertificate.CertificateException.Code.CERTIFICATE_SIGNER_UNAVAILABLE;

/** Fixed privileged helper; no shell, arbitrary executable, key material or caller-selected paths. */
@Component
public class ExternalCaSigner implements CertificateSigner {
    private static final String LINUX_LOCAL="linux-local";
    private static final String WSL_LOCAL_DEMO="wsl-local-demo";
    private static final List<String> LINUX_COMMAND=List.of("/usr/bin/sudo","-n","-u","imbasac-ca","/usr/local/lib/imbasac-ca/sign-request");
    private static final Invocation LINUX_INVOCATION=new Invocation(LINUX_COMMAND,Map.of("PATH","/usr/bin:/bin"),null);
    private static final Pattern ALTERNATE_CSR_PEM=Pattern.compile("\\A-----BEGIN NEW CERTIFICATE REQUEST-----(\\r?\\n[A-Za-z0-9+/=\\r\\n]+)-----END NEW CERTIFICATE REQUEST-----([\\r\\n]*)\\z");
    private final boolean enabled;private final ObjectMapper mapper;private final Invocation invocation;

    @Autowired
    public ExternalCaSigner(@Value("${security.ca-signer.enabled:false}") boolean enabled,
            @Value("${security.ca-signer.transport:linux-local}") String transport,ObjectMapper mapper,Environment environment) {
        this(enabled,mapper,invocationFor(transport,System.getProperty("os.name"),environment.getActiveProfiles(),System.getenv("SystemRoot")));
    }
    public ExternalCaSigner(boolean enabled,ObjectMapper mapper) { this(enabled,mapper,LINUX_INVOCATION); }
    private ExternalCaSigner(boolean enabled,ObjectMapper mapper,Invocation invocation) { this.enabled=enabled;this.mapper=mapper;this.invocation=invocation; }

    record Invocation(List<String> command,Map<String,String> environment,String directory) {}

    static Invocation invocationFor(String transport,String osName,String[] activeProfiles,String systemRoot) {
        if(LINUX_LOCAL.equals(transport)) return LINUX_INVOCATION;
        if(!WSL_LOCAL_DEMO.equals(transport)) throw new IllegalArgumentException("Unsupported CA signer transport");
        var profiles=Arrays.asList(activeProfiles);
        if(osName==null || !osName.toLowerCase(Locale.ROOT).startsWith("windows") || !profiles.contains("local")
                || profiles.contains("prod") || profiles.contains("production")) {
            throw new IllegalArgumentException("The WSL CA signer is restricted to the Windows local demo");
        }
        String root=systemRoot==null ? "" : systemRoot.replace('/','\\');
        if(!root.matches("^[A-Za-z]:\\\\[^\"\\p{Cntrl}]*$")) throw new IllegalArgumentException("Windows system directory is unavailable");
        String systemDirectory=(root.endsWith("\\") ? root : root+"\\")+"System32";
        var command=new ArrayList<>(List.of(systemDirectory+"\\wsl.exe","--distribution","Ubuntu","--user","posapp","--cd","/","--exec"));
        command.addAll(LINUX_COMMAND);
        return new Invocation(List.copyOf(command),Map.of("SystemRoot",root),systemDirectory);
    }
    public boolean isConfigured() { return enabled; }
    record Request(UUID requestId,UUID deviceId,String csrPem) {}
    private record Revocation(String operation,UUID requestId,UUID deviceId,String certificatePem) {}
    static Request signingRequest(UUID requestId,UUID deviceId,String csrPem) {
        // The CA bridge accepts the standard PEM labels; keep the signed payload and line endings intact.
        var alternate=ALTERNATE_CSR_PEM.matcher(csrPem);
        if(alternate.matches()) csrPem="-----BEGIN CERTIFICATE REQUEST-----"+alternate.group(1)+"-----END CERTIFICATE REQUEST-----"+alternate.group(2);
        return new Request(requestId,deviceId,csrPem);
    }
    public String sign(UUID requestId,UUID deviceId,String csrPem) {
        if(!enabled || requestId==null || deviceId==null || csrPem==null || csrPem.length()>16000) throw new CertificateException(CERTIFICATE_SIGNER_UNAVAILABLE);
        var json=execute(signingRequest(requestId,deviceId,csrPem));
        if(json==null || !json.isObject() || !json.path("certificatePem").isTextual()) throw new CertificateException(CERTIFICATE_SIGNER_UNAVAILABLE);
        String pem=json.path("certificatePem").asText();
        if(pem.length()>16000 || !pem.startsWith("-----BEGIN CERTIFICATE-----") || pem.contains("PRIVATE KEY")) throw new CertificateException(CERTIFICATE_SIGNER_UNAVAILABLE);
        return pem;
    }
    @Override public void revoke(UUID requestId,UUID deviceId,String certificatePem) {
        if(!enabled || requestId==null || deviceId==null || certificatePem==null || certificatePem.length()>16000) throw new CertificateException(CERTIFICATE_SIGNER_UNAVAILABLE);
        var json=execute(new Revocation("revoke",requestId,deviceId,certificatePem));
        if(json==null || !json.path("revoked").isBoolean() || !json.path("revoked").asBoolean()) throw new CertificateException(CERTIFICATE_SIGNER_UNAVAILABLE);
    }
    private com.fasterxml.jackson.databind.JsonNode execute(Object request) {
        Process process=null;
        var executor=Executors.newVirtualThreadPerTaskExecutor();
        try {
            byte[] input=mapper.writeValueAsBytes(request);
            if(input.length>20000) throw new IllegalArgumentException();
            var builder=new ProcessBuilder(invocation.command());
            if(invocation.directory()!=null) builder.directory(new File(invocation.directory()));
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);builder.environment().clear();builder.environment().putAll(invocation.environment());
            process=builder.start();Process running=process;
            var output=executor.submit(()->{try(var stdout=running.getInputStream()){return stdout.readNBytes(16001);}});
            var writer=executor.submit(()->{try(var stdin=running.getOutputStream()){stdin.write(input);}return true;});
            if(!process.waitFor(25,TimeUnit.SECONDS)) throw new TimeoutException();
            writer.get(1,TimeUnit.SECONDS);byte[] bytes=output.get(1,TimeUnit.SECONDS);
            if(process.exitValue()!=0 || bytes.length>16000) throw new IllegalStateException();
            return mapper.readTree(bytes);
        } catch(Exception ignored) {
            if(process!=null && process.isAlive()) { process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly(); }
            if(ignored instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new CertificateException(CERTIFICATE_SIGNER_UNAVAILABLE);
        } finally { executor.shutdownNow(); }
    }
}
