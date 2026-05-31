package com.xxx.server.web.tls;

import android.content.Context;
import android.util.Log;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

public class TLSCertificateManager {

    private static final String TAG = "TLSCertManager";
    private static final String KEYSTORE_FILE = "server_keystore.p12";
    public static final String KEYSTORE_PASSWORD = "changeThisPassword"; // For production, use Android Keystore to protect this
    private static final String KEY_ALIAS = "server_key";
    private final Context context;

    // Statically register Bouncy Castle as a Security Provider
    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public TLSCertificateManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean certificateExists() {
        File keystoreFile = new File(context.getFilesDir(), KEYSTORE_FILE);
        return keystoreFile.exists();
    }

    public void deleteCertificate() {
        File keystoreFile = new File(context.getFilesDir(), KEYSTORE_FILE);
        if (keystoreFile.exists()) {
            if (keystoreFile.delete()) {
                Log.i(TAG, "Keystore deleted successfully.");
            } else {
                Log.e(TAG, "Failed to delete keystore.");
            }
        }
    }

    public void generateAndStoreCertificate() throws Exception {
        // Generate a 2048-bit RSA key pair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048, new SecureRandom());
        KeyPair keyPair = keyGen.generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();

        // Define certificate details
        long now = System.currentTimeMillis();
        Date startDate = new Date(now);
        X500Name dnName = new X500Name("CN=localhost"); // Common Name
        BigInteger certSerialNumber = new BigInteger(Long.toString(now));
        Date endDate = new Date(now + 10 * 365 * 24 * 60 * 60 * 1000L); // Valid for 10 years

        // Use the private key to sign the certificate
        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSA").build(privateKey);

        // Build the X.509 certificate
        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(dnName, certSerialNumber, startDate, endDate, dnName, keyPair.getPublic());
        X509Certificate certificate = new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME).getCertificate(certBuilder.build(contentSigner));

        // Store the key and certificate in a BKS (Bouncy Castle KeyStore)
        KeyStore keyStore = KeyStore.getInstance("PKCS12", BouncyCastleProvider.PROVIDER_NAME);
        keyStore.load(null, null);
        keyStore.setKeyEntry(KEY_ALIAS, privateKey, KEYSTORE_PASSWORD.toCharArray(), new java.security.cert.Certificate[]{certificate});

        // Write the keystore to a file in the app's internal storage
        File keystoreFile = new File(context.getFilesDir(), KEYSTORE_FILE);
        try (FileOutputStream fos = new FileOutputStream(keystoreFile)) {
            keyStore.store(fos, KEYSTORE_PASSWORD.toCharArray());
        }
        Log.i(TAG, "Keystore created and stored at: " + keystoreFile.getAbsolutePath());
    }

    public SSLSocketFactory getSslSocketFactory() throws Exception {
        if (!certificateExists()) {
            throw new Exception("Keystore does not exist. Please generate a certificate first.");
        }

        // Load the BKS keystore
        KeyStore keyStore = KeyStore.getInstance("PKCS12", BouncyCastleProvider.PROVIDER_NAME);
        File keystoreFile = new File(context.getFilesDir(), KEYSTORE_FILE);
        try (FileInputStream fis = new FileInputStream(keystoreFile)) {
            keyStore.load(fis, KEYSTORE_PASSWORD.toCharArray());
        }

        // Set up the KeyManagerFactory
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, KEYSTORE_PASSWORD.toCharArray());

        // Set up the SSLContext to use the KeyManagerFactory
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

        return sslContext.getSocketFactory();
    }
}