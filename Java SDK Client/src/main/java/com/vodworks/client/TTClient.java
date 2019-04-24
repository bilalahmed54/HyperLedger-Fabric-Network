package com.vodworks.client;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Properties;
import org.apache.log4j.Logger;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.hyperledger.fabric.sdk.*;
import java.util.concurrent.TimeUnit;
import org.hyperledger.fabric.sdk.exception.*;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import java.lang.reflect.InvocationTargetException;
import org.hyperledger.fabric_ca.sdk.EnrollmentRequest;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;

public class TTClient {

    static HLFNetworkConfig hlfNetworkConfig = new HLFNetworkConfig();
    private static final Logger log = Logger.getLogger(TTClient.class);

    public static void main(String[] args) throws Exception {

        System.setProperty("javax.net.ssl.trustStore", "/Users/BAY/fabric-samples/first-network/channel-artifactssss/bayTrustStore.bay.sa");
        System.setProperty("javax.net.ssl.trustStorePassword", "changeitbay");

        String cert = "/Users/BAY/fabric-samples/first-network/crypto-config/peerOrganizations/telco1.vodworks.com/ca/ca.telco1.vodworks.com-cert.pem";

        File cf = new File(cert);
        if (!cf.exists() || !cf.isFile()) {
            throw new RuntimeException("TEST is missing cert file " + cf.getAbsolutePath());
        }

        Properties properties = new Properties();
        //properties.put("pemBytes", pemStr.getBytes());
        properties.setProperty("pemFile", cf.getAbsolutePath());

        //testing environment only NOT FOR PRODUCTION!
        properties.setProperty("allowAllHostNames", "true");

        // create fabric-ca client
        HFCAClient caClient = getHfCaClient("ca-telco1", "https://ca.telco1.vodworks.com:7054", properties);

        // enroll or load admin
        AppUser admin = getAdmin(caClient);
        log.info("Admin User Enrolled/Loaded: " + admin);

        // register and enroll new user
        AppUser appUser = getUser(caClient, admin, "hfbay");
        log.info("Application User Registered and Enrolled: " + appUser);

        // get HFC client instance
        HFClient client = getHfClient();

        // set user context
        client.setUserContext(admin);

        // get HFC channel using the client
        Channel channel = getChannel(client, admin);
        log.info("Get Hyperledger Fabric Channel: " + channel.getName());

        // call query blockchain example
        queryBlockChain(client);
    }

    /**
     * Get new fabic-ca client
     *
     * @param caUrl              The fabric-ca-server endpoint url
     * @param caClientProperties The fabri-ca client properties. Can be null.
     * @return new client instance. never null.
     * @throws Exception Exception
     */
    private static HFCAClient getHfCaClient(String caName, String caUrl, Properties caClientProperties) throws Exception {
        CryptoSuite cryptoSuite = CryptoSuite.Factory.getCryptoSuite();
        HFCAClient caClient = HFCAClient.createNewInstance(caName, caUrl, caClientProperties);
        caClient.setCryptoSuite(cryptoSuite);
        return caClient;
    }

    static private void queryBlockChain(HFClient client) throws ProposalException, InvalidArgumentException {

        // get channel instance from client
        Channel channel = client.getChannel("ttchannel");

        // create chaincode request
        QueryByChaincodeRequest qpr = client.newQueryProposalRequest();

        // build cc id providing the chaincode name. Version is omitted here.
        ChaincodeID ttchaincode = ChaincodeID.newBuilder().setName("ttchaincode").build();

        qpr.setChaincodeID(ttchaincode);

        // CC function to be called
        qpr.setArgs("b");
        qpr.setFcn("query");

        qpr.setChaincodeLanguage(TransactionRequest.Type.JAVA);

        Collection<ProposalResponse> res = channel.queryByChaincode(qpr);
        // display response
        for (ProposalResponse pres : res) {
            log.info("Chaincode Response Status: " + pres.getStatus());
            String stringResponse = new String(pres.getChaincodeActionResponsePayload());
            log.info("Query Response Received: " + stringResponse);
        }
    }

    private static Channel getChannel(HFClient client, User peerAdmin) throws InvalidArgumentException, TransactionException, CryptoException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException, IOException, NetworkConfigurationException {

        // initialize channel

        // peer name and endpoint in fabcar network

        Properties peerProperties = new Properties();
        peerProperties.setProperty("sslProvider", "openSSL");
        peerProperties.setProperty("negotiationType", "TLS");
        peerProperties.setProperty("trustServerCertificate", "true"); //testing environment only NOT FOR PRODUCTION!
        peerProperties.setProperty("hostnameOverride", "peer0.telco1.vodworks.com");
        peerProperties.setProperty("pemFile", "/Users/BAY/fabric-samples/first-network/crypto-config/peerOrganizations/telco1.vodworks.com/peers/peer0.telco1.vodworks.com/tls/server.crt");

        Peer peer = client.newPeer("peer0.telco1.vodworks.com", "grpcs://peer0.telco1.vodworks.com:7051", peerProperties);

        // eventhub name and endpoint in fabcar network
        //EventHub eventHub = client.newEventHub("eventhub01", "grpcs://localhost:7053");

        // orderer name and endpoint in fabcar network

        Properties ordererProperties = new Properties();

        ordererProperties.setProperty("clientCertFile", "/Users/BAY/fabric-samples/first-network/crypto-config/ordererOrganizations/vodworks.com/users/Admin@vodworks.com/tls/client.crt");
        ordererProperties.setProperty("clientKeyFile", "/Users/BAY/fabric-samples/first-network/crypto-config/ordererOrganizations/vodworks.com/users/Admin@vodworks.com/tls/client.key");
        ordererProperties.setProperty("sslProvider", "openSSL");
        ordererProperties.setProperty("negotiationType", "TLS");
        ordererProperties.setProperty("trustServerCertificate", "true"); //testing environment only NOT FOR PRODUCTION!
        ordererProperties.setProperty("hostnameOverride", "orderer0.vodworks.com");
        ordererProperties.setProperty("pemFile", "/Users/BAY/fabric-samples/first-network/crypto-config/ordererOrganizations/vodworks.com/orderers/orderer0.vodworks.com/tls/server.crt");

        ordererProperties.put("grpc.NettyChannelBuilderOption.keepAliveTime", new Object[]{5L, TimeUnit.MINUTES});
        ordererProperties.put("grpc.NettyChannelBuilderOption.keepAliveTimeout", new Object[]{8L, TimeUnit.SECONDS});
        ordererProperties.put("grpc.NettyChannelBuilderOption.keepAliveWithoutCalls", new Object[]{true});

        Orderer orderer = client.newOrderer("orderer0.vodworks.com", "grpcs://0.0.0.0:7050", ordererProperties);

        String path = "/Users/BAY/fabric-samples/first-network/channel-artifacts/channel.tx";
        ChannelConfiguration channelConfiguration = new ChannelConfiguration(new File(path));

        File f = new File("/Users/BAY/fabric-samples/first-network/connection_profiles/tt-network-telco1.json");

        NetworkConfig networkConfig = NetworkConfig.fromJsonFile(f);

        networkConfig.getOrdererNames().forEach(ordererName -> {

            try {

                Properties ordererProperties1 = networkConfig.getOrdererProperties(ordererName);
                Properties hlfNetworkEndPointConfig = hlfNetworkConfig.getEndPointProperties("orderer", ordererName);

                ordererProperties1.setProperty("clientKeyFile", hlfNetworkEndPointConfig.getProperty("clientKeyFile"));
                ordererProperties1.setProperty("clientCertFile", hlfNetworkEndPointConfig.getProperty("clientCertFile"));

                networkConfig.setOrdererProperties(ordererName, ordererProperties1);

            } catch (InvalidArgumentException e) {
                throw new RuntimeException(e);
            }
        });

        networkConfig.getPeerNames().forEach(peerName -> {

            try {

                Properties peerProperties1 = networkConfig.getPeerProperties(peerName);
                Properties hlfNetworkEndPointConfig = hlfNetworkConfig.getEndPointProperties("peer", peerName);

                peerProperties1.setProperty("clientKeyFile", hlfNetworkEndPointConfig.getProperty("clientKeyFile"));
                peerProperties1.setProperty("clientCertFile", hlfNetworkEndPointConfig.getProperty("clientCertFile"));

                networkConfig.setPeerProperties(peerName, peerProperties1);

            } catch (InvalidArgumentException e) {
                throw new RuntimeException(e);
            }
        });


        //Create channel that has only one signer that is this orgs peer admin. If channel creation policy needed more signature they would need to be added too.
//        Channel channel = client.newChannel("ttchannel", orderer, channelConfiguration, client.getChannelConfigurationSignature(channelConfiguration, peerAdmin));
        Channel channel = client.loadChannelFromConfig("ttchannel", networkConfig);


//        channel.addPeer(peer);
//        channel.addOrderer(orderer);
        channel.initialize();

        return channel;
    }

    private static HFClient getHfClient() throws Exception {

        // initialize default cryptosuite
        CryptoSuite cryptoSuite = CryptoSuite.Factory.getCryptoSuite();

        // setup the client
        HFClient client = HFClient.createNewInstance();
        client.setCryptoSuite(cryptoSuite);

        return client;
    }

    /**
     * Register and enroll user with userId.
     * If AppUser object with the name already exist on fs it will be loaded and
     * registration and enrollment will be skipped.
     *
     * @param caClient  The fabric-ca client.
     * @param registrar The registrar to be used.
     * @param userId    The user id.
     * @return AppUser instance with userId, affiliation,mspId and enrollment set.
     * @throws Exception Exception
     */
    private static AppUser getUser(HFCAClient caClient, AppUser registrar, String userId) throws Exception {

        AppUser appUser = tryDeserialize(userId);

        if (appUser == null) {
            RegistrationRequest rr = new RegistrationRequest(userId, "org1.department1");
            String enrollmentSecret = caClient.register(rr, registrar);
            Enrollment enrollment = caClient.enroll(userId, enrollmentSecret);
            appUser = new AppUser(userId, "telco1", "Telco1MSP", enrollment);
            serialize(appUser);
        }

        return appUser;
    }

    /**
     * Enroll admin into fabric-ca using {@code admin/adminpw} credentials.
     * If AppUser object already exist serialized on fs it will be loaded and
     * new enrollment will not be executed.
     *
     * @param caClient The fabric-ca client
     * @return AppUser instance with userid, affiliation, mspId and enrollment set
     * @throws Exception Exception
     */
    private static AppUser getAdmin(HFCAClient caClient) throws Exception {

        AppUser admin = tryDeserialize("admin");

        final EnrollmentRequest enrollmentRequestTLS = new EnrollmentRequest();
        enrollmentRequestTLS.addHost("localhost");
        enrollmentRequestTLS.setProfile("tls");

        if (admin == null) {
            Enrollment adminEnrollment = caClient.enroll("admin", "adminpw", enrollmentRequestTLS);
            admin = new AppUser("admin", "telco1", "Telco1MSP", adminEnrollment);
            serialize(admin);
        }

        return admin;
    }

    // user serialization and deserialization utility functions
    // files are stored in the base directory

    /**
     * Serialize AppUser object to file
     *
     * @param appUser The object to be serialized
     * @throws IOException IOException
     */
    private static void serialize(AppUser appUser) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(
                Paths.get(appUser.getName() + ".jso")))) {
            oos.writeObject(appUser);
        }
    }

    /**
     * Deserialize AppUser object from file
     *
     * @param name The name of the user. Used to build file name ${name}.jso
     * @return AppUser
     * @throws Exception Exception
     */
    private static AppUser tryDeserialize(String name) throws Exception {
        if (Files.exists(Paths.get(name + ".jso"))) {
            return deserialize(name);
        }
        return null;
    }

    private static AppUser deserialize(String name) throws Exception {
        try (ObjectInputStream decoder = new ObjectInputStream(
                Files.newInputStream(Paths.get(name + ".jso")))) {
            return (AppUser) decoder.readObject();
        }
    }
}