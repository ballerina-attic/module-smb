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

package org.wso2.ei.b7a.smb.server;

import org.ballerinalang.jvm.BRuntime;
import org.ballerinalang.jvm.values.MapValue;
import org.ballerinalang.jvm.values.ObjectValue;
import org.wso2.ei.b7a.smb.util.BallerinaSMBException;
import org.wso2.ei.b7a.smb.util.SMBUtil;
import org.wso2.ei.b7a.smb.util.SmbConstants;
import org.wso2.transport.remotefilesystem.Constants;
import org.wso2.transport.remotefilesystem.RemoteFileSystemConnectorFactory;
import org.wso2.transport.remotefilesystem.exception.RemoteFileSystemConnectorException;
import org.wso2.transport.remotefilesystem.impl.RemoteFileSystemConnectorFactoryImpl;
import org.wso2.transport.remotefilesystem.server.connector.contract.RemoteFileSystemServerConnector;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper class for listener functions
 */
public class SMBListenerHelper {

    private SMBListenerHelper() {
        // private constructor
    }

    public static RemoteFileSystemServerConnector register(ObjectValue smbListener,
            MapValue<Object, Object> serviceEndpointConfig, ObjectValue service, String name)
            throws BallerinaSMBException {

        try {
            Map<String, String> paramMap = getServerConnectorParamMap(serviceEndpointConfig);
            RemoteFileSystemConnectorFactory fileSystemConnectorFactory = new RemoteFileSystemConnectorFactoryImpl();
            final SMBListener listener = new SMBListener(BRuntime.getCurrentRuntime(), service);
            if (name == null || name.isEmpty()) {
                name = service.getType().getName();
            }
            RemoteFileSystemServerConnector serverConnector = fileSystemConnectorFactory
                    .createServerConnector(name, paramMap, listener);
            smbListener.addNativeData(SmbConstants.SMB_SERVER_CONNECTOR, serverConnector);
            // This is a temporary solution
            serviceEndpointConfig.addNativeData(SmbConstants.SMB_SERVER_CONNECTOR, serverConnector);
            return serverConnector;
        } catch (RemoteFileSystemConnectorException e) {
            throw new BallerinaSMBException("Unable to initialize the SMB listener: " + e.getMessage(), e);
        }
    }

    private static Map<String, String> getServerConnectorParamMap(MapValue serviceEndpointConfig)
            throws BallerinaSMBException {

        Map<String, String> params = new HashMap<>(12);

        MapValue secureSocket = serviceEndpointConfig.getMapValue(SmbConstants.ENDPOINT_CONFIG_SECURE_SOCKET);
        String url = SMBUtil.createUrl(serviceEndpointConfig);
        params.put(Constants.URI, url);
        addStringProperty(serviceEndpointConfig, params
        );
        if (secureSocket != null) {
            final MapValue privateKey = secureSocket.getMapValue(SmbConstants.ENDPOINT_CONFIG_PRIVATE_KEY);
            if (privateKey != null) {
                final String privateKeyPath = privateKey.getStringValue(SmbConstants.ENDPOINT_CONFIG_PATH);
                if (privateKeyPath != null && !privateKeyPath.isEmpty()) {
                    params.put(Constants.IDENTITY, privateKeyPath);
                    final String privateKeyPassword = privateKey.getStringValue(SmbConstants.ENDPOINT_CONFIG_PASS_KEY);
                    if (privateKeyPassword != null && !privateKeyPassword.isEmpty()) {
                        params.put(Constants.IDENTITY_PASS_PHRASE, privateKeyPassword);
                    }
                }
            }
        }
        params.put(Constants.USER_DIR_IS_ROOT, String.valueOf(false));
        params.put(Constants.AVOID_PERMISSION_CHECK, String.valueOf(true));
        params.put(Constants.PASSIVE_MODE, String.valueOf(true));
        return params;
    }

    private static void addStringProperty(MapValue config, Map<String, String> params) {

        final String value = config.getStringValue(SmbConstants.ENDPOINT_CONFIG_FILE_PATTERN);
        if (value != null && !value.isEmpty()) {
            params.put(Constants.FILE_NAME_PATTERN, value);
        }
    }

    public static void poll(MapValue<Object, Object> config) throws BallerinaSMBException {

        RemoteFileSystemServerConnector connector = (RemoteFileSystemServerConnector) config.
                getNativeData(SmbConstants.SMB_SERVER_CONNECTOR);
        try {
            connector.poll();
        } catch (RemoteFileSystemConnectorException e) {
            throw new BallerinaSMBException(e.getMessage());
        }
    }
}
