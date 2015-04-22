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

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.caboclo.service.Constants;
import org.caboclo.service.MultiPartDownload;
import org.caboclo.service.MultiPartUpload;
import org.caboclo.service.RemoteFile;
import org.caboclo.util.Credentials;


public class AmazonClient extends ApiClient {

    private AmazonS3 s3;
    private Map<String, String> parameters;
    public static final String ACCESS_KEY = "access_key";
    public static final String SECRET_KEY = "secret_key";
    public boolean authenticated = false;

    @Override
    public String[] getAuthenticationParameters() {
        return new String[]{ACCESS_KEY, SECRET_KEY};
    }

    @Override
    public String getAuthenticationParameter(String parameter) {
        return parameters.get(parameter);
    }

    @Override
    public boolean authenticate(Map<String, String> parameters) {
        this.parameters = parameters;
        try {
            s3 = getS3_();

            Credentials cred = new Credentials();

            //Remove old credentials, if they exist
            cred.removeCredentials("amazon");

            //Save new credentials
            String keys = parameters.get(ACCESS_KEY) + "@" + parameters.get(SECRET_KEY);
            cred.saveCredentials("amazon", keys);

            authenticated = true;
            createBackupBucket();

            return true;
        } catch (AmazonClientException ex) {
            Logger.getLogger(AmazonClient.class.getName()).log(Level.SEVERE,
                    "Could not connect to Amazon S3. Possible error with credentials");
            authenticated = false;
            return false;
        }
    }

    @Override
    public void makedir(String path) throws IOException {
        mkdirs(path);
    }

    private void mkdir(String path) {
        ObjectMetadata om = new ObjectMetadata();
        om.setLastModified(new Date());
        om.setContentLength(0);
        om.setContentType("inode/directory");
        ByteArrayInputStream bis = new ByteArrayInputStream(new byte[0]);

        s3.putObject(getBucketName(), path, bis, om);

        System.out.println("Creating Directory: " + path);
    }

    private void mkdirs(String str) {
        int index = str.lastIndexOf("/");

        if (index > 0) {
            String parent = str.substring(0, index);

            if (!objectExists(parent)) {
                mkdirs(parent);
            }
        }

        mkdir(str);
    }

    @Override
    public void putFile(File file, String path) throws IOException {
        ObjectMetadata om = new ObjectMetadata();
        om.setLastModified(new Date());
        om.setContentType(URLConnection.guessContentTypeFromName(file.getName()));
        om.setContentLength(file.length());
        BufferedInputStream stream = new BufferedInputStream(new FileInputStream(file));

        s3.putObject(getBucketName(), path, stream, om);

    }

    @Override
    public void getFile(File file, String child) throws IOException {
        S3Object obj = s3.getObject(getBucketName(), child);

        BufferedInputStream bis = new BufferedInputStream(obj.getObjectContent());

        writeStreamToFile(bis, file);
    }

    public void deleteBucketContents() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
//        ObjectListing listing = s3.listObjects(BUCKET_NAME);
//
//        System.out.println(listing.getObjectSummaries().size());
//
//        for (S3ObjectSummary summ : listing.getObjectSummaries()) {
//            s3.deleteObject(BUCKET_NAME, summ.getKey());
//        }
    }

    @Override
    public boolean isAuthenticated() {
        if (authenticated) {
            return true;
        } else {
            System.out.println("check credentials file");
            Credentials cred = new Credentials();
            String keys = cred.checkCredentialsFile("amazon");
            if (keys != null && !keys.isEmpty()) {
                String[] access_keys = keys.split("@");
                Map<String, String> params = new HashMap<>();
                params.put(ACCESS_KEY, access_keys[0]);
                params.put(SECRET_KEY, access_keys[1]);
                authenticated = authenticate(params);
                return authenticated;
            } else {
                System.out.println("No credentials in file");
                return false;
            }
        }
    }

    public ArrayList<String> getAllChildren(String folderName) throws IOException {
        ListObjectsRequest listRequest = new ListObjectsRequest();
        listRequest.setBucketName(getBucketName());
        listRequest.setPrefix(folderName);

        ObjectListing listing = s3.listObjects(listRequest);

        ArrayList<String> list = new ArrayList<String>();

        System.out.println(listing.getObjectSummaries().size());

        for (S3ObjectSummary summ : listing.getObjectSummaries()) {
            list.add(summ.getKey());
        }

        return list;
    }

    @Override
    public boolean isFolder(String folderName) throws IOException {
        ObjectMetadata om = s3.getObjectMetadata(getBucketName(), folderName);

        return isFolder(om);

    }

    private AmazonS3 getS3_() throws AmazonClientException {
        AmazonS3 s3_;
        S3ClientOptions s3ClientOptions;
//        AWSCredentials credentials = new BasicAWSCredentials(Constants.EC2_ACCESS_KEY, Constants.EC2_SECRET_KEY);

        AWSCredentials credentials = new BasicAWSCredentials(
                getAuthenticationParameter(ACCESS_KEY),
                getAuthenticationParameter(SECRET_KEY));

        s3_ = new AmazonS3Client(credentials);
        s3ClientOptions = new S3ClientOptions();
        //s3_.setEndpoint(Constants.EC2_END_POINT);
        s3ClientOptions.setPathStyleAccess(true);
        s3_.setS3ClientOptions(s3ClientOptions);
        System.out.println(s3_.listBuckets());
        return s3_;
    }

    public AmazonS3 getS3() {
        return s3;
    }

    private void writeStreamToFile(InputStream inputStream, File file) throws IOException {

        FileOutputStream output = new FileOutputStream(file);
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];
        int len = 0;

        while ((len = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, len);
        }

        output.close();
    }

    private void writeStreamToByteStream(InputStream inputStream, ByteArrayOutputStream output) throws IOException {

        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];
        int len = 0;

        while ((len = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, len);
        }

        output.close();
    }

    private boolean isFolder(ObjectMetadata om) {
        return om.getContentLength() == 0 && om.getContentType().equals("inode/directory");
    }

    public List<String> listBuckets() {
        List<Bucket> list = s3.listBuckets();
        List<String> buckets = new ArrayList<String>();

        for (Bucket bucket : list) {
            //System.out.println(bucket.getName());
            buckets.add(bucket.getName());
        }

        return buckets;
    }

    public boolean objectExists(String key) {

        if (key.equals("/backups")) {
            return true;
        }

        try {
            System.out.println(key);

            s3.getObject(getBucketName(), key);
        } catch (AmazonClientException ex) {
            return false;
        }

        return true;
    }

    public ObjectMetadata getObjectMetadata(String key) {
        ObjectMetadata om = s3.getObjectMetadata(getBucketName(), key);

        return om;
    }

    public List<String> getAllChildren(String folderName, String bucket) throws IOException {
        ListObjectsRequest listRequest = new ListObjectsRequest();
        listRequest.setBucketName(bucket);

        if (!(folderName == null || folderName.equals(""))) {
            listRequest.setPrefix(folderName);
        }

        ObjectListing listing = s3.listObjects(listRequest);

        ArrayList<String> list = new ArrayList<String>();

        for (S3ObjectSummary summ : listing.getObjectSummaries()) {
            list.add(summ.getKey());
        }

        return list;
    }

    public void deleteBucketContents(String bucket) {
        ObjectListing listing = s3.listObjects(bucket);

        System.out.println(listing.getObjectSummaries().size());

        for (S3ObjectSummary summ : listing.getObjectSummaries()) {
            s3.deleteObject(bucket, summ.getKey());
        }
    }

    public List<String> listBucket(String bkt, String prefix, String delimiter) throws IOException {

        ListObjectsRequest listRequest = new ListObjectsRequest();
        listRequest.setBucketName(bkt);
        listRequest.setDelimiter(delimiter);
        listRequest.setPrefix(prefix);

        ObjectListing listing = s3.listObjects(listRequest);

        ArrayList<String> list = new ArrayList<String>();

        for (S3ObjectSummary summ : listing.getObjectSummaries()) {
            list.add(summ.getKey());
        }

        return list;
    }

    public void createBucket(String bucketName) {
        s3.createBucket(bucketName);
    }

    private String getBucketName() {
        /*
         int index = path.substring(9).indexOf("/");
         String bucketName = "";
         if (index < 0) {
         if (path.startsWith("/backups/")) {
         bucketName = path.substring(9);
         }
         } else {
         bucketName = path.substring(9, 9 + index);
         }

         int hashcode = Math.abs(bucketName.hashCode()) % NUM_BUCKETS;

         System.out.println("test-modcs-bucket" + hashcode);

         return "test-modcs-bucket" + hashcode;
         */
        return "backups-user-" + parameters.get(ACCESS_KEY).toLowerCase();
    }

    void createFolder(String bucketName, String path) {
        ObjectMetadata om = new ObjectMetadata();
        om.setLastModified(new Date());
        om.setContentLength(0);
        om.setContentType("inode/directory");
        ByteArrayInputStream bis = new ByteArrayInputStream(new byte[0]);

        s3.putObject(bucketName, path, bis, om);

        System.out.println("Creating folder: " + path);
    }

    @Override
    public MultiPartUpload initializeUpload(File file, String fileName) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void sendNextPart(MultiPartUpload mpu) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void finalizeUpload(MultiPartUpload mpu) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<RemoteFile> getChildren(String folderName) throws IOException {
        if (!folderName.endsWith("/")) {
            folderName = folderName + "/";
        }

        ListObjectsRequest listRequest = new ListObjectsRequest();
        listRequest.setBucketName(getBucketName());
        listRequest.setDelimiter("/");
        listRequest.setPrefix(folderName);

        ObjectListing listing = s3.listObjects(listRequest);

        ArrayList<RemoteFile> list = new ArrayList<>();

        for (S3ObjectSummary summ : listing.getObjectSummaries()) {
            String name = summ.getKey();
            long size = summ.getSize();

            boolean isDirectory = isFolder(name);

            RemoteFile file = new RemoteFile(name, isDirectory, size);
            list.add(file);
        }

        return list;
    }

    private void createBackupBucket() {
        String buckName = getBucketName();
        if (!s3.doesBucketExist(buckName)) {
            createBucket(buckName);
        }
    }

    @Override
    public MultiPartDownload initializeDownload(File file, RemoteFile remoteFile) throws IOException {

        long start = 0;
        long end = Constants.CHUNK_DOWNLOAD_SIZE - 1;
        String child = remoteFile.getPath();

        GetObjectRequest rangeObjectRequest = new GetObjectRequest(
                getBucketName(), child);

        rangeObjectRequest.setRange(start, end);

        S3Object obj = s3.getObject(rangeObjectRequest);

        BufferedInputStream bis = new BufferedInputStream(obj.getObjectContent());

        writeStreamToFile(bis, file);

        MultiPartDownload mpd = new MultiPartDownload(file, remoteFile, end);

        return mpd;
    }

    @Override
    public void getNextPart(MultiPartDownload mpd) throws IOException {
        RemoteFile remoteFile = mpd.getRemotePath();
        File file = mpd.getFile();

        long start = mpd.getOffset() + 1;
        long end = mpd.getOffset() + Constants.CHUNK_DOWNLOAD_SIZE - 1;

        boolean finished = false;

        if (end > mpd.getRemotePath().getSize()) {
            end = mpd.getRemotePath().getSize();

            finished = true;
        }

        String child = remoteFile.getPath();

        GetObjectRequest rangeObjectRequest = new GetObjectRequest(
                getBucketName(), child);

        rangeObjectRequest.setRange(start, end);

        S3Object obj = s3.getObject(rangeObjectRequest);

        BufferedInputStream bis = new BufferedInputStream(obj.getObjectContent());

        ByteArrayOutputStream baos = new ByteArrayOutputStream(Constants.CHUNK_DOWNLOAD_SIZE);

        writeStreamToByteStream(bis, baos);

        appendFile(file, baos.toByteArray());

        mpd.setOffset(end);

        mpd.setFinished(finished);

    }

    @Override
    public void removeFolder(String remoteFolder) throws IOException {
        try{
            ArrayList<String> listOfFiles = this.getAllChildren(remoteFolder);
            for (String file:listOfFiles){
               s3.deleteObject(this.getBucketName(), file); 
            }        
        }catch (AmazonClientException ex){
            throw new IOException("Error while removing "+remoteFolder);
        }
    }
}
