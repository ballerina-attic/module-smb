/*
 * Copyright (c) 2019 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.ei.b7a.smb.client;

import org.ballerinalang.jvm.BRuntime;
import org.ballerinalang.jvm.values.ArrayValue;
import org.ballerinalang.jvm.values.MapValue;
import org.ballerinalang.jvm.values.ObjectValue;
import org.ballerinalang.stdlib.io.channels.base.Channel;
import org.ballerinalang.stdlib.io.utils.IOConstants;
import org.wso2.ei.b7a.smb.util.BallerinaSMBException;
import org.wso2.ei.b7a.smb.util.SMBUtil;
import org.wso2.ei.b7a.smb.util.SmbConstants;
import org.wso2.transport.remotefilesystem.RemoteFileSystemConnectorFactory;
import org.wso2.transport.remotefilesystem.client.connector.contract.FtpAction;
import org.wso2.transport.remotefilesystem.client.connector.contract.VFSClientConnector;
import org.wso2.transport.remotefilesystem.exception.RemoteFileSystemConnectorException;
import org.wso2.transport.remotefilesystem.impl.RemoteFileSystemConnectorFactoryImpl;
import org.wso2.transport.remotefilesystem.message.RemoteFileSystemMessage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Contains functionality of SMB client
 */
public class SMBClient {

    private SMBClient() {
        // private constructor
    }

    public static void initClientEndpoint(ObjectValue clientEndpoint, MapValue<Object, Object> config)
            throws BallerinaSMBException {

        String protocol = config.getStringValue(SmbConstants.ENDPOINT_CONFIG_PROTOCOL);
        if (SMBUtil.notValidProtocol(protocol)) {
            throw new BallerinaSMBException("Only SMB protocol is supported by SMB client.");
        }

        Map<String, String> authMap = SMBUtil.getAuthMap(config);
        clientEndpoint.addNativeData(SmbConstants.ENDPOINT_CONFIG_USERNAME,
                authMap.get(SmbConstants.ENDPOINT_CONFIG_USERNAME));
        clientEndpoint.addNativeData(SmbConstants.ENDPOINT_CONFIG_PASS_KEY,
                authMap.get(SmbConstants.ENDPOINT_CONFIG_PASS_KEY));
        clientEndpoint.addNativeData(SmbConstants.ENDPOINT_CONFIG_HOST,
                config.getStringValue(SmbConstants.ENDPOINT_CONFIG_HOST));
        clientEndpoint.addNativeData(SmbConstants.ENDPOINT_CONFIG_PORT,
                SMBUtil.extractPortValue(config.getIntValue(SmbConstants.ENDPOINT_CONFIG_PORT)));
        clientEndpoint.addNativeData(SmbConstants.ENDPOINT_CONFIG_PROTOCOL, protocol);
        Map<String, String> smbConfig = new HashMap<>(3);
        smbConfig.put(SmbConstants.SMB_PASSIVE_MODE, String.valueOf(true));
        smbConfig.put(SmbConstants.USER_DIR_IS_ROOT, String.valueOf(false));
        smbConfig.put(SmbConstants.AVOID_PERMISSION_CHECK, String.valueOf(true));
        clientEndpoint.addNativeData(SmbConstants.PROPERTY_MAP, smbConfig);
    }

    public static ObjectValue get(ObjectValue clientConnector, String filePath) throws BallerinaSMBException {

        String url = SMBUtil.createUrl(clientConnector, filePath);
        Map<String, String> propertyMap = new HashMap<>(
                (Map<String, String>) clientConnector.getNativeData(SmbConstants.PROPERTY_MAP));
        propertyMap.put(SmbConstants.PROPERTY_URI, url);

        CompletableFuture<Object> future = BRuntime.markAsync();
        SMBClientListener connectorListener = new SMBClientListener(future,
                remoteFileSystemBaseMessage -> SMBClientHelper.executeGetAction(remoteFileSystemBaseMessage, future));
        RemoteFileSystemConnectorFactory fileSystemConnectorFactory = new RemoteFileSystemConnectorFactoryImpl();
        VFSClientConnector connector;
        try {
            connector = fileSystemConnectorFactory.createVFSClientConnector(propertyMap, connectorListener);
        } catch (RemoteFileSystemConnectorException e) {
            throw new BallerinaSMBException(e.getMessage());
        }
        connector.send(null, FtpAction.GET);
        return null;
    }

    public static void append(ObjectValue clientConnector, MapValue<Object, Object> inputContent)
            throws BallerinaSMBException {

        try {
            String url = SMBUtil.createUrl(clientConnector,
                    inputContent.getStringValue(SmbConstants.INPUT_CONTENT_FILE_PATH_KEY));
            Map<String, String> propertyMap = new HashMap<>(
                    (Map<String, String>) clientConnector.getNativeData(SmbConstants.PROPERTY_MAP));
            propertyMap.put(SmbConstants.PROPERTY_URI, url);

            boolean isFile = inputContent.getBooleanValue(SmbConstants.INPUT_CONTENT_IS_FILE_KEY);
            RemoteFileSystemMessage message;
            if (isFile) {
                ObjectValue fileContent = inputContent.getObjectValue(SmbConstants.INPUT_CONTENT_FILE_CONTENT_KEY);
                Channel byteChannel = (Channel) fileContent.getNativeData(IOConstants.BYTE_CHANNEL_NAME);
                message = new RemoteFileSystemMessage(byteChannel.getInputStream());
            } else {
                String textContent = inputContent.getStringValue(SmbConstants.INPUT_CONTENT_TEXT_CONTENT_KEY);
                InputStream stream = new ByteArrayInputStream(textContent.getBytes());
                message = new RemoteFileSystemMessage(stream);
            }

            CompletableFuture<Object> future = BRuntime.markAsync();
            SMBClientListener connectorListener = new SMBClientListener(future,
                    remoteFileSystemBaseMessage -> SMBClientHelper.executeGenericAction(future));
            RemoteFileSystemConnectorFactory fileSystemConnectorFactory = new RemoteFileSystemConnectorFactoryImpl();

            VFSClientConnector connector = fileSystemConnectorFactory.createVFSClientConnector(propertyMap,
                    connectorListener);
            connector.send(message, FtpAction.APPEND);
            future.complete(null);
        } catch (RemoteFileSystemConnectorException | IOException e) {
            throw new BallerinaSMBException(e.getMessage());
        }
    }

    public static void put(ObjectValue clientConnector, MapValue<Object, Object> inputContent)
            throws BallerinaSMBException {

        try {
            String url = SMBUtil.createUrl(clientConnector,
                    inputContent.getStringValue(SmbConstants.INPUT_CONTENT_FILE_PATH_KEY));
            Map<String, String> propertyMap = new HashMap<>(
                    (Map<String, String>) clientConnector.getNativeData(SmbConstants.PROPERTY_MAP));
            propertyMap.put(SmbConstants.PROPERTY_URI, url);

            boolean isFile = inputContent.getBooleanValue(SmbConstants.INPUT_CONTENT_IS_FILE_KEY);
            RemoteFileSystemMessage message;
            if (isFile) {
                ObjectValue fileContent = inputContent.getObjectValue(SmbConstants.INPUT_CONTENT_FILE_CONTENT_KEY);
                Channel byteChannel = (Channel) fileContent.getNativeData(IOConstants.BYTE_CHANNEL_NAME);
                message = new RemoteFileSystemMessage(byteChannel.getInputStream());
            } else {
                String textContent = inputContent.getStringValue(SmbConstants.INPUT_CONTENT_TEXT_CONTENT_KEY);
                InputStream stream = new ByteArrayInputStream(textContent.getBytes());
                message = new RemoteFileSystemMessage(stream);
            }

            CompletableFuture<Object> future = BRuntime.markAsync();
            SMBClientListener connectorListener = new SMBClientListener(future, remoteFileSystemBaseMessage ->
                    SMBClientHelper.executeGenericAction(future));
            RemoteFileSystemConnectorFactory fileSystemConnectorFactory = new RemoteFileSystemConnectorFactoryImpl();

            VFSClientConnector connector = fileSystemConnectorFactory.createVFSClientConnector(propertyMap,
                    connectorListener);
            connector.send(message, FtpAction.PUT);
            future.complete(null);
        } catch (RemoteFileSystemConnectorException | IOException e) {
            throw new BallerinaSMBException(e.getMessage());
        }
    }

    public static void delete(ObjectValue clientConnector, String filePath) throws BallerinaSMBException {

        String url = SMBUtil.createUrl(clientConnector, filePath);
        Map<String, String> propertyMap = new HashMap<>(
                (Map<String, String>) clientConnector.getNativeData(SmbConstants.PROPERTY_MAP));
        propertyMap.put(SmbConstants.PROPERTY_URI, url);

        CompletableFuture<Object> future = BRuntime.markAsync();
        SMBClientListener connectorListener = new SMBClientListener(future,
                remoteFileSystemBaseMessage -> SMBClientHelper.executeGenericAction(future));
        RemoteFileSystemConnectorFactory fileSystemConnectorFactory = new RemoteFileSystemConnectorFactoryImpl();
        VFSClientConnector connector;
        try {
            connector = fileSystemConnectorFactory.createVFSClientConnector(propertyMap, connectorListener);
        } catch (RemoteFileSystemConnectorException e) {
            throw new BallerinaSMBException(e.getMessage());
        }
        connector.send(null, FtpAction.DELETE);
        future.complete(null);
    }

    public static boolean isDirectory(ObjectValue clientConnector, String filePath) throws BallerinaSMBException {

        String url = SMBUtil.createUrl(clientConnector, filePath);
        Map<String, String> propertyMap = new HashMap<>(
                (Map<String, String>) clientConnector.getNativeData(SmbConstants.PROPERTY_MAP));
        propertyMap.put(SmbConstants.PROPERTY_URI, url);

        CompletableFuture<Object> future = BRuntime.markAsync();
        SMBClientListener connectorListener = new SMBClientListener(future, remoteFileSystemBaseMessage ->
                SMBClientHelper.executeIsDirectoryAction(remoteFileSystemBaseMessage, future));
        RemoteFileSystemConnectorFactory fileSystemConnectorFactory = new RemoteFileSystemConnectorFactoryImpl();
        VFSClientConnector connector;
        try {
            connector = fileSystemConnectorFactory.createVFSClientConnector(propertyMap, connectorListener);
        } catch (RemoteFileSystemConnectorException e) {
            throw new BallerinaSMBException(e.getMessage());
        }
        connector.send(null, FtpAction.ISDIR);
        return false;
    }

    public static ArrayValue list(ObjectValue clientConnector, String filePath) throws BallerinaSMBException {

        String url = SMBUtil.createUrl(clientConnector, filePath);
        Map<String, String> propertyMap = new HashMap<>(
                (Map<String, String>) clientConnector.getNativeData(SmbConstants.PROPERTY_MAP));
        propertyMap.put(SmbConstants.PROPERTY_URI, url);

        CompletableFuture<Object> future = BRuntime.markAsync();
        SMBClientListener connectorListener = new SMBClientListener(future, remoteFileSystemBaseMessage ->
                SMBClientHelper.executeListAction(remoteFileSystemBaseMessage, future));
        RemoteFileSystemConnectorFactory fileSystemConnectorFactory = new RemoteFileSystemConnectorFactoryImpl();
        VFSClientConnector connector;
        try {
            connector = fileSystemConnectorFactory.createVFSClientConnector(propertyMap, connectorListener);
        } catch (RemoteFileSystemConnectorException e) {
            throw new BallerinaSMBException(e.getMessage());
        }
        connector.send(null, FtpAction.LIST);
        return null;
    }

    public static void mkdir(ObjectValue clientConnector, String path) throws BallerinaSMBException {

        String url = SMBUtil.createUrl(clientConnector, path);
        Map<String, String> propertyMap = new HashMap<>(
                (Map<String, String>) clientConnector.getNativeData(SmbConstants.PROPERTY_MAP));
        propertyMap.put(SmbConstants.PROPERTY_URI, url);

        CompletableFuture<Object> future = BRuntime.markAsync();
        SMBClientListener connectorListener = new SMBClientListener(future,
                remoteFileSystemBaseMessage -> SMBClientHelper.executeGenericAction(future));
        RemoteFileSystemConnectorFactory fileSystemConnectorFactory = new RemoteFileSystemConnectorFactoryImpl();
        VFSClientConnector connector;
        try {
            connector = fileSystemConnectorFactory.createVFSClientConnector(propertyMap, connectorListener);
        } catch (RemoteFileSystemConnectorException e) {
            throw new BallerinaSMBException(e.getMessage());
        }
        connector.send(null, FtpAction.MKDIR);
        future.complete(null);
    }

    public static void rename(ObjectValue clientConnector, String origin, String destination)
            throws BallerinaSMBException {

        Map<String, String> propertyMap = new HashMap<>(
                (Map<String, String>) clientConnector.getNativeData(SmbConstants.PROPERTY_MAP));
        propertyMap.put(SmbConstants.PROPERTY_URI, SMBUtil.createUrl(clientConnector, origin));
        propertyMap.put(SmbConstants.PROPERTY_DESTINATION, SMBUtil.createUrl(clientConnector, destination));

        CompletableFuture<Object> future = BRuntime.markAsync();
        SMBClientListener connectorListener = new SMBClientListener(future,
                remoteFileSystemBaseMessage -> SMBClientHelper.executeGenericAction(future));
        RemoteFileSystemConnectorFactory fileSystemConnectorFactory = new RemoteFileSystemConnectorFactoryImpl();
        VFSClientConnector connector;
        try {
            connector = fileSystemConnectorFactory.createVFSClientConnector(propertyMap, connectorListener);
        } catch (RemoteFileSystemConnectorException e) {
            throw new BallerinaSMBException(e.getMessage());
        }
        connector.send(null, FtpAction.RENAME);
        future.complete(null);
    }

    public static void rmdir(ObjectValue clientConnector, String filePath) throws BallerinaSMBException {

        String url = SMBUtil.createUrl(clientConnector, filePath);
        Map<String, String> propertyMap = new HashMap<>(
                (Map<String, String>) clientConnector.getNativeData(SmbConstants.PROPERTY_MAP));
        propertyMap.put(SmbConstants.PROPERTY_URI, url);

        CompletableFuture<Object> future = BRuntime.markAsync();
        SMBClientListener connectorListener = new SMBClientListener(future,
                remoteFileSystemBaseMessage -> SMBClientHelper.executeGenericAction(future));
        RemoteFileSystemConnectorFactory fileSystemConnectorFactory = new RemoteFileSystemConnectorFactoryImpl();
        VFSClientConnector connector;
        try {
            connector = fileSystemConnectorFactory.createVFSClientConnector(propertyMap, connectorListener);
        } catch (RemoteFileSystemConnectorException e) {
            throw new BallerinaSMBException(e.getMessage());
        }
        connector.send(null, FtpAction.RMDIR);
        future.complete(null);
    }

    public static int size(ObjectValue clientConnector, String filePath) throws BallerinaSMBException {

        String url = SMBUtil.createUrl(clientConnector, filePath);
        Map<String, String> propertyMap = new HashMap<>(
                (Map<String, String>) clientConnector.getNativeData(SmbConstants.PROPERTY_MAP));
        propertyMap.put(SmbConstants.PROPERTY_URI, url);
        propertyMap.put(SmbConstants.SMB_PASSIVE_MODE, Boolean.TRUE.toString());

        CompletableFuture<Object> future = BRuntime.markAsync();
        SMBClientListener connectorListener = new SMBClientListener(future, remoteFileSystemBaseMessage ->
                SMBClientHelper.executeSizeAction(remoteFileSystemBaseMessage, future));
        RemoteFileSystemConnectorFactory fileSystemConnectorFactory = new RemoteFileSystemConnectorFactoryImpl();
        VFSClientConnector connector;
        try {
            connector = fileSystemConnectorFactory.createVFSClientConnector(propertyMap, connectorListener);
        } catch (RemoteFileSystemConnectorException e) {
            throw new BallerinaSMBException(e.getMessage());
        }
        connector.send(null, FtpAction.SIZE);
        return 0;
    }
}
