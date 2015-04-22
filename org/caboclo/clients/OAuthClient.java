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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.caboclo.service.Constants;
import org.caboclo.service.MultiPartDownload;
import org.caboclo.service.RemoteFile;


public abstract class OAuthClient extends ApiClient {

    public static final String TOKEN = "token";
    public static final String BEARER_TOKEN = "bearer_token";
    public static final String URL = "url";
    private String token;

    @Override
    public String[] getAuthenticationParameters() {
        return new String[]{TOKEN, BEARER_TOKEN, URL};
    }

    @Override
    public String getAuthenticationParameter(String parameter) {
        switch (parameter) {
            case TOKEN:
                return getToken();
            case BEARER_TOKEN:
                return getBearerToken();
            case URL:
                return getOauthURL();
            default:
                return "";
        }
    }

    @Override
    public boolean authenticate(Map<String, String> parameters) {
        this.token = parameters.get("token");

        return authenticate(token);
    }

    public String getToken() {
        return token;
    }

    public abstract String getBearerToken();

    public abstract String getOauthURL();

    public abstract boolean authenticate(String token);

    @Override
    public MultiPartDownload initializeDownload(java.io.File file, RemoteFile remoteFile) throws IOException {

        String url = remoteFile.getDownloadURL();

        Map<String, String> map = new HashMap<>();
        map.put("Authorization", "Bearer " + getBearerToken());

        if (remoteFile.getSize() > Constants.CHUNK_DOWNLOAD_SIZE) {
            map.put("Range", "bytes=0-" + (Constants.CHUNK_DOWNLOAD_SIZE - 1));
        }

        downloadURL(url, file, map);

        MultiPartDownload mpd = new MultiPartDownload(file, remoteFile, Constants.CHUNK_DOWNLOAD_SIZE - 1);
        mpd.putObject("url", url);

        if (remoteFile.getSize() < Constants.CHUNK_DOWNLOAD_SIZE) {
            mpd.setFinished(true);
        }

        return mpd;
    }

    @Override
    public void getNextPart(MultiPartDownload mpd) throws IOException {
        String url = (String) mpd.getObject("url");

        long start = mpd.getOffset() + 1;
        long end = mpd.getOffset() + Constants.CHUNK_DOWNLOAD_SIZE - 1;
        boolean finished = false;

        if (end > mpd.getRemotePath().getSize()) {
            end = mpd.getRemotePath().getSize();

            finished = true;
        }

        Map<String, String> map = new HashMap<>();
        map.put("Authorization", "Bearer " + getBearerToken());
        map.put("Range", "bytes=" + start
                + "-"
                + end);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        downloadURL(url, bos, map);

        appendFile(mpd.getFile(), bos.toByteArray());

        mpd.setOffset(end);

        mpd.setFinished(finished);
    }
}
