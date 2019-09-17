// Copyright (c) 2019 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/log;

int addedFileCount = 0;
int deletedFileCount = 0;

listener Listener remoteServer = new({
    protocol: SMB,
    host: "127.0.0.1",
    secureSocket: {
        basicAuth: {
            username: "smbuser",
            password: "smbpwd"
        }
    },
    port: 445,
    path: "/sambaIn",
    pollingInterval: 2000,
    fileNamePattern: "(.*).txt"
});

service smbServerConnector on remoteServer {
    resource function fileResource(WatchEvent event) {
        addedFileCount = <@untainted> event.addedFiles.length();
        deletedFileCount = <@untainted> event.deletedFiles.length();

        foreach FileInfo v1 in event.addedFiles {
            log:printInfo("Added file path: " + v1.path);
        }
        foreach string v1 in event.deletedFiles {
            log:printInfo("Deleted file path: " + v1);
        }
    }
}
