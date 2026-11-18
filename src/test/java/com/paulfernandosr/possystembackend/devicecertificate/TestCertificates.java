package com.paulfernandosr.possystembackend.devicecertificate;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.*;

/** Test identities only. Keys exist only in memory and are never production credentials. */
public final class TestCertificates {
    private TestCertificates() {}
    public static KeyPair rsa(int size) throws Exception { KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(size);return generator.generateKeyPair(); }
    public static KeyPair ec(String namedCurve) throws Exception {
        KeyPairGenerator generator=KeyPairGenerator.getInstance("EC");generator.initialize(new java.security.spec.ECGenParameterSpec(namedCurve));return generator.generateKeyPair();
    }
    public static X509Certificate ca(KeyPair keys,String name) throws Exception {
        X500Name subject=new X500Name("CN="+name);
        var builder=new JcaX509v3CertificateBuilder(subject,serial(),Date.from(Instant.now().minusSeconds(60)),Date.from(Instant.now().plusSeconds(86400*365)),subject,keys.getPublic());
        builder.addExtension(Extension.basicConstraints,true,new BasicConstraints(0));
        builder.addExtension(Extension.keyUsage,true,new KeyUsage(KeyUsage.keyCertSign|KeyUsage.cRLSign));
        return new JcaX509CertificateConverter().getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(keys.getPrivate())));
    }
    public static X509Certificate leaf(KeyPair keys,X509Certificate issuer,PrivateKey issuerKey,UUID device,boolean clientAuth,boolean includeSan) throws Exception {
        return leaf(keys,issuer,issuerKey,includeSan?"urn:imbasac:device:"+device:null,clientAuth);
    }
    public static X509Certificate leaf(KeyPair keys,X509Certificate issuer,PrivateKey issuerKey,String sanUri,boolean clientAuth) throws Exception {
        var builder=new JcaX509v3CertificateBuilder(issuer,serial(),Date.from(Instant.now().minusSeconds(60)),Date.from(Instant.now().plusSeconds(86400*30)),new X500Name("CN=Test device"),keys.getPublic());
        builder.addExtension(Extension.basicConstraints,true,new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage,true,new KeyUsage(KeyUsage.digitalSignature));
        builder.addExtension(Extension.extendedKeyUsage,false,new ExtendedKeyUsage(clientAuth?KeyPurposeId.id_kp_clientAuth:KeyPurposeId.id_kp_serverAuth));
        if(sanUri!=null) builder.addExtension(Extension.subjectAlternativeName,false,new GeneralNames(new GeneralName(GeneralName.uniformResourceIdentifier,sanUri)));
        return new JcaX509CertificateConverter().getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(issuerKey)));
    }
    public static String csr(KeyPair keys) throws Exception { return csr(keys,keys.getPrivate()); }
    public static String csr(KeyPair keys,PrivateKey signerKey) throws Exception {
        return pem(new JcaPKCS10CertificationRequestBuilder(new X500Name("CN=Untrusted subject ignored by signer"),keys.getPublic())
                .build(new JcaContentSignerBuilder("EC".equals(signerKey.getAlgorithm())?"SHA256withECDSA":"SHA256withRSA").build(signerKey)));
    }
    public static String pem(Object object) throws Exception { StringWriter text=new StringWriter();try(var writer=new JcaPEMWriter(text)){writer.writeObject(object);}return text.toString(); }
    private static BigInteger serial() { return new BigInteger(159,new SecureRandom()).add(BigInteger.ONE); }
}
