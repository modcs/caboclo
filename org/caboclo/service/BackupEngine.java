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

import org.caboclo.clients.Authenticator;
import org.caboclo.activities.ActivityListener;
import org.caboclo.activities.ActivityManager;
import org.caboclo.activities.Activity;
import org.caboclo.clients.GoogleDriveClient;
import org.caboclo.clients.OpenStackClient;
import org.caboclo.clients.AmazonClient;
import org.caboclo.clients.DropboxClient;
import org.caboclo.clients.ApiClient;
import org.caboclo.clients.OneDriveClient;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;


public class BackupEngine implements Authenticator {

    private static BackupEngine dropboxInstance;
    private static BackupEngine oneDriveInstance;
    private static BackupEngine googleDriveInstance;
    private static BackupEngine amazonInstance;
    private static BackupEngine openStackInstance;

    private final ActivityManager activityManager;

    public static BackupEngine getAmazonInstance() {
        if (amazonInstance == null) {
            amazonInstance = new BackupEngine(new ControlBackup(new AmazonClient()));
        }

        return amazonInstance;
    }

    public static BackupEngine getOneDriveInstance() {
        if (oneDriveInstance == null) {
            oneDriveInstance = new BackupEngine(new ControlBackup(new OneDriveClient()));
        }

        return oneDriveInstance;
    }

    public static BackupEngine getOpenStackInstance() {
        if (openStackInstance == null) {
            openStackInstance = new BackupEngine(new ControlBackup(new OpenStackClient()));
        }

        return openStackInstance;
    }

    public static BackupEngine getDropboxInstance() {
        if (dropboxInstance == null) {
            dropboxInstance = new BackupEngine(new ControlBackup(new DropboxClient()));
        }

        return dropboxInstance;
    }

    public static BackupEngine getGoogleDriveInstance() {
        if (googleDriveInstance == null) {
            googleDriveInstance = new BackupEngine(new ControlBackup(new GoogleDriveClient()));
        }

        return googleDriveInstance;
    }
    private transient BackupService service;

    public BackupEngine(BackupService service) {
        this.service = service;
        this.activityManager = ActivityManager.getInstance();
    }

    @Override
    public boolean authenticate(Map<String, String> parameters) {
        return this.service.authenticate(parameters);
    }

    @Override
    public String[] getAuthenticationParameters() {
        return service.getAuthenticationParameters();
    }

    @Override
    public String getAuthenticationParameter(String parameter) {
        return service.getAuthenticationParameter(parameter);
    }

    @Override
    public boolean isAuthenticated() {
        return this.service.isAuthenticated();
    }

    public List<String> listBackups() {
        return service.listAllBackups();
    }

    public Activity getActivity(String id) {
        return getActivityManager().getActivity(id);
    }

    public List<Activity> getActivities() {
        List<Activity> list = activityManager.getActivities();
        
        for(Activity act: list){
            act.setBackupService(service);
        }
        
        return list;
    }

    public String doBackup(final String path, final ActivityListener listener) {
        ArrayList<String> paths = new ArrayList<>();
        paths.add(path);
        return this.doBackup(paths, listener);
    }
    
    public String doBackup(final ArrayList<String> paths, final ActivityListener listener) {
        final Activity act = new Activity();
        act.setBackupService(service);
        act.setManager(activityManager);
        act.addListener(listener);

        act.setDescription("Method - doBackup; Arguments: MultiplePaths ");

        Runnable run = new Runnable() {
            @Override
            public void run() {
                service.doBackup(paths, act);

                System.out.println("Stopping thread!");
            }
        };

        activityManager.addActivity(act);

        act.startActivity(run);

        return act.getId();        
    }

    public String doRestore(final String backupId, final String targetPath) {
        return doRestore(backupId, targetPath, ActivityListener.EMPTY_LISTENER);
    }

    public String doRestore(final String remoteFolder, final String targetPath, ActivityListener listener) {
        final Activity act = new Activity();
        act.setBackupService(service);
        
        act.setManager(activityManager);
        act.addListener(listener);

        act.setDescription("Method - doRestore; Arguments: (remoteFolder = \"" + remoteFolder + "\", targetPath = \""
                + targetPath
                + "\")");

        Runnable run = new Runnable() {
            @Override
            public void run() {
                Date date = new Date();
                DateFormat formato = new SimpleDateFormat("HH:mm:ss.SSS");
                String formattedDate = formato.format(date);
                long startTotal = System.currentTimeMillis();
                System.out.println("Started: " + formattedDate);
                long diff;
                long end;

                service.doRestore(remoteFolder, targetPath, act);

                System.out.println("Stopping thread!");
                end = System.currentTimeMillis();
                diff = end - startTotal;

                System.out.println("Finished: " + formattedDate);
                System.out.println("Execution time: " + org.caboclo.util.TimeConverter.converteToTime(diff));
            }
        };

        activityManager.addActivity(act);

        act.startActivity(run);

        return act.getId();
    }

    public ApiClient getClient() {
        return service.getClient();
    }

    public BackupService getService() {
        return service;
    }

    public ActivityManager getActivityManager() {
        return activityManager;
    }        
    
    public void deleteBackup(String backupID){
        this.service.deleteBackup(backupID);
    }

}
