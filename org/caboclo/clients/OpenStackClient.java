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

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.client.urlconnection.HTTPSProperties;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.caboclo.service.MultiPartDownload;
import org.caboclo.service.MultiPartUpload;
import org.caboclo.service.RemoteFile;
import org.caboclo.util.Credentials;
import org.javaswift.joss.client.factory.AccountConfig;
import org.javaswift.joss.client.factory.AccountFactory;
import org.javaswift.joss.client.factory.AuthenticationMethod;
import org.javaswift.joss.model.Account;
import org.javaswift.joss.model.Container;
import org.javaswift.joss.model.StoredObject;


public class OpenStackClient extends ApiClient {

    private Container container;    
    private Container segmentContainer;
    private Map<String, String> parameters;
    private String storageURL;
    private String authToken;
    private Client client;
    private boolean isAuthenticated;

    public static final String USER_NAME = "user_name";
    public static final String PASSWORD = "password";
    public static final String AUTH_URL = "auth_url";
    public static final String DISABLE_SSL_SALVATION = "disable_ssl_salvation";
    public static final String AUTHENTICATION_METHOD = "authentication_method";

    public static final String TEMP_USER_NAME = "account:bruno";
    public static final String TEMP_PASSWORD = "password123";
    public static final String TEMP_AUTH_URL = "https://192.168.242.129";

    private static final int CHUNK_SIZE = 1024 * 1024; //1MB TODO- Increase to 5MB

    @Override
    public String[] getAuthenticationParameters() {
        return new String[]{USER_NAME, PASSWORD, AUTH_URL, DISABLE_SSL_SALVATION, AUTHENTICATION_METHOD};
    }

    @Override
    public String getAuthenticationParameter(String parameter) {
        return parameters.get(parameter);
    }

    @Override
    public boolean authenticate(Map<String, String> parameters) {
        this.parameters = parameters;        
        Credentials cred = new Credentials();
        
        if (!hasValidCredentials()){
            cred.removeCredentials("openstack");
            return false;
        }
               
        try {
            AccountConfig config = new AccountConfig();

            config.setUsername(parameters.get(USER_NAME));
            config.setPassword(parameters.get(PASSWORD));
            config.setAuthUrl(parameters.get(AUTH_URL)); //+ "/auth/v1.0");
            config.setDisableSslValidation(true);
            config.setAuthenticationMethod(AuthenticationMethod.BASIC);

            Account account = new AccountFactory(config).createAccount();

            this.container = account.getContainer("mainContainer");

            if (!this.container.exists()) {
                this.container.create();
                this.container.makePublic();//TODO - Maybe this is not necessary
                System.out.println("Container " + this.container.getName() + " created");
            }
            
            this.segmentContainer = account.getContainer("segments");
            
            if (!this.segmentContainer.exists()) {
                this.segmentContainer.create();                
            }
        
            //Remove old credentials, if they exist
            cred.removeCredentials("openstack");

            //Save new credentials
            String keys = parameters.get(USER_NAME) + "@" + parameters.get(PASSWORD) + "@" + parameters.get(AUTH_URL);
            cred.saveCredentials("openstack", keys);
            
        } catch (Exception ex) {
            return false;
        }
        updateCredentials();                
        this.isAuthenticated = true;
        return true;
    }

    private ClientConfig configureClientSSLDisabled() {
        TrustManager[] certs = new TrustManager[]{
            new X509TrustManager() {

                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] xcs, String string) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] xcs, String string) throws CertificateException {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

            }};
        SSLContext ctx = null;
        try {
            ctx = SSLContext.getInstance("TLS");
            ctx.init(null, certs, new SecureRandom());
        } catch (java.security.GeneralSecurityException ex) {
        }
        if (ctx != null) {
            HttpsURLConnection.setDefaultSSLSocketFactory(ctx.getSocketFactory());
        }

        ClientConfig config = new DefaultClientConfig();
        try {
            config.getProperties().put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES, new HTTPSProperties(
                    new HostnameVerifier() {
                        @Override
                        public boolean verify(String hostname, SSLSession session) {
                            return true;
                        }
                    },
                    ctx
            ));
        } catch (Exception e) {
        }
        return config;
    }

    private void updateCredentials() throws UniformInterfaceException, RuntimeException {
        //TODO - In production version, this method should be called -> Client client = Client.create();                        
        this.client = Client.create(configureClientSSLDisabled());
        WebResource webResource = client.resource(parameters.get(AUTH_URL) + "/auth/v1.0?format=json");

        ClientResponse response = webResource
                .header("X-Auth-Key", parameters.get(PASSWORD))
                .header("X-Auth-User", parameters.get(USER_NAME))
                .accept("application/json")
                .get(ClientResponse.class);

        if (response.getStatus() != 200) {
            throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
        }
        this.authToken = response.getHeaders().get("X-Auth-Token").get(0);
        this.storageURL = response.getHeaders().get("X-Storage-Url").get(0);
    }

    @Override
    public void makedir(String path) throws IOException {
        //nothing
    }

    @Override
    public void putFile(File file, String path) throws IOException {
        if (path.startsWith("/") && path.length() > 1) {
            path = path.substring(1);
        }

        StoredObject object = container.getObject(path);
        object.uploadObject(file);
        object.reload();        
    }

    @Override
    public void getFile(File file, String path) throws IOException {
        if (path.startsWith("/") && path.length() > 1) {
            path = path.substring(1);
        }

        StoredObject object = container.getObject(path);
        object.downloadObject(file);
        object.reload();
    }

    @Override
    public boolean isAuthenticated() {
        if (this.isAuthenticated && hasValidCredentials()) {
            return true;
        } else {
            System.out.println("check credentials file");
            Credentials cred = new Credentials();
            String keys = cred.checkCredentialsFile("openstack");
            if (keys != null && !keys.isEmpty()) {
                String[] access_keys = keys.split("@");
                Map<String, String> params = new HashMap<>();
                params.put(USER_NAME, access_keys[0]);
                params.put(PASSWORD, access_keys[1]);
                params.put(AUTH_URL, access_keys[2]);
                this.isAuthenticated = authenticate(params);
                return this.isAuthenticated;
            } else {
                System.out.println("No credentials in file");                
                return false;
            }
        }
      }

    //TODO - Adapt to the new getChildren
    public ArrayList<String> getChildrenOld(String folderName) throws IOException {
        ArrayList<String> result = new ArrayList<>();
        try {

            if (folderName.startsWith("/") && folderName.length() > 1) {
                folderName = folderName.substring(1);
            }

            String relativePath;
            if (folderName == null || folderName.equals("") || folderName.equals("/")) {
                relativePath = "/"
                        + this.container.getName()
                        + "?delimiter=/&format=json";
            } else {
                relativePath = "/"
                        + this.container.getName()
                        + "?prefix=" + folderName
                        + (folderName.charAt(folderName.length() - 1) == '/' ? "" : "/")
                        + "&delimiter=/&format=json";
            }

            WebResource webResource = client.resource(this.storageURL + relativePath);
            ClientResponse response = webResource
                    .header("X-Auth-Token", this.authToken)
                    .accept("application/json")
                    .get(ClientResponse.class);

            if (response.getStatus() != 200) {
                throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
            }

            String output = response.getEntity(String.class);
            JSONArray children = new JSONArray(output);

            for (int i = 0; i < children.length(); i++) {
                JSONObject element = children.getJSONObject(i);
                if (!element.isNull("hash")) {
                    result.add("/" + element.getString("name"));
                } else if (!element.isNull("subdir")) {
                    result.add("/" + element.getString("subdir"));
                }
            }

            return result;

        } catch (JSONException ex) {
            Logger.getLogger(OpenStackClient.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    @Override
    public boolean isFolder(String folderName) throws IOException {
        if (folderName.startsWith("/") && folderName.length() > 1) {
            folderName = folderName.substring(1);
        }

        try {
            String relativePath = "/"
                    + this.container.getName()
                    + "?prefix="
                    + (folderName.charAt(folderName.length() - 1) == '/'
                    ? folderName.substring(0, folderName.length() - 1)
                    : folderName)
                    + "&delimiter=/&format=json";

            WebResource webResource = client.resource(this.storageURL + relativePath);
            ClientResponse response = webResource
                    .header("X-Auth-Token", this.authToken)
                    .accept("application/json")
                    .get(ClientResponse.class);

            if (response.getStatus() != 200) {
                throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
            }

            String output = response.getEntity(String.class);
            JSONArray children = new JSONArray(output);

            for (int i = 0; i < children.length(); i++) {
                JSONObject element = children.getJSONObject(i);
                if (!element.isNull("subdir")) {
                    if (element.get("subdir").equals(folderName)
                            || element.get("subdir").equals(folderName + "/")) {
                        return true;
                    }
                }
            }
        } catch (JSONException ex) {
            Logger.getLogger(OpenStackClient.class.getName()).log(Level.SEVERE, null, ex);
        }
        return false;
    }
    
    public boolean split(File f, long size) {
    if (size <= 0)
      return false;

    try {
      int parts = ((int) (f.length() / size));
      long flength = 0;
      if (f.length() % size > 0)
        parts++;

      File[] fparts = new File[parts];

      FileInputStream fis = new FileInputStream(f);
      FileOutputStream fos = null;

      for (int i = 0; i < fparts.length; i++) {
        fparts[i] = new File(f.getPath() + i);
        fos = new FileOutputStream(fparts[i]);

        int read = 0;
        long total = 0;
        byte[] buff = new byte[1024];
        int origbuff = buff.length;
        while (total < size) {
          read = fis.read(buff);
          if (read != -1) {
            //Probable mistake buff = invertBuffer(buff, 0, read);
            total += read;
            flength += read;
            fos.write(buff, 0, read);
          }
          if (i == fparts.length - 1 && read < origbuff)
            break;
        }

        fos.flush();
        fos.close();
        fos = null;
      }

      fis.close();
      // f.delete();
      f = fparts[0];

      System.out.println("Length Readed (KB): " + flength / 1024.0);
      return true;
    } catch (Exception ex) {
      System.out.println(ex);
      System.out.println(ex.getLocalizedMessage());
      System.out.println(ex.getStackTrace()[0].getLineNumber());
      ex.printStackTrace();
      return false;
    }
  }

    @Override
    public MultiPartUpload initializeUpload(File file, String fileName) {
        MultiPartUpload mpu = new MultiPartUpload(file, 0);
        String localPath = file.getAbsolutePath().substring(0,file.getAbsolutePath().lastIndexOf(File.separator) + 1);
        
        System.out.println("\t\tStarting multipart file sending: " + file.getAbsolutePath());

        //Hold the files to be sent
        int parts = ((int) (file.length() / CHUNK_SIZE));      
        if (file.length() % CHUNK_SIZE > 0)
        {
            parts++;
        }
        
        mpu.putObject("numOfChunks", parts);
        mpu.putObject("currentChunk", 0);
        mpu.putObject("fileName", file.getName());
        mpu.putObject("localPath", localPath);
        mpu.putObject("ServerPath", fileName);
        
        //Split the file into multiple files
        this.split(file, CHUNK_SIZE);

        return mpu;        
    }

    @Override
    public void sendNextPart(MultiPartUpload mpu) {
        Integer currentComponent = (Integer) mpu.getObject("currentChunk");
        Integer numChunks = (Integer) mpu.getObject("numOfChunks");
        String localPath = mpu.getObject("localPath").toString();
        
        if(currentComponent < numChunks)
        {            
            String fileName = mpu.getObject("fileName").toString();
            fileName = fileName + currentComponent;
            
            StoredObject object = segmentContainer.getObject(fileName);
            object.uploadObject(new File(localPath + fileName));            
            mpu.putObject("currentChunk", ++currentComponent);
        }
        else
        {
            mpu.setFinished();
        }
    }

    @Override
    public void finalizeUpload(MultiPartUpload mpu) {
              
      InputStream is = new ByteArrayInputStream("".getBytes());
                
      WebResource webResource = client.resource(this.storageURL + "/" +this.container.getName() + "/" + (String) mpu.getObject("ServerPath"));
      System.out.println("Server addr: " + this.storageURL + "/" +this.container.getName() + "/" + (String) mpu.getObject("ServerPath"));
            ClientResponse response = webResource
                    .header("X-Auth-Token", this.authToken)
                    .header("X-Object-Manifest", "segments/" + mpu.getObject("fileName"))
                    .put(ClientResponse.class, is);

            if (response.getStatus() != 201) {
                throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
            }
            
            System.out.print("Object Uploaded");

    }

/*** 
    public static void main(String[] args) throws IOException {

        //OBJETO = {"user_name":"account:bruno","password":"password123","password":"https://192.168.242.129"}
        
        OpenStackClient openStackClient = new OpenStackClient();
        Map<String, String> parameters = new HashMap<String, String>();
        
        parameters.put("user_name", "account:bruno");
        parameters.put("password", "password123");
        parameters.put("auth_url", "https://192.168.242.129");
        
        openStackClient.authenticate(parameters);
        
        MultiPartUpload mpu = openStackClient.initializeUpload(new File("C:\\Users\\Bruno\\Desktop\\Temp\\temp.mp3"), "temp.mp3");
        
        while (mpu.hasMoreParts()) {           
            openStackClient.sendNextPart(mpu);
        }
        
        openStackClient.finalizeUpload(mpu);
        
        openStackClient.getFile(new File("C:\\Users\\Bruno\\Desktop\\Temp\\tempNew.mp3"), "temp.mp3");
        
//        try {
//            System.out.println(openStackClient.getChildren("/"));
//            System.out.println("Info " + openStackClient.storageURL + " " + openStackClient.authToken);
//            System.out.println("Is Folder: " + openStackClient.isFolder("folder"));
//            System.out.println("Is Folder: " + openStackClient.isFolder("/folder/"));
//            System.out.println("Is Folder: " + openStackClient.isFolder("folder/sub"));
//            System.out.println("Is Folder: " + openStackClient.isFolder("/folder/subfolder"));
//            System.out.println("Is Folder: " + openStackClient.isFolder("folder/subfolder/"));
//            System.out.println("Is Folder: " + openStackClient.isFolder("folder/subfolder/File1.txt/"));
//            System.out.println("Is Folder: " + openStackClient.isFolder("folder/subfolder/File1.txt"));
//            System.out.println("Is Folder: " + openStackClient.isFolder("/"));
//        } catch (IOException ex) {
//            Logger.getLogger(OpenStackClient.class.getName()).log(Level.SEVERE, null, ex);
//        }
    }
//    {
//        AccountConfig config = new AccountConfig();
//        config.setUsername("account:bruno");     
//        config.setPassword("password123");
//        config.setAuthUrl("https://192.168.242.129/auth/v1.0");
//        config.setDisableSslValidation(true);
//        config.setAuthenticationMethod(AuthenticationMethod.BASIC);
//        
//        Account account = new AccountFactory(config).createAccount();        
//        Container container = account.getContainer("defaultContainer");
//        
//        Collection<Container> containers = account.list();
//        for (Container currentContainer : containers) {
//            System.out.println(currentContainer.getName());
//        }
//        
//        if(!container.exists())
//        {
//            container.create();
//            container.makePublic();
//            System.out.println("Container " + container.getName() + " created");
//        }                
//        
//        StoredObject object = container.getObject("dog.jpg");
//        object.uploadObject(new File("dog.jpg"));
//        System.out.println("Public URL: "+object.getPublicURL());                               
//        
//        object.downloadObject(new File("newDog.jpg"));
//    }          
*/

    @Override
    public List<RemoteFile> getChildren(String folderName) throws IOException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private boolean hasValidCredentials() {
        this.client = Client.create(configureClientSSLDisabled());
        WebResource webResource = client.resource(parameters.get(AUTH_URL) + "/auth/v1.0?format=json");

        ClientResponse response = webResource
                .header("X-Auth-Key", parameters.get(PASSWORD))
                .header("X-Auth-User", parameters.get(USER_NAME))
                .accept("application/json")
                .get(ClientResponse.class);

        if (response.getStatus() == 200) {
            return true;
        }else{
            return false;
        }
    }

    @Override
    public MultiPartDownload initializeDownload(File file,  RemoteFile remoteFile){
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void getNextPart(MultiPartDownload mpd){
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void removeFolder(String remoteFolder) throws IOException {
        ArrayList<String> result = new ArrayList<>();
        try {
            if (remoteFolder.startsWith("/") && remoteFolder.length() > 1) {
                remoteFolder = remoteFolder.substring(1);
            }

            String relativePath;
            if (remoteFolder == null || remoteFolder.equals("") || remoteFolder.equals("/")) {
                relativePath = "/"
                        + this.container.getName()
                        + "?delimiter=/&format=json";
            } else {
                relativePath = "/"
                        + this.container.getName()
                        + "?prefix=" + remoteFolder
                        + (remoteFolder.charAt(remoteFolder.length() - 1) == '/' ? "" : "/")
                        + "&format=json";
            }

            WebResource webResource = client.resource(this.storageURL + relativePath);
            ClientResponse response = webResource
                    .header("X-Auth-Token", this.authToken)
                    .accept("application/json")
                    .get(ClientResponse.class);

            if (response.getStatus() != 200) {
                throw new RuntimeException("Failed : HTTP error code : " + response.getStatus());
            }
            
            
            String output = response.getEntity(String.class);
            JSONArray children = new JSONArray(output);                      
            
            for (int i = 0; i < children.length(); i++) {
                JSONObject element = children.getJSONObject(i);
                if (!element.isNull("hash")) {
                    container.getObject(element.getString("name")).delete();
                } else if (!element.isNull("subdir")) {
                    container.getObject(element.getString("subdir")).delete();
                }
            }
            
            } catch (JSONException ex) {
                Logger.getLogger(OpenStackClient.class.getName()).log(Level.SEVERE, null, ex);
                throw new IOException("Error while removing directory "+remoteFolder);
            }        
    }
    
}
