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


package org.caboclo.tests;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.caboclo.activities.Activity;
import org.caboclo.activities.ActivityListener;
import org.caboclo.clients.AmazonClient;
import org.caboclo.service.BackupEngine;


public class MainAmazonTest {

    public static void main(String[] args) throws IOException {

        BackupEngine engine = BackupEngine.getAmazonInstance();

        if (!engine.isAuthenticated()) {
            Map<String, String> param = new HashMap<>();
            String accesskey = "your-access-key";
            String secretkey = "your-secret-key";
            param.put(AmazonClient.ACCESS_KEY, accesskey);
            param.put(AmazonClient.SECRET_KEY, secretkey);
            engine.authenticate(param);
        }

        /*
        String backupFolder = "C:\\Users\\Rubens\\test\\";
        String id = engine.doBackup(backupFolder, ActivityListener.EMPTY_LISTENER);
        Activity act = engine.getActivity(id);
        System.out.print("Waiting ");
        while (!act.isFinished()){
            try {
                Thread.sleep(2000);
                System.out.print(".");
            } catch (InterruptedException ex) {
                Logger.getLogger(MainGoogleDriveTest.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        System.out.println();
        */
       // engine.deleteBackup("Sat_Jun_07_15-05-34_BRT_2014");
        //engine.doRestore("Sun_Jun_08_15-10-10_BRT_2014","C:\\Users\\Rubens\\", list);
        
        
        
    }
