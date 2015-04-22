/*
**The MIT License (MIT)
**Copyright (c) <2014> <CIn-UFPE>
** 
**Permission is hereby granted, free of charge, to any person obtaining a copy
**of this software and associated documentation files (the "Software"), to deal
**in the Software without restriction, including without limitation the rights
**to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
**copies of the Software, and to permit persons to whom the Software is
**furnished to do so, subject to the following conditions:
** 
**The above copyright notice and this permission notice shall be included in
**all copies or substantial portions of the Software.
** 
**THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
**IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
**FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
**AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
**LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
**OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
**THE SOFTWARE.
*/


package org.caboclo.clients;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.core.MediaType;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.caboclo.util.Credentials;
import org.caboclo.util.OSValidator;
import org.apache.commons.collections4.map.LRUMap;
import org.caboclo.service.MultiPartUpload;
import org.caboclo.service.RemoteFile;


public class OneDriveClient extends OAuthClient {

    private String accessToken = "";
    private boolean authenticated = false;
    private LRUMap<String, String> cacheNameId;
	private static final String CLIENT_ID="your-client-ID";

    public OneDriveClient() {
        this.cacheNameId = new LRUMap(4000);
    }

    public OneDriveClient(String token) {
        this.cacheNameId = new LRUMap(4000);
        accessToken = token;
        authenticated = true;
    }

    @Override
    public String getOauthURL() {		
        return "https://login.live.com/oauth20_authorize.srf?client_id="+CLIENT_ID+"&scope=wl.skydrive,wl.skydrive_update&response_type=token&redirect_uri=https://login.live.com/oauth20_desktop.srf";
    }

    @Override
    public boolean authenticate(String token) {

        Credentials cred = new Credentials();
        if (isValidToken(token)) {
            accessToken = token;

            //Remove old credentials, if they exist
            cred.removeCredentials("onedrive");

            //Save new credentials
            cred.saveCredentials("onedrive", token);

            authenticated = true;
            return true;
        } else {
            cred.removeCredentials("onedrive");
            authenticated = false;
            return false;
        }
    }

    @Override
    public void makedir(String path) throws IOException {
        //Obtain the backups folder id
        //System.out.println("mkdir: "+path);
        String parentID = null;
        try {
            parentID = getBackupsFolderId();
        } catch (JSONException ex) {
            Logger.getLogger(OneDriveClient.class.getName()).log(Level.SEVERE, "Could not get backups folder id", ex);
        }
        //Sometimes we try to create a folder in which just 
        //part of the path is created. For instance, \test\folder\ exists
        //and we try to create \test\folder\newFolder1\folder2. In this case,
        //it is necessary to test if the id exists for each part of path until
        //find the first error.        

        Client client = Client.create();
        try {
            String[] paths = null;
            if (OSValidator.isWindows()) {
                path = path.replace("/", "\\");
                paths = path.split("\\\\");
            } else {
                paths = path.split("/");
            }

            for (int i = 1; i < paths.length; i++) {
                //System.out.println(paths[i]);

                boolean foundPath = false;
                //Get files from parent folder
                WebResource webResource = client.resource("https://apis.live.net/v5.0/" + parentID + "/files/?access_token=" + accessToken);
                ClientResponse response = webResource.accept("application/json").get(ClientResponse.class);
                if (response.getStatus() != 200) {
                    throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
                }
                String output = response.getEntity(String.class);
                JSONObject json = new JSONObject(output);
                JSONArray data = json.getJSONArray("data");

                //Check if the list of files contains the current folder (paths[i]) name 
                for (int j = 0; j < data.length(); j++) {
                    JSONObject element = data.getJSONObject(j);
                    String aux = element.getString("name");

                    if (aux.equals(paths[i])) {
                        parentID = element.getString("id");
                        foundPath = true;
                    }
                }
                //Create folder if it does not exist in the current part of the path
                //The new parentID will be used in the next iteration
                if (!foundPath) {
                    //System.out.println("Create folder: "+paths[i]);
                    parentID = createFolder(paths[i], parentID);
                }
            }
        } catch (RuntimeException e) {
            Logger.getLogger(OneDriveClient.class.getName()).log(Level.SEVERE, null, e);
        } catch (JSONException ex) {
            Logger.getLogger(OneDriveClient.class.getName()).log(Level.SEVERE, null, ex);
        } catch (Exception ex) {
            Logger.getLogger(this.getClass().getName()).log(Level.WARNING, "Make dir failed", ex);
        }
    }

    public String createFolder(String name, String folderID) {
        // create a folder        
        String id = "";
        Client client = Client.create();
        WebResource webResource = client.resource("https://apis.live.net/v5.0/" + folderID + "?access_token=" + accessToken);
        String folderName;
        folderName = "{\"name\":\"" + name + "\",\"description\":\"A folder for the backup.\"}";
        ClientResponse response = webResource.type(MediaType.APPLICATION_JSON).post(ClientResponse.class, folderName);
        String output = response.getEntity(String.class);
        JSONObject json;
        try {
            json = new JSONObject(output);
            id = json.getString("id");
        } catch (JSONException ex) {
            Logger.getLogger(OneDriveClient.class.getName()).log(Level.SEVERE, "Error while creating folder", ex);
        }
        return id;
    }

    @Override
    public void putFile(File file, String path) throws IOException {
        final String BOUNDARY_STRING = "3i2ndDfv2rThIsIsPrOjEcTSYNceEMCPROTOTYPEfj3q2f";

        //We use the directory name (without actual file name) to get folder ID
        int indDirName = path.replace("/", "\\").lastIndexOf("\\");
        String targetFolderId = getFolderID(path.substring(0, indDirName));
        System.out.printf("Put file: %s\tId: %s\n", path, targetFolderId);

        if (targetFolderId == null || targetFolderId.isEmpty()) {
            return;
        }

        URL connectURL = new URL("https://apis.live.net/v5.0/" + targetFolderId + "/files?"
                + "state=MyNewFileState&redirect_uri=https://login.live.com/oauth20_desktop.srf"
                + "&access_token=" + accessToken);
        HttpURLConnection conn = (HttpURLConnection) connectURL.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY_STRING);
        // set read & write
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setRequestProperty("Connection", "Keep-Alive");
        conn.connect();
        // set body
        DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
        dos.writeBytes("--" + BOUNDARY_STRING + "\r\n");
        dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\""
                + URLEncoder.encode(file.getName(), "UTF-8") + "\"\r\n");
        dos.writeBytes("Content-Type: application/octet-stream\r\n");
        dos.writeBytes("\r\n");

        FileInputStream fileInputStream = new FileInputStream(file);
        int fileSize = fileInputStream.available();
        int maxBufferSize = 8 * 1024;
        int bufferSize = Math.min(fileSize, maxBufferSize);
        byte[] buffer = new byte[bufferSize];

        // send file
        int bytesRead = fileInputStream.read(buffer, 0, bufferSize);
        while (bytesRead > 0) {

            // Upload file part(s)
            dos.write(buffer, 0, bytesRead);

            int bytesAvailable = fileInputStream.available();
            bufferSize = Math.min(bytesAvailable, buffer.length);
            bytesRead = fileInputStream.read(buffer, 0, bufferSize);
        }
        fileInputStream.close();
        // send file end

        dos.writeBytes("\r\n");
        dos.writeBytes("--" + BOUNDARY_STRING + "--\r\n");

        dos.flush();

        BufferedReader reader = new BufferedReader(new InputStreamReader(
                conn.getInputStream()));
        String line = null;
        StringBuilder sbuilder = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            sbuilder.append(line);
        }

        String fileId = null;
        try {
            JSONObject json = new JSONObject(sbuilder.toString());
            fileId = json.getString("id");
            //System.out.println("File ID is: " + fileId);
            //System.out.println("File name is: " + json.getString("name"));
            //System.out.println("File uploaded sucessfully.");
        } catch (JSONException e) {
            Logger.getLogger(OneDriveClient.class.getName()).log(Level.WARNING, "Error uploading file " + file.getName(), e);
        }
    }

    @Override
    public void getFile(File file, String child) throws IOException {
        String source = getFileID(child);
        HttpClient httpclient = new DefaultHttpClient();
        HttpGet httpget = new HttpGet(source);
        HttpResponse response = httpclient.execute(httpget);
        //System.out.println(response.getStatusLine());
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            try {
                InputStream instream = entity.getContent();
                BufferedInputStream bis = new BufferedInputStream(instream);
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
                int inByte;
                while ((inByte = bis.read()) != -1) {
                    bos.write(inByte);
                }
                bis.close();
                bos.close();
            } catch (IOException ex) {
                throw ex;
            } catch (IllegalStateException ex) {
                httpget.abort();
                throw ex;
            }
            httpclient.getConnectionManager().shutdown();
        }
    }

    @Override
    public boolean isAuthenticated() {
        if (authenticated && isValidToken(accessToken)) {
            return true;
        } else {
            System.out.println("check credentials file");
            Credentials cred = new Credentials();
            String token = cred.checkCredentialsFile("onedrive");
            if (token != null && !token.isEmpty() && isValidToken(token)) {
                accessToken = token;
                authenticated = true;
                return true;
            } else {
                authenticated = false;
                return false;
            }
        }
    }

    public boolean isValidToken(String token) {
        boolean isValid = false;
        try {
            Client client = Client.create();
            WebResource webResource = client.resource("https://apis.live.net/v5.0/me/skydrive/files/?access_token=" + token);
            ClientResponse response = webResource.accept("application/json").get(ClientResponse.class);
            if (response.getStatus() == 401 || response.getStatus() != 200) {
                return false;
            } else {
                return true;
            }
        } catch (RuntimeException e) {
            Logger.getLogger(OneDriveClient.class.getName()).log(Level.SEVERE, null, e);
        }
        return isValid;
    }

    public String getBackupsFolderId() throws JSONException {
        return "me/skydrive";
    }

    //Obtains the ID of a folder
    public String getFolderID(String folderPath) {
        //first we check in the LRUcache, if the ID is not there yet, then we look it up on the server
        String cachedID = cacheNameId.get(folderPath);
        if (cachedID != null) {
            return cachedID;
        }
        String originalPath = folderPath;
        String parentID = null;
        boolean foundPath = false;
        try {
            parentID = getBackupsFolderId();
        } catch (JSONException ex) {
            Logger.getLogger(OneDriveClient.class.getName()).log(Level.SEVERE, "Could not get the folder id", ex);
        }
        try {

            String[] paths = null;
            if (OSValidator.isWindows()) {
                folderPath = folderPath.replace("/", "\\");
                paths = folderPath.split("\\\\");
            } else {
                paths = folderPath.split("/");
            }

            for (int i = 1; i < paths.length; i++) {
                //System.out.println("debug:" + paths[i]);
                foundPath = false;
                //Get files from parent folder
                Client client = Client.create();
                WebResource webResource = client.resource("https://apis.live.net/v5.0/" + parentID + "/files/?access_token=" + accessToken);
                ClientResponse response = webResource.accept("application/json").get(ClientResponse.class);

                //printResponse(response);
                String output = response.getEntity(String.class);
                //System.out.println(output);
                JSONObject json = new JSONObject(output);
                JSONArray data = json.getJSONArray("data");

                //Check if the list of files contains the current folder (paths[i]) name 
                for (int j = 0; j < data.length(); j++) {
                    JSONObject element = data.getJSONObject(j);
                    String aux = element.getString("name");
                    String type = element.getString("type");
                    if (aux.equals(paths[i]) && type.equals("folder")) {
                        parentID = element.getString("id");
                        cacheNameId.put(originalPath, parentID);
                        foundPath = true;
                    }
                }
                //Create folder if it is the backups folder and it does not exist
                //The new parentID will be used in the next iteration
                if (folderPath.equals("/backups") && !foundPath) {
                    parentID = createFolder("backups", parentID);
                    cacheNameId.put("/backups", parentID);
                    foundPath = true;
                    return parentID;
                }
            }
        } catch (RuntimeException e) {
            Logger.getLogger(OneDriveClient.class.getName()).log(Level.SEVERE, null, e);
        } catch (JSONException ex) {
            Logger.getLogger(OneDriveClient.class.getName()).log(Level.SEVERE, null, ex);
            Credentials cred = new Credentials();
            cred.removeCredentials("onedrive");
            this.authenticated = false;
        } catch (Exception ex) {
            Logger.getLogger(this.getClass().getName()).log(Level.WARNING, "Make dir failed", ex);
        }

        if (!foundPath) {
            return "";
        }
        return parentID;
    }

//inside the folder 'backups', returns the id of the given name of the backup folder
    public String getBackupId(String name) {
        String id = null;
        String backupFolderId = null;
        try {
            backupFolderId = getBackupsFolderId();
        } catch (JSONException ex) {
            Logger.getLogger(OneDriveClient.class.getName()).log(Level.SEVERE, null, ex);
        }
        try {
            Client client = Client.create();
            WebResource webResource = client.resource("https://apis.live.net/v5.0/" + backupFolderId + "/files/?access_token=" + accessToken);
            ClientResponse response = webResource.accept("application/json").get(ClientResponse.class);
            String output = response.getEntity(String.class);
            JSONObject json = new JSONObject(output.trim());
            //printResponse(response);
            JSONArray data = json.getJSONArray("data");
            for (int i = 0; i < data.length(); i++) {
                JSONObject element = data.getJSONObject(i);
                String aux = element.getString("name");
                if (aux.equals(name)) {
                    id = element.getString("id");
                    return id;
                }
            }
        } catch (RuntimeException e) {
            Logger.getLogger(OneDriveClient.class.getName()).log(Level.SEVERE, null, e);
        } catch (JSONException ex) {
            Logger.getLogger(OneDriveClient.class.getName()).log(Level.SEVERE, null, ex);
        }
        return id;
    }

    @Override
    public boolean isFolder(String folderPath) throws IOException {
        String parentID = null;
        boolean isFolder = false;

        try {
            parentID = getBackupsFolderId();
        } catch (JSONException ex) {
            Logger.getLogger(OneDriveClient.class.getName()).log(Level.SEVERE, "Could not get backups folder id", ex);
        }
        try {
            String[] paths = null;
            if (OSValidator.isWindows()) {
                folderPath = folderPath.replace("/", "\\");
                paths = folderPath.split("\\\\");
            } else {
                paths = folderPath.split("/");
            }

            for (int i = 0; i < paths.length; i++) {
                //System.out.println("debug:" + paths[i]);

                //Get files from parent folder
                Client client = Client.create();
                WebResource webResource = client.resource("https://apis.live.net/v5.0/" + parentID + "/files/?access_token=" + accessToken);
                ClientResponse response = webResource.accept("application/json").get(ClientResponse.class);

                //printResponse(response);
                String output = response.getEntity(String.class);
                //System.out.println(output);
                JSONObject json = new JSONObject(output);
                JSONArray data = json.getJSONArray("data");

                //Check if the list of files contains the current folder (paths[i]) name 
                for (int j = 0; j < data.length(); j++) {
                    JSONObject element = data.getJSONObject(j);
                    String aux = element.getString("name");
                    String type = element.getString("type");
                    if (aux.equals(paths[i]) && type.equals("folder")) {
                        isFolder = true;
                        parentID = element.getString("id");
                    } else if (aux.equals(paths[i]) && !type.equals("folder")) { //type.equals("file")) {
                        isFolder = false;
                        parentID = element.getString("id");

                    }
                }
            }
        } catch (RuntimeException e) {
            Logger.getLogger(OneDriveClient.class.getName()).log(Level.SEVERE, null, e);
        } catch (JSONException ex) {
            Logger.getLogger(OneDriveClient.class.getName()).log(Level.SEVERE, null, ex);
        }

        return isFolder;
    }

//prints the response of the Server (test purposes only)
    public String printResponse(ClientResponse response) {
        if (response.getStatus() == 201) {
            System.out.println("Created folder.");
            return "201";
        } else if (response.getStatus() == 204) {
            System.out.println("Delete operation successful.");
            return "204";
        } else if (response.getStatus() != 200) {
            throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
        }
        System.out.println("Output from Server ... \n");
        String output = response.getEntity(String.class);
        System.out.println(output);
        return output;
    }

    //Obtains the ID of a file
    public String getFileID(String filePath) {
        //first we check in the LRUcache, if the ID is not there yet, then we look it up on the server
        String cachedID = cacheNameId.get(filePath);
        if (cachedID != null) {
            return cachedID;
        }
        String originalPath = filePath;
        String parentID = null;
        String retorno = null;
        try {
            parentID = getBackupsFolderId();
        } catch (JSONException ex) {
            Logger.getLogger(OneDriveClient.class.getName()).log(Level.SEVERE, "Could not get backups folder id", ex);
        }
        try {
            String[] paths = null;
            if (OSValidator.isWindows()) {
                filePath = filePath.replace("/", "\\");
                paths = filePath.split("\\\\");
            } else {
                paths = filePath.split("/");
            }

            for (int i = 1; i < paths.length; i++) {
                //  System.out.println("debug:" + paths[i]);
                boolean foundPath = false;
                //Get files from parent folder
                Client client = Client.create();
                WebResource webResource = client.resource("https://apis.live.net/v5.0/" + parentID + "/files/?access_token=" + accessToken);
                ClientResponse response = webResource.accept("application/json").get(ClientResponse.class);
                //printResponse(response);
                String output = response.getEntity(String.class);
                //System.out.println(output);
                JSONObject json = new JSONObject(output);
                JSONArray data = json.getJSONArray("data");

                //Check if the list of files contains the current folder (paths[i]) name 
                for (int j = 0; j < data.length(); j++) {
                    JSONObject element = data.getJSONObject(j);
                    String aux = element.getString("name");
                    String type = element.getString("type");
                    // System.out.println(aux);
                    if (aux.equals(paths[i])) {
                        parentID = element.getString("id");
                        cacheNameId.put(originalPath, parentID);
                        foundPath = true;
                    }
                    if (aux.equals(paths[i]) && !type.equals("folder")) {
                        retorno = element.getString("source");
                    }
                }
            }
        } catch (RuntimeException e) {
            Logger.getLogger(OneDriveClient.class.getName()).log(Level.SEVERE, null, e);
        } catch (JSONException ex) {
            Logger.getLogger(OneDriveClient.class.getName()).log(Level.SEVERE, null, ex);
        }

        System.out.println("Return: " + retorno);

        return retorno;
    }

//the methods bellow are not supported by Microsoft OneDrive REST API    
    @Override
    public MultiPartUpload initializeUpload(File file, String fileName) {
        throw new UnsupportedOperationException("Not supported by OneDrive.");
    }

    @Override
    public void sendNextPart(MultiPartUpload mpu) {
        throw new UnsupportedOperationException("Not supported by OneDrive.");
    }

    @Override
    public void finalizeUpload(MultiPartUpload mpu) {
        throw new UnsupportedOperationException("Not supported by OneDrive.");
    }

    @Override
    public String getBearerToken() {
        return accessToken;
    }

    @Override
    public List<RemoteFile> getChildren(String folderName) throws IOException {
        ArrayList<RemoteFile> list = new ArrayList<>();
        String folderId = getFolderID(folderName);
        if (folderId == null || folderId.isEmpty()) {
            return list;
        }
        try {
            Client client = Client.create();
            WebResource webResource = client.resource("https://apis.live.net/v5.0/" + folderId + "/files/?access_token=" + accessToken);
            ClientResponse response = webResource.accept("application/json").get(ClientResponse.class);
            String output = response.getEntity(String.class);
            JSONObject json = new JSONObject(output);

            JSONArray data = json.getJSONArray("data");
            for (int i = 0; i < data.length(); i++) {
                JSONObject element = data.getJSONObject(i);
                String name = element.getString("name");
                String type = element.getString("type");
                long size = element.getLong("size");
                boolean isDirectory = type.equals("folder");

                size = isDirectory ? 0 : size;

                RemoteFile file = null;
                
                if (isDirectory) {
                    file = new RemoteFile(folderName + "/" + name, isDirectory, size);
                } else {
                    String url = element.getString("source");
                    file = new RemoteFile(folderName + "/" + name, isDirectory, size, url);
                }

                list.add(file);
            }
        } catch (RuntimeException e) {
            Logger.getLogger(OneDriveClient.class.getName()).log(Level.SEVERE, null, e);
        } catch (JSONException ex) {
            Logger.getLogger(OneDriveClient.class.getName()).log(Level.SEVERE, null, ex);
        }

        return list;
    }

    @Override
    public void removeFolder(String remoteFolder) throws IOException {
        String folderId = getFolderID(remoteFolder);
        if (folderId == null || folderId.isEmpty()) {
            System.out.println("Could not find folder ID to delete");
            return;
        }
        try{            
            Client client = Client.create();
            WebResource webResource = client.resource("https://apis.live.net/v5.0/" + folderId + "?access_token=" + accessToken);
            ClientResponse response = webResource.delete(ClientResponse.class);
            printResponse(response);
        }catch (RuntimeException ex){
            throw new IOException("Error while deleting folder "+remoteFolder,ex);
        }
        
    }
    
}
