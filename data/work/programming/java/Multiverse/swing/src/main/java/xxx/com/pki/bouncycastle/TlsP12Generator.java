package xxx.com.pki.bouncycastle;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PKCS8Generator;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8EncryptorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OutputEncryptor;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * A utility class to generate in-memory PKCS#12 (P12) keystores for a client and server for use in
 * mTLS communication. It creates a full certificate chain: Root CA -> Intermediate CA -> End-Entity
 * Certificate. Requires BouncyCastle (bcprov-jdk18on and bcpkix-jdk18on) v1.78 or higher.
 */
public class TlsP12Generator {

  /** A simple data container class to hold the generated Public Key Infrastructure components. */
  public record PKIContainer(
      KeyPair rootCaKeyPair,
      X509Certificate rootCaCert,
      KeyPair intermediateCaKeyPair,
      X509Certificate intermediateCaCert,
      KeyPair serverKeyPair,
      X509Certificate serverCert,
      KeyPair clientKeyPair,
      X509Certificate clientCert) {

    public Certificate[] getServerCertificateChain() {
      return new Certificate[] {this.serverCert, this.intermediateCaCert, this.rootCaCert};
    }

    public Certificate[] getClientCertificateChain() {
      return new Certificate[] {this.clientCert, this.intermediateCaCert, this.rootCaCert};
    }
  }

  /** A simple data container to hold components extracted from a P12 file. */
  public record PKIXExtractor(
      PrivateKey privateKey,
      PublicKey publicKey,
      X509Certificate publicCertificate,
      List<X509Certificate> certificateAuthority) {

    /**
     * Exports the contained PKI components to PEM-formatted files in a specified directory.
     *
     * @param password The password to encrypt the private key.
     * @param filepath The path to the directory where PEM files will be saved.
     */
    public void pemExporter(String password, String filepath) throws Exception {
      Path dir = Paths.get(filepath);
      Files.createDirectories(dir);

      // 1. Export the encrypted Private Key
      try (JcaPEMWriter pemWriter =
          new JcaPEMWriter(new FileWriter(dir.resolve("private_key.pem").toFile()))) {
        JceOpenSSLPKCS8EncryptorBuilder encryptorBuilder =
            new JceOpenSSLPKCS8EncryptorBuilder(PKCS8Generator.AES_256_CBC);
        encryptorBuilder.setProvider(BouncyCastleProvider.PROVIDER_NAME);
        encryptorBuilder.setPassword(password.toCharArray());
        OutputEncryptor encryptor = encryptorBuilder.build();
        JcaPKCS8Generator pkcs8Generator = new JcaPKCS8Generator(this.privateKey, encryptor);
        pemWriter.writeObject(pkcs8Generator.generate());
      }

      // 2. Export the Public Key
      try (JcaPEMWriter pemWriter =
          new JcaPEMWriter(new FileWriter(dir.resolve("public_key.pem").toFile()))) {
        pemWriter.writeObject(this.publicKey);
      }

      // 3. Export the Public Certificate
      try (JcaPEMWriter pemWriter =
          new JcaPEMWriter(new FileWriter(dir.resolve("certificate.pem").toFile()))) {
        pemWriter.writeObject(this.publicCertificate);
      }

      // 4. Export the Certificate Authority chain
      for (int i = 0; i < this.certificateAuthority.size(); i++) {
        String filename = String.format("ca_%d.pem", i);
        try (JcaPEMWriter pemWriter =
            new JcaPEMWriter(new FileWriter(dir.resolve(filename).toFile()))) {
          pemWriter.writeObject(this.certificateAuthority.get(i));
        }
      }
    }
  }

  private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
  private static final String KEY_GENERATION_ALGORITHM = "RSA";
  private static final int KEY_SIZE = 2048;

  static {
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
  }

  /**
   * Generates a pair of P12 keystores (one for a server, one for a client) for mTLS.
   *
   * @param serverCn The Common Name (and SAN DNS Name) for the server certificate.
   * @param clientCn The Common Name (and SAN DNS Name) for the client certificate.
   * @param password The password to protect both generated P12 keystores.
   * @return A Map containing the byte arrays of the P12 files.
   * @throws Exception if any error occurs during key or certificate generation.
   */
  public Map<String, byte[]> generateP12Keystores(String serverCn, String clientCn, String password)
      throws Exception {
    // 1. Generate all the PKI components
    PKIContainer pkiContainer = generatePKI(serverCn, clientCn);

    // 2. Create P12 Keystores from the generated components
    byte[] serverP12 =
        createP12(
            "server-key",
            pkiContainer.serverKeyPair.getPrivate(),
            pkiContainer.getServerCertificateChain(),
            password);

    byte[] clientP12 =
        createP12(
            "client-key",
            pkiContainer.clientKeyPair.getPrivate(),
            pkiContainer.getClientCertificateChain(),
            password);

    // 3. Return results in a map
    Map<String, byte[]> keystores = new HashMap<>();
    keystores.put("server.p12", serverP12);
    keystores.put("client.p12", clientP12);

    return keystores;
  }

  /**
   * Extracts the key and certificate components from a P12 byte array.
   *
   * @param p12Bytes The byte array of the P12 file.
   * @param password The password for the P12 file.
   * @return A PKIXExtractor object containing the extracted components.
   * @throws Exception if the P12 file cannot be parsed or if the key entry is not found.
   */
  public static PKIXExtractor extractP12(byte[] p12Bytes, String password) throws Exception {
    KeyStore p12Store = KeyStore.getInstance("PKCS12", BouncyCastleProvider.PROVIDER_NAME);
    p12Store.load(new ByteArrayInputStream(p12Bytes), password.toCharArray());

    Enumeration<String> aliases = p12Store.aliases();
    String keyAlias = null;

    while (aliases.hasMoreElements()) {
      String alias = aliases.nextElement();
      if (p12Store.isKeyEntry(alias)) {
        keyAlias = alias;
        break;
      }
    }

    if (keyAlias == null) {
      throw new KeyStoreException("Could not find private key entry in the P12 file.");
    }

    PrivateKey privateKey = (PrivateKey) p12Store.getKey(keyAlias, password.toCharArray());
    Certificate[] chain = p12Store.getCertificateChain(keyAlias);
    if (chain == null || chain.length == 0) {
      throw new KeyStoreException("Certificate chain not found for the private key entry.");
    }

    X509Certificate publicCertificate = (X509Certificate) chain[0];
    PublicKey publicKey = publicCertificate.getPublicKey();
    List<X509Certificate> certificateAuthority = new ArrayList<>();
    for (int i = 1; i < chain.length; i++) {
      certificateAuthority.add((X509Certificate) chain[i]);
    }

    return new PKIXExtractor(privateKey, publicKey, publicCertificate, certificateAuthority);
  }

  /**
   * Generates all the necessary key pairs and certificates for a complete mTLS PKI setup.
   *
   * @param serverCn The Common Name to use for the server certificate.
   * @param clientCn The Common Name to use for the client certificate.
   * @return A PKIContainer object holding all the generated components.
   * @throws Exception if any error occurs during generation.
   */
  public PKIContainer generatePKI(String serverCn, String clientCn) throws Exception {
    // 1. Create Root CA
    KeyPair rootCaKeyPair = generateKeyPair();
    X500Name rootCaDn = createDn("My Corp Root CA");
    X509Certificate rootCaCert = createRootCaCertificate(rootCaDn, rootCaKeyPair, 10);

    // 2. Create Intermediate CA, signed by Root CA
    KeyPair intermediateCaKeyPair = generateKeyPair();
    X500Name intermediateCaDn = createDn("My Corp Intermediate CA");
    X509Certificate intermediateCaCert =
        createIntermediateCaCertificate(
            intermediateCaDn,
            intermediateCaKeyPair,
            rootCaDn,
            rootCaCert,
            rootCaKeyPair.getPrivate(),
            5);

    // 3. Create Server Certificate, signed by Intermediate CA
    KeyPair serverKeyPair = generateKeyPair();
    X500Name serverDn = createDn(serverCn);
    X509Certificate serverCert =
        createEndEntityCertificate(
            serverDn,
            serverCn, // Use serverCn for SAN
            serverKeyPair,
            intermediateCaDn,
            intermediateCaCert,
            intermediateCaKeyPair.getPrivate(),
            true // isServer = true
            );

    // 4. Create Client Certificate, signed by Intermediate CA
    KeyPair clientKeyPair = generateKeyPair();
    X500Name clientDn = createDn(clientCn);
    X509Certificate clientCert =
        createEndEntityCertificate(
            clientDn,
            clientCn, // Use clientCn for SAN
            clientKeyPair,
            intermediateCaDn,
            intermediateCaCert,
            intermediateCaKeyPair.getPrivate(),
            false // isServer = false
            );

    return new PKIContainer(
        rootCaKeyPair, rootCaCert,
        intermediateCaKeyPair, intermediateCaCert,
        serverKeyPair, serverCert,
        clientKeyPair, clientCert);
  }

  private KeyPair generateKeyPair() throws NoSuchAlgorithmException {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KEY_GENERATION_ALGORITHM);
    keyPairGenerator.initialize(KEY_SIZE);
    return keyPairGenerator.generateKeyPair();
  }

  private X500Name createDn(String cn) {
    X500NameBuilder builder = new X500NameBuilder(BCStyle.INSTANCE);
    builder.addRDN(BCStyle.C, "US");
    builder.addRDN(BCStyle.ST, "Maryland");
    builder.addRDN(BCStyle.L, "Baltimore");
    builder.addRDN(BCStyle.O, "My Corporation");
    builder.addRDN(BCStyle.OU, "IT Department");
    builder.addRDN(BCStyle.CN, cn);
    return builder.build();
  }

  private X509Certificate createRootCaCertificate(
      X500Name subjectDn, KeyPair keyPair, int validityYears) throws Exception {
    Instant now = Instant.now();
    Date notBefore = Date.from(now);
    Date notAfter = Date.from(now.plus(365L * validityYears, ChronoUnit.DAYS));
    BigInteger serial = new BigInteger(128, new SecureRandom());

    X509v3CertificateBuilder certBuilder =
        new JcaX509v3CertificateBuilder(
            subjectDn, serial, notBefore, notAfter, subjectDn, keyPair.getPublic());

    JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
    certBuilder.addExtension(
        Extension.subjectKeyIdentifier,
        false,
        extUtils.createSubjectKeyIdentifier(keyPair.getPublic()));
    certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
    certBuilder.addExtension(
        Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));

    ContentSigner signer =
        new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(keyPair.getPrivate());
    X509CertificateHolder certHolder = certBuilder.build(signer);

    return new JcaX509CertificateConverter()
        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
        .getCertificate(certHolder);
  }

  private X509Certificate createIntermediateCaCertificate(
      X500Name subjectDn,
      KeyPair subjectKeyPair,
      X500Name issuerDn,
      X509Certificate issuerCert,
      PrivateKey issuerPrivateKey,
      int validityYears)
      throws Exception {
    Instant now = Instant.now();
    Date notBefore = Date.from(now);
    Date notAfter = Date.from(now.plus(365L * validityYears, ChronoUnit.DAYS));
    BigInteger serial = new BigInteger(128, new SecureRandom());

    X509v3CertificateBuilder certBuilder =
        new JcaX509v3CertificateBuilder(
            issuerDn, serial, notBefore, notAfter, subjectDn, subjectKeyPair.getPublic());

    JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
    certBuilder.addExtension(
        Extension.subjectKeyIdentifier,
        false,
        extUtils.createSubjectKeyIdentifier(subjectKeyPair.getPublic()));
    certBuilder.addExtension(
        Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(issuerCert));
    certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
    certBuilder.addExtension(
        Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));

    ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(issuerPrivateKey);
    X509CertificateHolder certHolder = certBuilder.build(signer);

    return new JcaX509CertificateConverter()
        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
        .getCertificate(certHolder);
  }

  private X509Certificate createEndEntityCertificate(
      X500Name subjectDn,
      String sanName, // Subject Alternative Name (e.g., DNS name)
      KeyPair subjectKeyPair,
      X500Name issuerDn,
      X509Certificate issuerCert,
      PrivateKey issuerPrivateKey,
      boolean isServer)
      throws Exception {
    Instant now = Instant.now();
    Date notBefore = Date.from(now);
    Date notAfter = Date.from(now.plus(365, ChronoUnit.DAYS));
    BigInteger serial = new BigInteger(128, new SecureRandom());

    X509v3CertificateBuilder certBuilder =
        new JcaX509v3CertificateBuilder(
            issuerDn, serial, notBefore, notAfter, subjectDn, subjectKeyPair.getPublic());

    JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
    certBuilder.addExtension(
        Extension.subjectKeyIdentifier,
        false,
        extUtils.createSubjectKeyIdentifier(subjectKeyPair.getPublic()));
    certBuilder.addExtension(
        Extension.authorityKeyIdentifier, false, extUtils.createAuthorityKeyIdentifier(issuerCert));
    certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
    certBuilder.addExtension(
        Extension.keyUsage,
        true,
        new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));

    // ** CRITICAL FIX: Add Subject Alternative Name (SAN) **
    GeneralNames subjectAltName = new GeneralNames(new GeneralName(GeneralName.dNSName, sanName));
    certBuilder.addExtension(Extension.subjectAlternativeName, false, subjectAltName);

    KeyPurposeId[] keyPurposeIds;
    if (isServer) {
      keyPurposeIds = new KeyPurposeId[] {KeyPurposeId.id_kp_serverAuth};
    } else {
      keyPurposeIds = new KeyPurposeId[] {KeyPurposeId.id_kp_clientAuth};
    }
    certBuilder.addExtension(
        Extension.extendedKeyUsage, false, new ExtendedKeyUsage(keyPurposeIds));

    ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(issuerPrivateKey);
    X509CertificateHolder certHolder = certBuilder.build(signer);

    return new JcaX509CertificateConverter()
        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
        .getCertificate(certHolder);
  }

  private byte[] createP12(
      String alias, PrivateKey privateKey, Certificate[] chain, String password)
      throws KeyStoreException,
          IOException,
          NoSuchAlgorithmException,
          CertificateException,
          NoSuchProviderException {
    KeyStore keyStore = KeyStore.getInstance("PKCS12", BouncyCastleProvider.PROVIDER_NAME);
    keyStore.load(null, null);
    keyStore.setKeyEntry(alias, privateKey, password.toCharArray(), chain);

    try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
      keyStore.store(bos, password.toCharArray());
      return bos.toByteArray();
    }
  }

  /** Main method for demonstration purposes. */
  public static void main(String[] args) {
    try {
      TlsP12Generator generator = new TlsP12Generator();
      String serverCn = "mtls.example.com";
      String clientCn = "client-app-1.example.com";
      String password = "changeit";

      System.out.println("Generating P12 keystores for:");
      System.out.println(" - Server CN: " + serverCn);
      System.out.println(" - Client CN: " + clientCn);
      Map<String, byte[]> keystores = generator.generateP12Keystores(serverCn, clientCn, password);

      byte[] serverP12 = keystores.get("server.p12");
      byte[] clientP12 = keystores.get("client.p12");

      System.out.println("\nSuccessfully generated keystores in memory.");
      System.out.println(" - Server P12 size: " + serverP12.length + " bytes.");
      System.out.println(" - Client P12 size: " + clientP12.length + " bytes.");

      System.out.println("\nExtracting components from server.p12...");
      PKIXExtractor extractor = TlsP12Generator.extractP12(serverP12, password);
      System.out.println("Successfully extracted components into PKIXExtractor object.");
      System.out.println(
          " - Public Certificate Subject: "
              + extractor.publicCertificate.getSubjectX500Principal());
      System.out.println(
          " - Public Certificate SAN: " + extractor.publicCertificate.getSubjectAlternativeNames());
      System.out.println(" - Number of CAs in chain: " + extractor.certificateAuthority.size());

      System.out.println("\nExporting extracted components to PEM files...");
      Path tempDir = Files.createTempDirectory("pki-export-");
      String exportPassword = "export-password";
      extractor.pemExporter(exportPassword, tempDir.toString());
      System.out.println(
          "Successfully exported PEM files to temporary directory: " + tempDir.toAbsolutePath());

    } catch (Exception e) {
      System.err.println("An error occurred during P12 generation: " + e.getMessage());
    }
  }
}
