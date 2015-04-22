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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.caboclo.service.MultiPartDownload;
import org.caboclo.service.MultiPartUpload;
import org.caboclo.service.RemoteFile;


public abstract class ApiClient implements Authenticator {

    public abstract void makedir(String path) throws IOException;

    public abstract void putFile(java.io.File file, String path) throws IOException;

    public abstract void getFile(java.io.File file, String path) throws IOException;

    public abstract List<RemoteFile> getChildren(String folderName) throws IOException;

    public abstract boolean isFolder(String folderName) throws IOException;

    public abstract MultiPartUpload initializeUpload(File file, String fileName);

    public abstract void sendNextPart(MultiPartUpload mpu);

    public abstract void finalizeUpload(MultiPartUpload mpu);

    public abstract MultiPartDownload initializeDownload(File file, RemoteFile remoteFile) throws IOException;

    public abstract void getNextPart(MultiPartDownload mpd) throws IOException;

    protected void downloadURL(String url, File file, Map<String, String> headers) throws IllegalStateException, IOException {
        HttpClient httpclient = new DefaultHttpClient();
        HttpGet httpget = new HttpGet(url);

        for (String key : headers.keySet()) {
            String value = headers.get(key);

            httpget.setHeader(key, value);
        }
        
        HttpResponse response = httpclient.execute(httpget);
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

    protected void downloadURL(String url, ByteArrayOutputStream bos, Map<String, String> headers) throws IllegalStateException, IOException {        
        HttpClient httpclient = new DefaultHttpClient();
        HttpGet httpget = new HttpGet(url);

        for (String key : headers.keySet()) {
            String value = headers.get(key);

            httpget.setHeader(key, value);
        }

        HttpResponse response = httpclient.execute(httpget);

        HttpEntity entity = response.getEntity();
        if (entity != null) {
            try {
                InputStream instream = entity.getContent();
                BufferedInputStream bis = new BufferedInputStream(instream);

                int inByte;
                while ((inByte = bis.read()) != -1) {
                    bos.write(inByte);
                }
                bis.close();
                bos.close();
            } catch (IOException ex) {
                ex.printStackTrace();
                throw ex;
            } catch (IllegalStateException ex) {
                ex.printStackTrace();
                httpget.abort();
                throw ex;
            }
            httpclient.getConnectionManager().shutdown();
        }
    }

    protected void appendFile(File file, byte[] data) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file, true)) {
            output.write(data);
        }
    }

    public abstract void removeFolder(String remoteFolder) throws IOException;
}
