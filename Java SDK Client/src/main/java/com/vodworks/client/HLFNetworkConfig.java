package com.vodworks.client;

import java.io.File;
import java.nio.file.Paths;
import java.util.Properties;

public class HLFNetworkConfig {

    public Properties getEndPointProperties(final String type, final String name) {

        Properties ret = new Properties();

        final String domainName = getDomainName(name);

        File cert = Paths.get(getTestChannelPath(), "crypto-config/ordererOrganizations".replace("orderer", type), domainName, type + "s",
                name, "tls/server.crt").toFile();

        if (!cert.exists()) {
            throw new RuntimeException(String.format("Missing cert file for: %s. Could not find at location: %s", name,
                    cert.getAbsolutePath()));
        }

        File clientCert;
        File clientKey;
        if ("orderer".equals(type)) {
            clientCert = Paths.get(getTestChannelPath(), "crypto-config/ordererOrganizations/vodworks.com/users/Admin@vodworks.com/tls/client.crt").toFile();

            clientKey = Paths.get(getTestChannelPath(), "crypto-config/ordererOrganizations/vodworks.com/users/Admin@vodworks.com/tls/client.key").toFile();
        } else {
            clientCert = Paths.get(getTestChannelPath(), "crypto-config/peerOrganizations/", domainName, "users/User1@" + domainName, "tls/client.crt").toFile();
            clientKey = Paths.get(getTestChannelPath(), "crypto-config/peerOrganizations/", domainName, "users/User1@" + domainName, "tls/client.key").toFile();
        }

        if (!clientCert.exists()) {
            throw new RuntimeException(String.format("Missing  client cert file for: %s. Could not find at location: %s", name,
                    clientCert.getAbsolutePath()));
        }

        if (!clientKey.exists()) {
            throw new RuntimeException(String.format("Missing  client key file for: %s. Could not find at location: %s", name,
                    clientKey.getAbsolutePath()));
        }

        ret.setProperty("clientCertFile", clientCert.getAbsolutePath());
        ret.setProperty("clientKeyFile", clientKey.getAbsolutePath());

        ret.setProperty("pemFile", cert.getAbsolutePath());

        ret.setProperty("hostnameOverride", name);
        ret.setProperty("sslProvider", "openSSL");
        ret.setProperty("negotiationType", "TLS");

        return ret;
    }

    private String getDomainName(final String name) {
        int dot = name.indexOf(".");
        if (-1 == dot) {
            return null;
        } else {
            return name.substring(dot + 1);
        }
    }

    public String getTestChannelPath() {
        return "/Users/BAY/fabric-samples/first-network/";
    }
}