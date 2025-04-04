package com.smallcloud.refactai.testUtils

import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.security.SecureRandom
import java.util.Date
import javax.security.auth.x500.X500Principal
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.asn1.x500.X500Name
import java.math.BigInteger


fun createSelfSignedCertificate(): Pair<SSLContext, PrivateKey> {
    val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
    keyPairGenerator.initialize(2048)
    val keyPair = keyPairGenerator.generateKeyPair()
    val privateKey = keyPair.private
    val publicKey: PublicKey = keyPair.public

    val subject = X500Principal("CN=localhost")
    val issuer = subject
    val serialNumber = SecureRandom().nextInt().toLong()
    val notBefore = Date(System.currentTimeMillis())
    val notAfter = Date(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000) // 1 year validity

    val certificate = generateSelfSignedCertificate(subject, issuer, serialNumber, notBefore, notAfter, publicKey, privateKey)

    // Create a KeyStore and load the self-signed certificate
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
    keyStore.load(null, null)
    keyStore.setKeyEntry("selfsigned", privateKey, "password".toCharArray(), arrayOf(certificate))

    // Create SSLContext
    val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
    keyManagerFactory.init(keyStore, "password".toCharArray())

    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagerFactory.keyManagers, null, null)

    return Pair(sslContext, privateKey)
}

fun generateSelfSignedCertificate(
    subject: X500Principal,
    issuer: X500Principal,
    serialNumber: Long,
    notBefore: Date,
    notAfter: Date,
    publicKey: PublicKey,
    privateKey: PrivateKey
): X509Certificate {
    val subjectName = X500Name(subject.name)
    val issuerName = X500Name(issuer.name)

    val certBuilder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
        issuerName,
        BigInteger.valueOf(serialNumber),
        notBefore,
        notAfter,
        subjectName,
        publicKey
    )

    val contentSigner: ContentSigner = JcaContentSignerBuilder("SHA256WithRSA").build(privateKey)

    val certificate: X509Certificate = JcaX509CertificateConverter().getCertificate(certBuilder.build(contentSigner))

    return certificate
}


public abstract class MockServer {
    lateinit var server: MockWebServer
    lateinit var baseUrl: String

    @Before
    fun setup() {
        server = MockWebServer()
        server.useHttps(sslContext.socketFactory, false)
        server.start()
        baseUrl = server.url("/").toString()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    companion object {
        private var _sslContext: SSLContext? = null
        private var _privateKey: PrivateKey? = null

        val sslContext: SSLContext
            get() {
                if (_sslContext == null) {
                    val (context, key) = createSelfSignedCertificate()
                    _sslContext = context
                    _privateKey = key
                }
                return _sslContext!!
            }

        val privateKey: PrivateKey
            get() {
                if (_privateKey == null) {
                    sslContext // This will trigger the initialization
                }
                return _privateKey!!
            }
    }

}
