[![Build Status](https://travis-ci.org/wso2-ballerina/module-smb.svg?branch=master)](https://travis-ci.org/wso2-ballerina/module-smb)

## Module Overview

The `wso2/smb` module provides an SMB client and an SMB server listener implementation to facilitate an SMB connection 
to a remote location.

The following sections provide you details on how to use the SMB connector.

- [Compatibility](#compatibility)
- [Feature Overview](#feature-overview)
- [Getting Started](#getting-started)
- [Samples](#samples)

## Compatibility

| Ballerina Language Version  |
|:---------------------------:|
|  1.0.0                     |

## Feature Overview

### SMB Client
The `smb:Client` connects to an SMB server and performs various operations on the files. Currently, it supports the 
generic SMB operations; `get`, `delete`, `put`, `append`, `mkdir`, `rmdir`, `isDirectory`,  `rename`, `size`, and
 `list`.

An SMB client endpoint is defined using the parameters `protocol` and `host`, and optionally the `port` and 
`secureSocket`. Authentication configuration can be configured using the `secureSocket` parameter for basicAuth, 
private key, or TrustStore/Keystore.

### SMB Listener
The `smb:Listener` is used to listen to a remote SMB location and trigger an event of `WatchEvent` type, when new 
files are added to or deleted from the directory. The `fileResource` function is invoked when a new file is added 
and/or deleted.

An SMB listener endpoint is defined using the mandatory parameters `protocol`, `host` and  `path`. Authentication 
configuration can be done using `secureSocket` and polling interval can be configured using `pollingInterval`. 
Default polling interval is 60 seconds.

The `fileNamePattern` parameter can be used to define the type of files the SMB listener endpoint will listen to. 
For instance, if the listener should get invoked for text files, the value `(.*).txt` can be given for the config.

## Getting Started

### Prerequisites
Download and install [Ballerina](https://ballerinalang.org/downloads/).

### Pull the Module
You can pull the SMB module from Ballerina Central using the command:
```ballerina
$ ballerina pull wso2/smb
```

## Samples

### SMB Listener Sample
The SMB Listener can be used to listen to a remote directory. It will keep listening to the specified directory and 
periodically notify the file addition and deletion.

```ballerina
import wso2/smb;
import ballerina/log;

listener smb:Listener remoteServer = new({
    protocol: smb:SMB,
    host: "<The SMB host>",
    secureSocket: {
        basicAuth: {
            username: "<The SMB username>",
            password: "<The SMB passowrd>"
        }
    },
    port: <The SMB port>,
    path: "<The remote SMB direcotry location>",
    pollingInterval: <Polling interval>,
    fileNamePattern: "<File type>"
});

service smbServerConnector on remoteServer {
    resource function fileResource(smb:WatchEvent m) {

        foreach smb:FileInfo v1 in m.addedFiles {
            log:printInfo("Added file path: " + v1.path);
        }
        foreach string v1 in m.deletedFiles {
            log:printInfo("Deleted file path: " + v1);
        }
    }
}
```

### SMB Client Sample
The SMB Client Connector can be used to connect to an SMB server and perform I/O operations.

```ballerina
import wso2/smb;
import ballerina/io;
import ballerina/log;

smb:ClientEndpointConfig smbConfig = {
    protocol: smb:SMB,
    host: "<The SMB host>",
    port: <The SMB port>,
    secureSocket: {
        basicAuth: {
            username: "<The SMB username>",
            password: "<The SMB passowrd>"
        }
    }
};
smb:Client smbClient = new(smbConfig);
    
public function main() {
    // To create a folder in remote server.
    error? dirCreErr = smbClient->mkdir("<The directory path>");
    if (dirCreErr is error) {
        log:printError("Error occured in creating directory.", dirCreErr);
        return;
    }
    
    // Upload file to a remote server.
    io:ReadableByteChannel|error summaryChannel = io:openReadableFile("<The local data source path>");
    if(summaryChannel is io:ReadableByteChannel){
        error? filePutErr = smbClient->put("<The resource path>", summaryChannel);   
        if(filePutErr is error) {
            log:printError("Error occured in uploading content.", filePutErr);
            return;
        }
    }
    
    // Get the size of a remote file.
    var size = smbClient->size("<The resource path>");
    if (size is int) {
        log:printInfo("File size: " + size.toString());
    } else {
        log:printError("Error occured in retrieving size.", size);
        return;
    }
    
    // Read content of a remote file.
    var getResult = smbClient->get("<The file path>");
    if (getResult is io:ReadableByteChannel) {
        io:ReadableCharacterChannel? characters = new io:ReadableCharacterChannel(getResult, "utf-8");
        if (characters is io:ReadableCharacterChannel) {
            var output = characters.read(<No of characters to read>);
            if (output is string) {
                log:printInfo("File content: "+ output);
            } else {
                log:printError("Error occured in retrieving content.", output);
                return;
            }
            var closeResult = characters.close();
            if (closeResult is error) {
                log:printError("Error occurred while closing the channel", err);
            }
        }
    } else {
        log:printError("Error occured in retrieving content.", getResult);
        return;
    }
    
    // Rename or move remote file to a another remote location in a same SMB server.
    error? renameErr = smbClient->rename("<The source file path>", "<The destination file path>");
    if (renameErr is error) {
        log:printError("Error occurred while renaming the file", renameErr);
    }
    
    // Delete remote file.
    error? fileDelCreErr = smbClient->delete("<The resource path>");
    if (fileDelCreErr is error) {
        log:printError("Error occurred while deleting a file", fileDelCreErr);
    }

    // Remove directory from remote server.
    var result = smbClient->rmdir("<The directory path>");
    if (result is error) {
        io:println("Error occured in removing directory.", result); 
    }
}
```
