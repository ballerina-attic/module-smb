Connects to a Samba server using Ballerina.

# Module Overview

The `wso2/smb` module provides an SMB client and an SMB server listener implementation to facilitate an SMB connection 
to a remote location.

**SMB Client**

The `smb:Client` connects to an SMB server and performs various operations on the files. Currently, it supports the 
generic SMB operations; `get`, `delete`, `put`, `append`, `mkdir`, `rmdir`, `isDirectory`,  `rename`, `size`, and
 `list`.

An SMB client endpoint is defined using the parameters `protocol` and `host`, and optionally the `port` and 
`secureSocket`. Authentication configuration can be configured using the `secureSocket` parameter for basicAuth, 
private key, or TrustStore/Keystore.

**SMB Listener**

The `smb:Listener` is used to listen to a remote SMB location and trigger an event of `WatchEvent` type, when new 
files are added to or deleted from the directory. The `fileResource` function is invoked when a new file is added 
and/or deleted.

An SMB listener endpoint is defined using the mandatory parameters `protocol`, `host` and  `path`. Authentication 
configuration can be done using `secureSocket` and polling interval can be configured using `pollingInterval`. 
Default polling interval is 60 seconds.

The `fileNamePattern` parameter can be used to define the type of files the SMB listener endpoint will listen to. 
For instance, if the listener should get invoked for text files, the value `(.*).txt` can be given for the config.

## Compatibility

|                             |           Version           |
|:---------------------------:|:---------------------------:|
| Ballerina Language          |            1.0.0            |

## Samples

**SMB Listener Sample**

The SMB Listener can be used to listen to a remote directory. It will keep listening to the specified directory and 
periodically notify the files that are added to and deleted from the server.

```ballerina
import ballerina/log;
import wso2/smb;

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
    resource function onFileChange(smb:WatchEvent fileEvent) {

        foreach smb:FileInfo addedFile in fileEvent.addedFiles {
            log:printInfo("Added file path: " + addedFile.path);
        }
        foreach string deletedFile in fileEvent.deletedFiles {
            log:printInfo("Deleted file path: " + deletedFile);
        }
    }
}
```

**SMB Client Sample**

The SMB Client Connector can be used to connect to an SMB server and perform I/O operations.

```ballerina
import ballerina/log;
import ballerina/io;
import wso2/smb;

// Define Samba client configuration
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

// Create Samba client
smb:Client smbClient = new(smbConfig);
    
public function main() {
    // Create a folder in remote server
    error? mkdirResponse = smbClient->mkdir("<The directory path>");
    if (mkdirResponse is error) {
        log:printError("Error occured in creating directory.", mkdirResponse);
        return;
    }
    
    // Upload file to a remote server
    io:ReadableByteChannel|error summaryChannel = io:openReadableFile("<The local data source path>");
    if(summaryChannel is io:ReadableByteChannel){
        error? putResponse = smbClient->put("<The resource path>", summaryChannel);   
        if(putResponse is error) {
            log:printError("Error occured in uploading content.", putResponse);
            return;
        }
    }
    
    // Get the size of a remote file
    var sizeResponse = smbClient->size("<The resource path>");
    if (sizeResponse is int) {
        log:printInfo("File size: " + sizeResponse.toString());
    } else {
        log:printError("Error occured in retrieving size.", sizeResponse);
        return;
    }
    
    // Read content of a remote file
    var getResponse = smbClient->get("<The file path>");
    if (getResponse is io:ReadableByteChannel) {
        io:ReadableCharacterChannel? characters = new io:ReadableCharacterChannel(getResponse, "utf-8");
        if (characters is io:ReadableCharacterChannel) {
            var output = characters.read(<No of characters to read>);
            if (output is string) {
                log:printInfo("File content: " + output);
            } else {
                log:printError("Error occured in retrieving content", output);
                return;
            }
            var closeResult = characters.close();
            if (closeResult is error) {
                log:printError("Error occurred while closing the channel", closeResult);
            }
        }
    } else {
        log:printError("Error occured in retrieving content", getResponse);
        return;
    }
    
    // Rename or move remote file to a another remote location in a same SMB server
    error? renameResponse = smbClient->rename("<The source file path>", "<The destination file path>");
    if (renameResponse is error) {
        log:printError("Error occurred while renaming the file", renameResponse);
    }
    
    // Delete remote file
    error? deleteResponse = smbClient->delete("<The resource path>");
    if (deleteResponse is error) {
        log:printError("Error occurred while deleting a file", deleteResponse);
    }

    // Remove directory from remote server
    var rmdirResponse = smbClient->rmdir("<The directory path>");
    if (rmdirResponse is error) {
        io:println("Error occured in removing directory.", rmdirResponse); 
    }
}
```
