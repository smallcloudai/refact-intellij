package com.smallcloud.refactai.io

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.URI
import java.util.concurrent.TimeUnit
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

// TODO: refactor this and mockServer into there own utils.
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
    // Use BouncyCastle or similar library to generate the certificate
    // This is a placeholder for the actual implementation
    // You can use libraries like BouncyCastle to create the certificate
    // For simplicity, this function is not fully implemented here
    // throw NotImplementedError("Implement certificate generation using BouncyCastle or similar library.")
    val subjectName = X500Name(subject.name)
    val issuerName = X500Name(issuer.name)

    // Create a certificate builder
    val certBuilder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
        issuerName,
        BigInteger.valueOf(serialNumber),
        notBefore,
        notAfter,
        subjectName,
        publicKey
    )

    // Create a content signer
    val contentSigner: ContentSigner = JcaContentSignerBuilder("SHA256WithRSA").build(privateKey)

    // Build the certificate
    val certificate: X509Certificate = JcaX509CertificateConverter().getCertificate(certBuilder.build(contentSigner))

    return certificate
}

/**
 * Basic demonstration of MockWebServer for network mocking in tests.
 * This example uses Java's HttpClient instead of project-specific classes
 * to show the core concepts of MockWebServer.
 */
class MockWebServerDemoTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var httpClient: AsyncConnection
    private lateinit var baseUrl: String

    @Before
    fun setup() {
        val (sslContext, privateKey) = createSelfSignedCertificate()

        httpClient = AsyncConnection()
        // Start the mock server
        mockWebServer = MockWebServer()
        mockWebServer.useHttps(sslContext.socketFactory, false)
        mockWebServer.start()
        
        // Get the base URL of the mock server
        baseUrl = mockWebServer.url("/").toString()
        
        // Create a standard HttpClient with SSL context that trusts all certificates

    }

    @After
    fun tearDown() {
        // Shutdown the mock server after each test
        mockWebServer.shutdown()
    }

    @Test
    fun testBasicGetRequest() {
        // Prepare a mock response
        val responseBody = """{"status":"success","data":"test data"}"""
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody)
        )

        val response = httpClient.get(URI.create(baseUrl + "api/test")).join().get().toString()

        // Verify the request was made correctly
        val recordedRequest = mockWebServer.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(recordedRequest)
        assertEquals("GET", recordedRequest!!.method)
        assertEquals("/api/test", recordedRequest.path)
        
        // Verify the response
        assertEquals(responseBody, response)
        
        // Parse the JSON to verify the content
        val gson = Gson()
        val jsonObject = gson.fromJson(response, JsonObject::class.java)
        assertEquals("success", jsonObject.get("status").asString)
        assertEquals("test data", jsonObject.get("data").asString)
    }

}
