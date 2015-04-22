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


package org.caboclo.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base64;
import org.caboclo.clients.AmazonClient;
import static org.caboclo.clients.AmazonClient.ACCESS_KEY;
import static org.caboclo.clients.AmazonClient.SECRET_KEY;
import org.caboclo.clients.ApiClient;
import org.caboclo.clients.DropboxClient;
import org.caboclo.clients.GoogleDriveClient;
import org.caboclo.clients.OneDriveClient;


public class Credentials {
    public static final String DROPBOX = "dropbox";
    public static final String GOOGLE = "google";
    public static final String ONEDRIVE = "onedrive";
    public static final String AMAZON = "amazon";

    /**
     * Save cloud service credentials as an encrypted string of characters
     *
     * @param currentServer The cloud storage provider for which we want to save
     * the credentials
     * @param cred The plain-text credentials that will be saved on file
     */
    public void saveCredentials(String currentServer, String cred) {
        StringBuilder strPath = new StringBuilder();
        strPath.append(System.getProperty("user.home")).
                append(java.io.File.separator).append("backupcredentials");

        BufferedWriter strout = null;

        try {
            //Create file if it does not exist
            File credentialsFile = new File(strPath.toString());
            if (!credentialsFile.exists()) {
                credentialsFile.createNewFile();
            }
            byte[] cypherCred = encryptCredentials(cred);
            strout = new BufferedWriter(new FileWriter(credentialsFile, true));
            String encodedCred = new String(new Base64().encode(cypherCred));
            String line = currentServer + ":" + encodedCred;
            strout.append(line);
            strout.newLine();
        } catch (IOException ex) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalBlockSizeException ex) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
        } catch (BadPaddingException ex) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidKeyException ex) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
        } catch (NoSuchPaddingException ex) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (strout != null) {
                    strout.flush();
                    strout.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private byte[] encryptCredentials(String plainText) throws NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, NoSuchAlgorithmException {
        String passphrase = "Backup Plugin Credentials";
        MessageDigest digest = MessageDigest.getInstance("SHA");
        digest.update(passphrase.getBytes());
        SecretKey sKey1 = new SecretKeySpec(digest.digest(), 0, 16, "AES");
        Cipher aesCipher = Cipher.getInstance("AES");
        aesCipher.init(Cipher.ENCRYPT_MODE, sKey1);
        byte[] byteCipherText = aesCipher.doFinal(plainText.getBytes());
        return byteCipherText;
    }

    private String decryptCredentials(byte[] text) throws BadPaddingException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, IllegalBlockSizeException {
        String passphrase = "Backup Plugin Credentials";
        MessageDigest digest = MessageDigest.getInstance("SHA");
        digest.update(passphrase.getBytes());
        SecretKey sKey1 = new SecretKeySpec(digest.digest(), 0, 16, "AES");
        Cipher aesCipher = Cipher.getInstance("AES");
        aesCipher.init(Cipher.DECRYPT_MODE, sKey1);
        String cred = new String(aesCipher.doFinal(text));
        return cred;
    }

    /**
     * Check if there are credentials previously stored on disk, for a specified
     * cloud provider
     *
     * @param currentServer The cloud storage provider
     * @return Credentials for the specified cloud provider, that were
     * previously saved on file
     */
    public String checkCredentialsFile(String currentServer) {
        StringBuilder path = new StringBuilder();
        path.append(System.getProperty("user.home")).
                append(java.io.File.separator).append("backupcredentials");

        File credentialsFile = new File(path.toString());
        BufferedReader input = null;
        String token = "";

        try {
            //Creates file only if it does not exist            
            boolean createdNow = credentialsFile.createNewFile();
            //Return with empty token, because file did not exist
            if (createdNow) {
                return token;
            }
            input = new BufferedReader(new FileReader(credentialsFile));
            String line;
            while (input.ready()) {
                line = input.readLine();
                if (line.startsWith(currentServer)) {
                    int colon = line.indexOf(":");
                    String encodedCred = line.substring(colon + 1);
                    token = decryptCredentials(new Base64().decode(encodedCred.getBytes()));
                }
            }
        } catch (IllegalBlockSizeException ex) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "Error while checking credentials file", ex.getMessage());
        } catch (BadPaddingException ex) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "Error while checking credentials file", ex.getMessage());
        } catch (InvalidKeyException ex) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "Error while checking credentials file", ex.getMessage());
        } catch (NoSuchPaddingException ex) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "Error while checking credentials file", ex.getMessage());
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "Error while checking credentials file", ex.getMessage());
        } catch (IOException ex) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, "Error while checking credentials file", ex.getMessage());
        }
        return token;
    }

    /**
     * Deletes the access credentials of the specified cloud provider on
     * credentials database file
     *
     * @param server
     */
    public void removeCredentials(String server) {
        StringBuilder path = new StringBuilder();
        path.append(System.getProperty("user.home")).
                append(java.io.File.separator).
                append("backupcredentials");

        BufferedReader input;
        try {
            //Create file if it does not exist
            File credFile = new File(path.toString());
            if (!credFile.exists()) {
                credFile.createNewFile();
            }

            //Stores contents of credentials file, except by line for the 
            //specified cloud server
            input = new BufferedReader(new FileReader(credFile));
            StringBuilder builder = new StringBuilder();
            while (input.ready()) {
                String line = input.readLine();
                System.out.println("Cred line: " + line);
                if (!line.startsWith(server)) {
                    System.out.println("Not-deleted line: " + line);
                    builder.append(line).append(System.lineSeparator());
                }
            }
            input.close();

            //Write new contents in the credentials file
            BufferedWriter output = new BufferedWriter(new FileWriter(credFile));
            output.write(builder.toString());
            output.flush();
            output.close();
        } catch (IOException ex) {
            Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
        }
    }

    public List<String> retrieveSavedAccounts() {
        StringBuilder path = new StringBuilder();
        path.append(System.getProperty("user.home")).
                append(java.io.File.separator).
                append("backupcredentials");

        List<String> list = new ArrayList<>();

        try {
            //Create file if it does not exist
            File credFile = new File(path.toString());
            if (!credFile.exists()) {
                return list;
            }

            byte[] encoded = Files.readAllBytes(Paths.get(path.toString()));
            String content = new String(encoded);

            String eol = System.getProperty("line.separator");

            StringTokenizer tokenizer = new StringTokenizer(content, eol, false);

            while (tokenizer.hasMoreTokens()) {
                String line = tokenizer.nextToken();
                int index = line.indexOf(":");
                String service = line.substring(0, index);
                list.add(service);
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return list;
    }

    public ApiClient retrieveAccount(String service) {

        String token = null;

        switch (service) {
            case DROPBOX:
                token = checkCredentialsFile(DROPBOX);

                if (token != null && !token.trim().equals("")) {
                    DropboxClient client = new DropboxClient(token);

                    return client;
                }

                return null;
            case GOOGLE:
                token = checkCredentialsFile(GOOGLE);

                if (token != null && !token.trim().equals("")) {
                    GoogleDriveClient client = new GoogleDriveClient(token);

                    return client;
                }

                return null;
            case ONEDRIVE:
                token = checkCredentialsFile(ONEDRIVE);

                if (token != null && !token.trim().equals("")) {
                    OneDriveClient client = new OneDriveClient(token);

                    return client;
                }

            case AMAZON:
                Credentials cred = new Credentials();
                String keys = cred.checkCredentialsFile(AMAZON);
                if (keys != null && !keys.isEmpty()) {
                    String[] access_keys = keys.split("@");
                    Map<String, String> params = new HashMap<>();
                    params.put(ACCESS_KEY, access_keys[0]);
                    params.put(SECRET_KEY, access_keys[1]);
                    
                    AmazonClient client = new AmazonClient();
                    client.authenticate(params);
                    
                    return client;
                }
            default:
                return null;

        }

    }
}
