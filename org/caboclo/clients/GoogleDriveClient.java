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

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Children;
import com.google.api.services.drive.Drive.Files;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.ChildList;
import com.google.api.services.drive.model.ChildReference;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.collections4.map.LRUMap;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.caboclo.service.Constants;
import org.caboclo.service.MultiPartUpload;
import org.caboclo.service.RemoteFile;
import org.caboclo.util.Credentials;
import org.caboclo.util.OSValidator;


public class GoogleDriveClient extends OAuthClient {

    private Files files;
    private Drive service;
    private String rootId = "";
    private static final String CLIENT_ID = "your-client-id";
    private static final String CLIENT_SECRET = "your-client-secret";
    private static final String REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob";
    private LRUMap<String, String> cacheNameId;
    private LRUMap<String, String> cacheIdName;
    private HttpTransport httpTransport;
    private JsonFactory jsonFactory;
    private GoogleAuthorizationCodeFlow flow;
    private boolean authenticated;
    private String token;

    public GoogleDriveClient() {
        this.cacheNameId = new LRUMap(2000);
        this.cacheIdName = new LRUMap(2000);
        authenticated = false;
        httpTransport = new NetHttpTransport();
        jsonFactory = new JacksonFactory();
    }

    public GoogleDriveClient(String bearerToken) {
        this();

        GoogleCredential credential = new GoogleCredential()
                .setAccessToken(bearerToken);
        // Create a new authorized API client
        service = new Drive.Builder(httpTransport, jsonFactory, credential)
                .build();
        files = service.files();
        
        this.token = bearerToken;

        authenticated = true;
    }

    @Override
    public String getOauthURL() {
        flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport,
                jsonFactory, CLIENT_ID, CLIENT_SECRET,
                Arrays.asList(DriveScopes.DRIVE)).setAccessType("online")
                .setApprovalPrompt("auto").build();
        return flow.newAuthorizationUrl().setRedirectUri(REDIRECT_URI).build();
    }

    @Override
    public String getBearerToken() {
        return token;
    }

    @Override
    public boolean authenticate(String token) {
        try {

            GoogleTokenResponse response = flow.newTokenRequest(token)
                    .setRedirectUri(REDIRECT_URI).execute();

            GoogleCredential credential = new GoogleCredential()
                    .setFromTokenResponse(response);

            // Create a new authorized API client
            service = new Drive.Builder(httpTransport, jsonFactory, credential)
                    .build();
            files = service.files();

            Credentials cred = new Credentials();

            //Remove old credentials, if they exist
            cred.removeCredentials(Credentials.GOOGLE);

            //Save new credentials
            cred.saveCredentials(Credentials.GOOGLE, credential.getAccessToken());

            this.token = credential.getAccessToken();
            
            authenticated = true;

            return true;

        } catch (Exception ex) {
            Logger.getLogger(GoogleDriveClient.class.getName()).log(Level.SEVERE, null, ex);
        }

        return false;

    }

    @Override
    public void makedir(String path) throws IOException {

        //Obtain the root id
        if (rootId.equals("")) {
            try {
                About about;
                about = service.about().get().execute();
                rootId = about.getRootFolderId();
            } catch (IOException ex) {
                Logger.getLogger(GoogleDriveClient.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        mkDirs(path, rootId);

    }

    @Override
    public void putFile(java.io.File file, String path) throws IOException {

        path = path.replace("\\", "/");

        int pos = path.lastIndexOf("/");
        if (pos > 0) {
            path = path.substring(0, pos);
        }

        String parentId = this.getId(path);

        //The path does not exists! 
        //FIXME-It is necessary to construct the path based on the last
        //valid id
        if (parentId.equals("root") && !path.equals("")
                && !path.equals("/") && !path.equals("\\")) {
            this.makedir(path);
            parentId = this.getId(path);
        }

        try {
            String mimeType = URLConnection.guessContentTypeFromName(file.getName());

            mimeType = (mimeType == null) ? "application/octet-stream"
                    : mimeType;

            File body = new File();
            body.setTitle(getTitle(file.getName()));
            body.setMimeType(mimeType);

            if (parentId != null || !parentId.equals("")) {
                body.setParents(Arrays.asList(new ParentReference()
                        .setId(parentId)));
            }

            FileContent mediaContent = new FileContent(mimeType, file);

            File f = service.files().insert(body, mediaContent).execute();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private String getId(String fileName) throws IOException {

        fileName = fileName.replace("/", "\\");

        //first of all test the cache
        String cachedID = cacheNameId.get(fileName);
        if (cachedID != null) {
            return cachedID;
        }

        String s[] = fileName.split("\\\\");

        String parent = "root";
        String currentPath = "";

        for (int i = 1; i < s.length; i++) {

            currentPath = currentPath + "\\" + s[i];

            cachedID = cacheNameId.get(currentPath);
            if (cachedID != null) {
                parent = cachedID;
                continue;
            }

            FileList list = files
                    .list()
                    .setQ("title = '" + s[i] + "'" + " and '" + parent
                            + "' in parents").execute();

            if (list.getItems().isEmpty()) {
                return "root";
            }

            File file = list.getItems().get(0);
            cacheNameId.put(currentPath, file.getId());

            parent = file.getId();

        }

        return parent;
    }

    private String getPathById(String id) {
        try {

            //first of all test the cache
            String cachedName = cacheIdName.get(id);
            if (cachedName != null) {
                return cachedName;
            }

            File currentFile;
            String parent = id;
            String path = "";

            String backupsId = getId("/backups");
            ArrayList<String> ids = new ArrayList<String>();
            ids.add(id);

            do {
                cachedName = cacheIdName.get(parent);

                if (cachedName != null) {
                    path = cachedName + path;
                    cacheIdName.put(id, path);
                    return path;
                }

                currentFile = files.get(parent).execute();
                if (currentFile != null) {
                    if (OSValidator.isWindows()) {
                        path = currentFile.getTitle() + "\\" + path;
                    } else {
                        path = currentFile.getTitle() + "/" + path;
                    }
                        parent = currentFile.getParents().get(0).getId();

                        //Insert the list of ids starting from the last id.
                        ids.add(parent);
                    }
            } while (!parent.equals(backupsId));

            String currentPath = "";
            if (OSValidator.isWindows()) {
                currentPath = "\\backups\\" + path;
            } else {
                currentPath = "/backups/" + path;
            }

            currentPath = currentPath.substring(0, currentPath.length() - 1);

            //update the cached ids
            for (String currentId : ids) {
                if (OSValidator.isWindows()) {
                    cacheIdName.put(currentId, currentPath + "\\");
                    currentPath = currentPath.substring(0, currentPath.length() - 1);
                    currentPath = currentPath.substring(0, currentPath.lastIndexOf("\\"));
                } else {
                    cacheIdName.put(currentId, currentPath + "/");
                    currentPath = currentPath.substring(0, currentPath.length() - 1);
                    currentPath = currentPath.substring(0, currentPath.lastIndexOf("/"));
                }
            }

            if (OSValidator.isWindows()) {
                return "\\backups\\" + path;
            } else {
                return "/backups/" + path;
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return "";
    }

    private File createFolder(String title, String parentId)
            throws IOException {

        File body = new File();
        body.setTitle(title);

        body.setParents(Arrays.asList(new ParentReference().setId(parentId)));
        body.setMimeType("application/vnd.google-apps.folder");
        File file = service.files().insert(body).execute();
        return file;

    }

    private File mkDirs(String path, String id) throws IOException {

        File folder = null;
        String currentPath = "";

        //Sometimes we try to create a folder in which just 
        //part of the path is created. For instance, \test\folder\ exists
        //and we try to create \test\folder\newFolder1\folder2. In this case,
        //it is necessary to test if the id exists for each part of path until
        //find the first error.        
        boolean shouldTest = true;

        try {
            path = path.replace("/", "\\");
            String[] paths = path.split("\\\\");

            for (int i = 1; i < paths.length; i++) {

                String pathi = paths[i];

                if (shouldTest) {
                    currentPath = currentPath + "\\" + pathi;
                    String currentId = this.getId(currentPath);
                    //There is a folder for this part of path
                    if (currentId.equals("root") == false) {
                        id = currentId;
                        continue;
                    } else {
                        shouldTest = false;
                    }
                }

                folder = createFolder(pathi, id);
                id = folder.getId();

            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return folder;
    }

    private String getTitle(String fileName) {
        int pos = fileName.lastIndexOf("/");

        if (pos > 0) {
            return fileName.substring(pos + 1);
        } else {
            return fileName;
        }
    }

    @Override
    public boolean isAuthenticated() {
        //System.out.println("isAuth?");
        if (authenticated) {
            return true;
        } else {
            System.out.println("check credentials file");
            Credentials cred = new Credentials();           
            token = cred.checkCredentialsFile(Credentials.GOOGLE);

            if (token != null && !token.isEmpty()) {
                GoogleCredential credential = new GoogleCredential()
                        .setAccessToken(token);
                // Create a new authorized API client
                service = new Drive.Builder(httpTransport, jsonFactory, credential)
                        .build();
                if (isValidToken()){
                    files = service.files();
                    authenticated = true;
                    return true;
                }else{
                    cred.removeCredentials(Credentials.GOOGLE);
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    @Override
    public boolean isFolder(String folderName) throws IOException {
        String id = this.getId(folderName);
        File file = files.get(id).execute();

        if (file.getMimeType().equals(
                "application/vnd.google-apps.folder")) {
            return true;
        }

        return false;
    }

    private InputStream downloadFile(File file) {
        if (file.getDownloadUrl() != null && file.getDownloadUrl().length() > 0) {
            try {

                HttpResponse resp = service.getRequestFactory()
                        .buildGetRequest(new GenericUrl(file.getDownloadUrl()))
                        .execute();

                return resp.getContent();
            } catch (IOException e) {
                // An error occurred.
                e.printStackTrace();
                return null;
            }
        } else {
            // The file doesn't have any content stored on Drive.
            return null;
        }
    }

    private void writeOnDisk(InputStream input, java.io.File localFile) throws FileNotFoundException, IOException {

        OutputStream output = null;

        try {
            output = new FileOutputStream(localFile);

            byte[] b = new byte[2048];
            int length;

            while ((length = input.read(b)) != -1) {
                output.write(b, 0, length);
            }

        } finally {
            try {
                if (output != null) {
                    output.close();
                }
                if (input != null) {
                    input.close();
                }
            } catch (IOException e) {
            }
        }

    }

    @Override
    public void getFile(java.io.File file, String child) throws IOException {
        File fileGoogleDrive = files.get(this.getId(child)).execute();

        BufferedInputStream bis = new BufferedInputStream(
                downloadFile(fileGoogleDrive));

        writeOnDisk(bis, file);
    }

    @Override
    public MultiPartUpload initializeUpload(java.io.File file, String path) {

        String parentId = "root";

        try {
            path = path.replace("\\", "/");

            int pos = path.lastIndexOf("/");
            if (pos > 0) {
                path = path.substring(0, pos);
            }

            parentId = this.getId(path);

            //The path does not exists! 
            //FIXME-It is necessary to construct the path based on the last
            //valid id
            if (parentId.equals("root") && !path.equals("")
                    && !path.equals("/") && !path.equals("\\")) {
                this.makedir(path);
                parentId = this.getId(path);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        JSONObject json = new JSONObject();

        try {
            json.put("title", getTitle(file.getName()));

            JSONArray arr = new JSONArray();

            JSONObject jsonParent = new JSONObject();
            jsonParent.put("id", parentId);

            arr.put(jsonParent);
            json.put("parents", arr);

        } catch (JSONException ex) {
            Logger.getLogger(GoogleDriveClient.class.getName()).log(Level.SEVERE, null, ex);
        }

        String body = json.toString();

        String mimeType = getMimeType(file);

        Client client = Client.create();

        WebResource webResource = client
                .resource("https://www.googleapis.com/upload/drive/v2/files?uploadType=resumable");

        ClientResponse response = webResource
                .header("Host", "www.googleapis.com")
                .header("Authorization", "Bearer " + token)
                .header("Content-Length", "" + body.length())
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("X-Upload-Content-Type", mimeType)
                .header("X-Upload-Content-Length", "" + file.length())
                .post(ClientResponse.class, body);

        if (response.getStatus() != 200) {
            throw new RuntimeException("Failed : HTTP error code : "
                    + response.getStatus());
        }

        String location = response.getHeaders().get("Location").get(0);

        MultiPartUpload mpu = new MultiPartUpload(file, 0);
        mpu.putObject("location", location);
        mpu.putObject("mimeType", mimeType);

        return mpu;
    }

    @Override
    public void sendNextPart(MultiPartUpload mpu) {
        byte[] chunk = new byte[Constants.CHUNK_UPLOAD_SIZE];

        try {
            RandomAccessFile raf = new RandomAccessFile(mpu.getFile(), "r");

            raf.seek(mpu.getOffset());

            int chunkLen = raf.read(chunk);

            if (chunkLen < 0) {
                mpu.setFinished();
                raf.close();
                return;
            }

            if (chunkLen < Constants.CHUNK_UPLOAD_SIZE) {
                chunk = Arrays.copyOfRange(chunk, 0, chunkLen);
            }

            String sessionURL = (String) mpu.getObject("location");
            String mimeType = (String) mpu.getObject("mimeType");

            long start = mpu.getOffset();
            long end = start + chunkLen - 1;
            long total = mpu.getFile().length();
            String contentRange = "bytes " + start + "-" + end + "/" + total;

            Client client = Client.create();

            WebResource webResource = client
                    .resource(sessionURL);

            ClientResponse response = webResource
                    .header("Host", "www.googleapis.com")
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Length", "" + chunkLen)
                    .header("Content-Type", mimeType)
                    .header("Content-Range", contentRange)
                    .put(ClientResponse.class, chunk);

            mpu.incrOffset(chunkLen);

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void finalizeUpload(MultiPartUpload mpu) {
//        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<RemoteFile> getChildren(String folderName) throws IOException {
        List<RemoteFile> result = new ArrayList<>();

        String id = this.getId(folderName);
        Children.List request = service.children().list(id);

        do {

            ChildList children = request.execute();

            for (ChildReference child : children.getItems()) {
                String childName = this.getPathById(child.getId());

                File f = files.get(child.getId()).execute();

                boolean isFolder = f.getMimeType().equals("application/vnd.google-apps.folder");

                String downloadURL = f.getDownloadUrl();

                Long sizeL = f.getFileSize();
                long size = sizeL == null ? 0 : sizeL;

                if (childName.endsWith("/")) {
                    childName = childName.substring(0, childName.length() - 1);
                }

                RemoteFile file = new RemoteFile(childName, isFolder, size, downloadURL);

                try {
                    result.add(file);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            request.setPageToken(children.getNextPageToken());

        } while (request.getPageToken() != null
                && request.getPageToken().length() > 0);

        return result;
    }

    private String getMimeType(java.io.File file) {
        String mimeType = URLConnection.guessContentTypeFromName(file.getName());
        mimeType = (mimeType == null) ? "application/octet-stream"
                : mimeType;
        return mimeType;
    }

    @Override
    public void removeFolder(String remoteFolder) throws IOException {        
            files.delete(this.getId(remoteFolder)).execute();        
    }

    private boolean isValidToken() {
        if (this.service==null){
            return false;
        }
        try {
            this.service.about().get().execute();
            return true;
        } catch (GoogleJsonResponseException ex) {            
            //Logger.getLogger(GoogleDriveClient.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        } catch (IOException ex) {
            //Logger.getLogger(GoogleDriveClient.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }

}
