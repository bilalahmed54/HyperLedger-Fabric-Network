package com.vodworks.client.gateway;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.hyperledger.fabric.gateway.*;
import sun.misc.BASE64Decoder;

import javax.xml.bind.DatatypeConverter;

public class TTGatewayClient {

    public static void main(String[] args) throws IOException, GatewayException {

        // load a CCP
        Path networkConfigPath = Paths.get("/Users/BAY/fabric-samples/first-network/connection_profiles/tt-network-telco1.json");

        Wallet wallet = createWallet();
        Gateway.Builder builder = Gateway.createBuilder();

        builder.identity(wallet, "bayUser");
        builder.networkConfig(networkConfigPath);

        // create a gateway connection
        try (Gateway gateway = builder.connect()) {
            // get the network and contract
            Network network = gateway.getNetwork("ttchannel");
            Contract contract = network.getContract("ttchaincode");

            byte[] result = contract.submitTransaction("query", "b");
            System.out.println("BAY Response 01: " + result.toString());

            result = contract.evaluateTransaction("query", "b");
            System.out.println("BAY Response 02: " + result.toString());

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static Wallet createWallet() throws IOException, GatewayException {

//        Path walletPath = Paths.get("wallet");
        Path credentialPath = Paths.get("/Users/BAY/fabric-samples/first-network/crypto-config/peerOrganizations/telco1.vodworks.com/users/User1@telco1.vodworks.com/msp");

        String certificatePem = readFile(credentialPath.resolve(Paths.get("signcerts", "User1@telco1.vodworks.com-cert.pem")));
        PrivateKey privateKey = readPrivateKey(credentialPath.resolve(Paths.get("keystore", "key.pem")));

        Wallet wallet = Wallet.createInMemoryWallet();
        wallet.put("bayUser", Wallet.Identity.createIdentity("Telco1MSP", certificatePem, privateKey));

        return wallet;
    }

    private static String readFile(Path file) throws IOException {
        StringBuilder contents = new StringBuilder();
        if (Files.exists(file)) {
            try (BufferedReader fr = Files.newBufferedReader(file)) {
                String line;
                while ((line = fr.readLine()) != null) {
                    contents.append(line);
                    contents.append('\n');
                }
            }
            return contents.toString();
        }
        return null;
    }

    private static PrivateKey readPrivateKey(Path pemFile) {

        if (Files.exists(pemFile)) {

            try {

                PEMParser parser = new PEMParser(Files.newBufferedReader(pemFile));

                Object key = parser.readObject();

                JcaPEMKeyConverter converter = new JcaPEMKeyConverter();

                if (key instanceof PrivateKeyInfo) {
                    return converter.getPrivateKey((PrivateKeyInfo) key);
                } else {
                    return converter.getPrivateKey(((PEMKeyPair) key).getPrivateKeyInfo());
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return null;
    }
}