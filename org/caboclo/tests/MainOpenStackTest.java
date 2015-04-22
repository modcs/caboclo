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
import org.caboclo.service.BackupEngine;
import org.caboclo.clients.OpenStackClient;


public class MainOpenStackTest {

    public static void main(String[] args) throws IOException {

        BackupEngine engine = BackupEngine.getOpenStackInstance();

        if (!engine.isAuthenticated()) {
            Map<String, String> param = new HashMap<>();
            param.put(OpenStackClient.AUTH_URL,"https://192.168.44.131");
            
            param.put(OpenStackClient.USER_NAME,"account:bruno");
            param.put(OpenStackClient.PASSWORD,"password123");
            
            engine.authenticate(param);
        }

        //engine.doSuspendableBackupAsync("C:\\Users\\Rubens\\test");
        //engine.doRestoreAsync("Thu_May_15_01-23-51_GMT-03-00_2014", "C:\\Users\\Rubens");
        System.out.println("ok!! :D");
    }
}
