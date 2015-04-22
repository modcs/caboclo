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

import java.io.File;


public class OSValidator {

    private static final String OS = System.getProperty("os.name").toLowerCase();

    public static void main(String[] args) {

        System.out.println(OS);

        if (isWindows()) {
            System.out.println("This is Windows");
        } else if (isMac()) {
            System.out.println("This is Mac OS X");
        } else if (isUnix()) {
            System.out.println("This is Unix or Linux");
        } else if (isSolaris()) {
            System.out.println("This is Solaris");
        } else {
            System.out.println("Your OS is not support!!");
        }
    }

    public static boolean isWindows() {
        return (OS.contains("win"));
    }

    public static boolean isMac() {
        return (OS.contains("mac"));
    }

    public static boolean isUnix() {
        return (OS.contains("nix") || OS.contains("nux") || OS.indexOf("aix") > 0);
    }

    public static boolean isSolaris() {
        return (OS.contains("sunos"));
    }

    public static String getSeparator() {
        if (isWindows()) {
            return "\\";
        } else {
            return "/";
        }
    }

    public static String getAbsolutePath(File f, String rootPath) {
        String path = f.getPath();

        if (path.startsWith(rootPath)) {
            int pos = rootPath.lastIndexOf(OSValidator.getSeparator());
            path = path.substring(pos);
        } //else {
          //  throw new RuntimeException("This was not supposed to happen...");
        //}

        if (OSValidator.isWindows()) {
            int pos = path.indexOf(":");
            path = path.substring(pos + 1).replace("\\", "/");
        }

        return path;
    }

    public static String convertPath(String path, String root, String target) {
        String result = "";
        if (OSValidator.isWindows()) {
            result = (target + getSeparator() + path.substring(root.length())).replace("/", "\\");
        } else {
            result = target + getSeparator() + path.substring(root.length());
        }
        return result;
    }
}
