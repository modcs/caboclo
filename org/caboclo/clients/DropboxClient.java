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

import com.dropbox.core.DbxAccountInfo;
import com.dropbox.core.DbxAppInfo;
import com.dropbox.core.DbxAuthFinish;
import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxEntry;
import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.DbxWebAuthNoRedirect;
import com.dropbox.core.DbxWriteMode;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.caboclo.service.BackupEngine;
import org.caboclo.util.Browser;
import org.caboclo.service.Constants;
import org.caboclo.service.MultiPartUpload;
import org.caboclo.service.RemoteFile;
import org.caboclo.util.Credentials;


public class DropboxClient extends OAuthClient {

    final String APP_KEY = "your-key";
    final String APP_SECRET = "your-secret";
    private DbxRequestConfig config;
    private DbxWebAuthNoRedirect webAuth;
    private DbxClient dbClient;
    private boolean authenticated;
    private String bearerToken;

    public DropboxClient() {
        config = new DbxRequestConfig("JavaTutorial/1.0", Locale.getDefault()
                .toString());
        authenticated = false;
    }

    public DropboxClient(String bearerToken) {
        this();

        this.bearerToken = bearerToken;

        System.out.println(bearerToken);

        dbClient = new DbxClient(config, bearerToken);

        authenticated = true;
    }

    public void retrieveChunk(String file) {
    }

    public DbxClient getClient() {
        return dbClient;
    }

    @Override
    public String getOauthURL() {
        DbxAppInfo appInfo = new DbxAppInfo(APP_KEY, APP_SECRET);

        webAuth = new DbxWebAuthNoRedirect(config, appInfo);

        return webAuth.start();
    }

    @Override
    public String getBearerToken() {
        return bearerToken;
    }

    @Override
    public void putFile(java.io.File inputFile, String fileName) {
        FileInputStream inputStream = null;

        try {

            inputStream = new FileInputStream(inputFile);

            DbxEntry.File uploadedFile = dbClient.uploadFile(fileName,
                    DbxWriteMode.add(), inputFile.length(), inputStream);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (DbxException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public void makedir(String path) throws IOException {
        try {
            this.dbClient.createFolder(path);
        } catch (DbxException ex) {
            throw new IOException("Error to create the dropbox folder");
        }
    }

    @Override
    public void getFile(File file, String path) throws IOException {
        try {
            FileOutputStream outputStream = new FileOutputStream(file);

            DbxEntry.File downloadedFile = dbClient.getFile(path, null,
                    outputStream);

        } catch (DbxException ex) {
            throw new IOException("Error to get file");
        }
    }

    @Override
    public boolean authenticate(String code) {

        try {
            DbxAuthFinish authFinish = webAuth.finish(code);

            dbClient = new DbxClient(config, authFinish.accessToken);

            Credentials cred = new Credentials();

            //Remove old credentials, if they exist
            cred.removeCredentials("dropbox");

            //Save new credentials
            cred.saveCredentials("dropbox", authFinish.accessToken);

            bearerToken = authFinish.accessToken;

            authenticated = true;

            return true;
        } catch (DbxException e) {
            e.printStackTrace();
            authenticated = false;
            return false;
        }
    }

    @Override
    public boolean isAuthenticated() {
        if (authenticated) {
            return true;
        } else {
            System.out.println("check credentials file");
            Credentials cred = new Credentials();
            String token = cred.checkCredentialsFile("dropbox");
            if (token != null && !token.isEmpty()) {
                dbClient = new DbxClient(config, token);
                if (isValidToken()) {
                    authenticated = true;
                    bearerToken = token;
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    @Override
    public boolean isFolder(String child) throws IOException {
        boolean isfolder = false;

        try {
            DbxEntry entry = this.dbClient.getMetadata(child);

            if (entry.isFolder()) {
                isfolder = true;
            }
        } catch (DbxException ex) {
            throw new IOException("Error in folder checking");
        }

        return isfolder;
    }

    @Override
    public MultiPartUpload initializeUpload(File file, String fileName) {
        try {
            byte[] chunk = new byte[Constants.CHUNK_UPLOAD_SIZE];

            RandomAccessFile raf = new RandomAccessFile(file, "r");

            int chunkLen = raf.read(chunk);

            int offset = chunkLen;

            String id = dbClient.chunkedUploadFirst(chunk, 0, chunkLen);

            MultiPartUpload mpu = new MultiPartUpload(file, offset);
            mpu.putObject("id", id);
            mpu.putObject("path", fileName);

            raf.close();

            return mpu;

        } catch (IOException | DbxException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    @Override
    public void sendNextPart(MultiPartUpload mpu) {
        try {
            byte[] chunk = new byte[Constants.CHUNK_UPLOAD_SIZE];

            RandomAccessFile raf = new RandomAccessFile(mpu.getFile(), "r");

            raf.seek(mpu.getOffset());

            int chunkLen = raf.read(chunk);

            if (chunkLen < 0) {
                mpu.setFinished();
                raf.close();
                return;
            }

            String id = (String) mpu.getObject("id");

            dbClient.chunkedUploadAppend(id, mpu.getOffset(), chunk, 0, chunkLen);

            mpu.incrOffset(chunkLen);

            raf.close();

        } catch (IOException | DbxException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void finalizeUpload(MultiPartUpload mpu) {
        try {
            String path = (String) mpu.getObject("path");
            String id = (String) mpu.getObject("id");
            dbClient.chunkedUploadFinish(path, DbxWriteMode.add(), id);
        } catch (DbxException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public List<RemoteFile> getChildren(String folderName) throws IOException {
        try {
            ArrayList<RemoteFile> result = new ArrayList<>();

            DbxEntry.WithChildren listing = dbClient.getMetadataWithChildren(folderName);

            if (listing == null) {
                throw new IOException("Folder: " + folderName + " does not exist");
            }

            for (DbxEntry child : listing.children) {
                String path = child.path;
                boolean directory = child.isFolder();

                long size = 0;

                if (!directory) {
                    size = child.asFile().numBytes;
                }

                String base = "https://api-content.dropbox.com/1/files/dropbox";

                String url = escapeURL(base, path);

                RemoteFile file = new RemoteFile(path, directory, size, url);

                result.add(file);
            }
            return result;
        } catch (DbxException ex) {
            throw new IOException("Dropbox folder listing error.");
        }
    }

    private String escapeURL(String baseURL, String path) {
        try {
            path = java.net.URLEncoder.encode(path, "UTF-8");
            String str = baseURL + path;
            str = str.replace("%2F", "/");
            str = str.replace("+", "%20");
            return str;
        } catch (UnsupportedEncodingException ex) {
            return null;
        }
    }

    public static void main(String[] args) throws IOException {
        BackupEngine engine = BackupEngine.getDropboxInstance();

        if (!engine.isAuthenticated()) {
            Map<String, String> param = new HashMap<>();
            String authUrl = engine.getAuthenticationParameter("url");
            Browser.openBrowser(authUrl);
            System.out.println("Paste the token:");
            Scanner sc = new Scanner(System.in);
            String token = sc.nextLine();
            param.put("token", token);
            engine.authenticate(param);
        }

        DropboxClient cli = (DropboxClient) engine.getClient();
    }

    @Override
    public void removeFolder(String remoteFolder) throws IOException {
        try {
            this.dbClient.delete(remoteFolder);
        } catch (DbxException ex) {
            Logger.getLogger(DropboxClient.class.getName()).log(Level.SEVERE, null, ex);
            throw new IOException("Error while removing folder " + remoteFolder);
        }
    }

    private boolean isValidToken() {
        if (this.dbClient == null) {
            return false;
        }
        try {
            DbxAccountInfo info = this.dbClient.getAccountInfo();
            if (info == null) {
                return false;
            } else {
                return true;
            }
        } catch (DbxException ex) {
            Logger.getLogger(DropboxClient.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }
}
