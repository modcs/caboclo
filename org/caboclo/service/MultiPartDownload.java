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


package org.caboclo.service;

import java.io.File;
import java.util.HashMap;
import java.util.Map;


public class MultiPartDownload {

    private File file;
    private RemoteFile remoteFile;
    private long offset;
    private Map<String, Object> contextInformation;
    private int partNumber;
    private boolean finished;
       
    public MultiPartDownload(File file, RemoteFile remoteFile, long offset) {
        this.file = file;
        this.remoteFile = remoteFile;
        this.offset = offset;
        this.contextInformation = new HashMap<>();
        this.finished = false;
        partNumber = 0;
    }

    public File getFile() {
        return file;
    }

    public long getOffset() {
        return offset;
    }

    public int incrAndGet() {
        return ++partNumber;
    }

    public RemoteFile getRemotePath() {
        return remoteFile;
    }

    public void putObject(String key, Object obj) {
        contextInformation.put(key, obj);
    }

    public Object getObject(String key) {
        return contextInformation.get(key);
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }
    
    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

}
