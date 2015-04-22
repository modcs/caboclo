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


public class RemoteFile {
    private final String path;
    private final boolean directory;
    private final long size;
    private final String downloadURL;

    public RemoteFile(String path, boolean isDirectory, long size) {
        this.path = path;
        this.directory = isDirectory;
        this.size = size;
        this.downloadURL = "";
    }

    public RemoteFile(String path, boolean directory, long size, String downloadURL) {
        this.path = path;
        this.directory = directory;
        this.size = size;
        this.downloadURL = downloadURL;
    }
    
    public String getPath() {
        return path;
    }

    public boolean isDirectory() {
        return directory;
    }

    public long getSize() {
        return size;
    }        

    @Override
    public String toString() {
        int pos = path.lastIndexOf("/");
        
        if(pos < 0){
            return path;
        }

        return path.substring(pos + 1);  
    }  

    public String getDownloadURL() {
        return downloadURL;
    }            
        
}
