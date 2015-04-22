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
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.caboclo.activities.Activity;
import org.caboclo.activities.ActivityListener;
import org.caboclo.service.BackupEngine;
import org.caboclo.util.Browser;


public class MainOneDriveTest {

    public static void main(String[] args) throws IOException {

        BackupEngine engine = BackupEngine.getOneDriveInstance();

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
        
        //engine.deleteBackup("Wed_May_14_17-07-05_GMT-03-00_2014");
        //cb.doSuspendableRestore("Sun_Jun_08_15-43-14_BRT_2014", "/home/danilo/Videos", new Activity());
    }
}
