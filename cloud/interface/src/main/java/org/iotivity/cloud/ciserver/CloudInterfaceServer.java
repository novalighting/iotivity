/*
 * //******************************************************************
 * //
 * // Copyright 2016 Samsung Electronics All Rights Reserved.
 * //
 * //-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
 * //
 * // Licensed under the Apache License, Version 2.0 (the "License");
 * // you may not use this file except in compliance with the License.
 * // You may obtain a copy of the License at
 * //
 * //      http://www.apache.org/licenses/LICENSE-2.0
 * //
 * // Unless required by applicable law or agreed to in writing, software
 * // distributed under the License is distributed on an "AS IS" BASIS,
 * // WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * // See the License for the specific language governing permissions and
 * // limitations under the License.
 * //
 * //-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
 */
package org.iotivity.cloud.ciserver;

import java.net.InetSocketAddress;
import java.util.Scanner;

import org.iotivity.cloud.ciserver.resources.UpdateDeviceStateListener;
import org.iotivity.cloud.ciserver.resources.proxy.account.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.iotivity.cloud.base.connector.ConnectorPool;
import org.iotivity.cloud.base.server.CoapServer;
import org.iotivity.cloud.base.server.HttpServer;
import org.iotivity.cloud.base.server.WebSocketServer;
import org.iotivity.cloud.ciserver.DeviceServerSystem.CoapDevicePool;
import org.iotivity.cloud.ciserver.resources.KeepAliveResource;
import org.iotivity.cloud.ciserver.resources.RouteResource;
import org.iotivity.cloud.ciserver.resources.proxy.mq.MessageQueue;
import org.iotivity.cloud.ciserver.resources.proxy.rd.DevicePresence;
import org.iotivity.cloud.ciserver.resources.proxy.rd.ResourceDirectory;
import org.iotivity.cloud.ciserver.resources.proxy.rd.ResourceFind;
import org.iotivity.cloud.ciserver.resources.proxy.rd.ResourcePresence;

public class CloudInterfaceServer {
    private final static Logger Log = LoggerFactory.getLogger(CloudInterfaceServer.class);
    private static int[]        deviceKeepAliveMinutes = new int[] { 1, 2, 4, 8 };
    private static int          coapServerPort;
    private static boolean      tlsMode;
    private static boolean      keepAlive = false;
    private static boolean      hcProxyMode;
    private static int          hcProxyPort;
    private static boolean      websocketMode;
    private static int          websocketPort;
    private static String       resourceDirectoryAddress;
    private static int          resourceDirectoryPort;
    private static String       accountServerAddress;
    private static int          accountServerPort;
    private static boolean      messageQueueEnabled;
    private static String       messageQueueAddress;
    private static int          messageQueuePort;

    public static void main(String[] args) throws Exception {
        Log.info("Starting Cloud Interface Server");

        if (!parseConfiguration(args)) {
            Log.error("\nCoAP-server <Port> RD-server <Address> <Port> Account-server <Address> <Port> MQ-broker <Address> <Port> HC-proxy [HTTP-port] "
                    + "Websocket-server <Port> and TLS-mode <0|1> are required.\n"
                    + "ex) " + Constants.DEFAULT_COAP_PORT
                    + " 127.0.0.1 " + Constants.DEFAULT_RESOURCE_DIRECTORY_PORT
                    + " 127.0.0.1 " + Constants.DEFAULT_ACCOUNT_SERVER_PORT
                    + " 127.0.0.1 " + Constants.DEFAULT_MESSAGE_QUEUE_PORT
                    + " " + Constants.DEFAULT_HC_PROXY_PORT
                    + " " + Constants.DEFAULT_WEBSOCKET_PORT + " 0\n");
            return;
        }

        final KeepAliveResource resKeepAlive = new KeepAliveResource(deviceKeepAliveMinutes);
        final UpdateDeviceStateListener updatePresenceState = new UpdateDeviceStateListener(resKeepAlive);
        ConnectorPool.requestConnection("rd",
                new InetSocketAddress(resourceDirectoryAddress, resourceDirectoryPort),
                tlsMode, keepAlive, updatePresenceState);
        ConnectorPool.requestConnection("account",
                new InetSocketAddress(accountServerAddress, accountServerPort),
                tlsMode, keepAlive);

        if(messageQueueEnabled) {
            ConnectorPool.requestConnection("mq",
                    new InetSocketAddress(messageQueueAddress, messageQueuePort),
                    tlsMode, keepAlive);
        }

        DeviceServerSystem deviceServer = new DeviceServerSystem();

        Account acHandler = new Account();
        AccountSession acSessionHandler = new AccountSession();
        ResourceDirectory rdHandler = new ResourceDirectory();
        ResourceFind resHandler = new ResourceFind();
        ResourcePresence adHandler = new ResourcePresence();
        DevicePresence prsHandler = new DevicePresence();
        MessageQueue mqHandler = new MessageQueue();
        Acl aclHandler = new Acl();
        AclGroup aclGroupHandler = new AclGroup();
        Certificate certHandler = new Certificate();
        AclInvite aclInviteHandler = new AclInvite();
        Crl crlHandler = new Crl();
        CoapDevicePool devicePool = deviceServer.getDevicePool();

        //version 2.0.0
        SecAccount secAcHandler = new SecAccount();
        SecAccountSession secAcSessionHandler = new SecAccountSession();
        deviceServer.addResource(secAcHandler);
        deviceServer.addResource(secAcSessionHandler);


        deviceServer.addResource(acHandler);

        deviceServer.addResource(acSessionHandler);

        deviceServer.addResource(rdHandler);

        deviceServer.addResource(resHandler);

        deviceServer.addResource(adHandler);

        deviceServer.addResource(prsHandler);

        deviceServer.addResource(mqHandler);

        deviceServer.addResource(aclHandler);

        deviceServer.addResource(aclGroupHandler);

        deviceServer.addResource(certHandler);

        deviceServer.addResource(aclInviteHandler);

        deviceServer.addResource(crlHandler);

        deviceServer.addResource(resKeepAlive);

        deviceServer.addResource(new RouteResource(devicePool));

        deviceServer.addServer(
                new CoapServer(new InetSocketAddress(coapServerPort)));

        if (hcProxyMode)
            deviceServer.addServer(
                    new HttpServer(new InetSocketAddress(hcProxyPort)));

        if (websocketMode)
            deviceServer.addServer(
                    new WebSocketServer(new InetSocketAddress(websocketPort)));

        deviceServer.startSystem(tlsMode);

        resKeepAlive.startSessionChecker(3000, 6000);

        Scanner in = new Scanner(System.in);

        System.out.println("press 'q' to terminate");

        while (!in.nextLine().equals("q"));

        in.close();

        System.out.println("Terminating...");

        resKeepAlive.stopSessionChecker();

        deviceServer.stopSystem();

        System.out.println("Terminated");
    }

    private static boolean parseConfiguration(String[] args) {
        // configuration provided by arguments
        if (args.length == 10) {
            coapServerPort = Integer.parseInt(args[0]);
            resourceDirectoryAddress = args[1];
            resourceDirectoryPort = Integer.parseInt(args[2]);
            accountServerAddress = args[3];
            accountServerPort = Integer.parseInt(args[4]);
            messageQueueAddress = args[5];
            messageQueuePort = Integer.parseInt(args[6]);
            hcProxyPort = Integer.parseInt(args[7]);
            hcProxyMode = hcProxyPort != 0;
            websocketPort = Integer.parseInt(args[8]);
            websocketMode = websocketPort != 0;
            tlsMode = Integer.parseInt(args[9]) == 1;

            return true;
        }

        // configuration provided by docker env
        String tlsModeEnv = System.getenv("TLS_MODE");
        if (tlsModeEnv != null) {
            coapServerPort = Integer.parseInt(System.getenv("COAP_PORT"));
            resourceDirectoryAddress = System.getenv("RESOURCE_DIRECTORY_ADDRESS");
            resourceDirectoryPort = Integer.parseInt(System.getenv("RESOURCE_DIRECTORY_PORT"));
            accountServerAddress = System.getenv("ACCOUNT_SERVER_ADDRESS");
            accountServerPort = Integer.parseInt(System.getenv("ACCOUNT_SERVER_PORT"));
            hcProxyMode = Integer.parseInt(System.getenv("HTTP_PROXY_MODE")) == 1;
            hcProxyPort = Integer.parseInt(System.getenv("HTTP_PROXY_PORT"));
            websocketMode = Integer.parseInt(System.getenv("WEBSOCKET_MODE")) == 1;
            websocketPort = Constants.DEFAULT_WEBSOCKET_PORT;
            keepAlive = Integer.parseInt(System.getenv("KEEPALIVE_CLOUD")) == 1;
            tlsMode = Integer.parseInt(tlsModeEnv) == 1;
            String keepAliveEnv = System.getenv("DEVICE_KEEPALIVE_MINUTES");
            if (keepAliveEnv != null && !keepAliveEnv.isEmpty())
                deviceKeepAliveMinutes = new int[] { Integer.parseInt(keepAliveEnv) };

            String messageQueueEnv = System.getenv("MESSAGE_QUEUE_ADDRESS");
            if(messageQueueEnv != null)
            {
                messageQueueAddress = System.getenv("MESSAGE_QUEUE_ADDRESS");
                messageQueuePort = Integer.parseInt(System.getenv("MESSAGE_QUEUE_PORT"));
                messageQueueEnabled = true;
            }
            return true;
        }
        return false;
    }
}