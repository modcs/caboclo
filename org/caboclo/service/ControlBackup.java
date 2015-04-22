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

import org.caboclo.util.Browser;
import org.caboclo.activities.Activity;
import org.caboclo.activities.ActivityStatus;
import org.caboclo.clients.ApiClient;
import java.io.File;
import java.io.IOException;
import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.caboclo.util.OSValidator;


public class ControlBackup extends BackupService {

    public ControlBackup(ApiClient client) {
        super(client);
    }

    @Override
    public boolean authenticate(Map<String, String> parameters) {
        return this.client.authenticate(parameters);
    }

    @Override
    public String[] getAuthenticationParameters() {
        return client.getAuthenticationParameters();
    }

    @Override
    public String getAuthenticationParameter(String parameter) {
        return client.getAuthenticationParameter(parameter);
    }

    @Override
    public boolean isAuthenticated() {
        return this.client.isAuthenticated();
    }

    @Override
    public List<String> listAllBackups() {
        List<String> newList = new ArrayList<>();

        try {
            List<RemoteFile> backupList = client.getChildren("/backups");
            for (RemoteFile file : backupList) {
                String string = file.getPath();

                string = string.replace("\\", "/");
                String s[] = string.split("/");
                newList.add(s[s.length - 1]);
            }
        } catch (IOException ioex) {
            ioex.printStackTrace();
        }

        return newList;
    }

    @Override
    public void doRestore(String folder, String targetPath, Activity act) {
        String rootFolder = folder.startsWith("/backups/") ? folder : ("/backups/" + folder);

        act.storeObject("rootFolder", rootFolder);

        //TODO - Potentially long operation, todo: if the user abort it, then start all over again due to the recursive method
        
        AbstractQueue<RemoteFile> queue = getRemoteFileList(rootFolder);

        Command comm = new ResumeRestoreCommand(queue, targetPath, act);
        act.setResumeCommand(comm);

        act.saveActivity();

        resumeRestore(queue, targetPath, act);
    }

    void resumeRestore(AbstractQueue<RemoteFile> queue, String targetPath, Activity act) {

        try {

            do {
                if (act.getStatus() == ActivityStatus.SUSPENDED) {

                    act.printOutput("Suspending restore!");
                    return;

                } else if (act.getStatus() == ActivityStatus.CANCELED) {
                    act.printOutput("Activity cancelled.");
                    return;
                }

                if (act.hasObject("multiPartDownload")) {

                    MultiPartDownload mpd = (MultiPartDownload) act.getObject("multiPartDownload");

                    while (!mpd.isFinished()) {

                        if (act.getStatus() == ActivityStatus.SUSPENDED) {

                            act.printOutput("Suspending restore!");
                            return;

                        } else if (act.getStatus() == ActivityStatus.CANCELED) {
                            act.printOutput("Activity cancelled.");
                            return;
                        }

                        try {
                            client.getNextPart(mpd);

                            act.saveActivity();
                        } catch (IOException ex) {
                            act.setStatus(ActivityStatus.FAILED);
                            return;
                        }
                    }

                    act.removeObject("multiPartDownload");

                    act.saveActivity();
                }

                RemoteFile rf = null;

                if (!queue.isEmpty()) {
                    rf = queue.remove();
                } else {
                    break;
                }

                String root = (String) act.getObject("rootFolder");
                java.io.File file = new java.io.File(OSValidator.convertPath(rf.getPath(),
                        root, targetPath));

                if (rf.isDirectory()) {
                    if (!file.exists()) {
                        file.mkdirs();
                    }
                } else {
                    try {

                        act.printOutput("Downloading file: " + file);

                        MultiPartDownload mpd = client.initializeDownload(file, rf);

                        act.storeObject("multiPartDownload", mpd);

                        while (!mpd.isFinished()) {

                            if (act.getStatus() == ActivityStatus.SUSPENDED) {
                                act.printOutput("Suspending restore!");
                                return;

                            } else if (act.getStatus() == ActivityStatus.CANCELED) {
                                act.printOutput("Activity cancelled.");
                                return;
                            }

                            client.getNextPart(mpd);

                            act.saveActivity();

                            act.printOutput("Sent: " + mpd.getOffset() + " bytes of " + mpd.getRemotePath().getSize());
                        }

                        act.removeObject("multiPartDownload");

                        act.saveActivity();

                    } catch (IOException ex) {
                        act.setStatus(ActivityStatus.FAILED);
                        return;
                    }

                }

            } while (!queue.isEmpty());

            act.printOutput("Restore finished!");
            act.setStatus(ActivityStatus.FINISHED);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
        
    public void doBackup(String target, Activity act) {
        ArrayList<String> targets = new ArrayList<>();
        targets.add(target);
        this.doBackup(targets, act);
    }

    @Override
    public void doBackup(ArrayList<String> target, Activity act) {

        //Hash map that links each File to its related root folder
        AbstractQueue<BackupItem> queue = new ConcurrentLinkedQueue<BackupItem>();
        
        //Create list of objects to
        for (int i = 0; i < target.size(); i++) {
            String rootFolder = target.get(i);
            getLocalFileList(queue, rootFolder);
        }        

        String backupName = getBackupId();

        try {
            client.makedir("/backups/" + backupName);
        } catch (IOException ex) {
            Logger.getLogger(ControlBackup.class.getName()).log(Level.SEVERE, null, ex);
            act.cancel();
            return;
        }

        String backupDir = "/backups/" + backupName;

        Command comm = new ResumeBackupCommand(queue, backupDir, act);
        act.setResumeCommand(comm);

        resumeBackup(queue, backupDir, act);
    }


    void resumeBackup(AbstractQueue<BackupItem> queue,
            String backupDir, Activity act) {

        java.io.File file = null;
        act.setStatus(ActivityStatus.RUNNING);

        do {
            if (act.getStatus() == ActivityStatus.SUSPENDED) {

                act.printOutput("Suspending backup!");

                return;

            } else if (act.getStatus() == ActivityStatus.CANCELED) {
                act.printOutput("Activity cancelled.");
                return;
            } else {

                if (act.hasObject("multiPartUpload")) {
                    MultiPartUpload mpu = (MultiPartUpload) act.getObject("multiPartUpload");

                    while (mpu.hasMoreParts()) {
                        if (act.getStatus() == ActivityStatus.SUSPENDED) {
                            act.printOutput("Suspending backup!");
                            return;
                        }
                        client.sendNextPart(mpu);
                    }

                    client.finalizeUpload(mpu);
                    act.removeObject("multiPartUpload");
                }

                BackupItem item;
                
                if (!queue.isEmpty()) {
                    item = queue.remove();
                } else {
                    break;
                }

                file = item.getFile();
                String fileName = backupDir + OSValidator.getAbsolutePath(item.getFile(), item.getRootPath());

                try {
                    if (file.isDirectory()) {
                        client.makedir(fileName);
                    } else {

                        act.printOutput("Uploading file: " + file.getAbsolutePath());

                        if (file.length() < Constants.CHUNK_UPLOAD_SIZE) {
                            client.putFile(file, fileName);
                        } else {
                            MultiPartUpload mpu = client.initializeUpload(file, fileName);
                            act.storeObject("multiPartUpload", mpu);

                            while (mpu.hasMoreParts()) {
                                if (act.getStatus() == ActivityStatus.SUSPENDED) {
                                    act.printOutput("Suspending backup!");
                                    return;
                                } else if (act.getStatus() == ActivityStatus.CANCELED) {
                                    act.printOutput("Activity cancelled.");
                                    return;
                                }
                                client.sendNextPart(mpu);
                            }

                            client.finalizeUpload(mpu);
                            act.removeObject("multiPartUpload");
                        }
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

            }
        } while (queue.size() > 0);

        act.printOutput("Backup finished!");
        act.setStatus(ActivityStatus.FINISHED);
    }

    private void getLocalFileList(AbstractQueue<BackupItem> list, String rootFolder) {
        File dir = new java.io.File(rootFolder);
        list.add(new BackupItem(dir, rootFolder));

        if (dir.isDirectory()) {
            getLocalFileListAux(dir, list, rootFolder);
        }        
    }

    private AbstractQueue<RemoteFile> getRemoteFileList(String folder) {
        AbstractQueue<RemoteFile> list = new ConcurrentLinkedQueue<>();

        try {
            getRemoteFileListAux(folder, list);
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return list;
    }

    private void getLocalFileListAux(java.io.File dir, AbstractQueue<BackupItem> list, String rootFolder) {

        for (java.io.File child : dir.listFiles()) {

            list.add(new BackupItem(child, rootFolder));

            if (child.isDirectory()) {
                getLocalFileListAux(child, list, rootFolder);
            }
        }
    }

    public static void main(String[] args) {
        BackupEngine engine = BackupEngine.getDropboxInstance();

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

        ControlBackup cb = (ControlBackup) engine.getService();

        System.out.println(cb.getRemoteFileList("/backups"));
    }

    private void getRemoteFileListAux(String folder, AbstractQueue<RemoteFile> list) throws IOException {

        for (RemoteFile child : client.getChildren(folder)) {
            list.add(child);

            if (child.isDirectory()) {
                getRemoteFileListAux(child.getPath(), list);
            }
        }
    }

    @Override
    public void deleteBackup(String backupId) {
        try {
            String remoteFolder = backupId.startsWith("/backups/") ? backupId : ("/backups/" + backupId);

            if (this.client.isFolder(remoteFolder)) {
                this.client.removeFolder(remoteFolder);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

class BackupItem {
    private java.io.File file;
    private String rootPath;

    public BackupItem(File file, String rootPath) {
        this.file = file;
        this.rootPath = rootPath;
    }
    
    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }        
}

class ResumeBackupCommand implements Command {

    private AbstractQueue<BackupItem> queue;
    private Activity act;
    private String backupDir;
    private ControlBackup cb;

    public ResumeBackupCommand(AbstractQueue<BackupItem> queue,
            String backupDir, Activity act) {
        this.queue = queue;
        this.act = act;
        this.backupDir = backupDir;
    }

    @Override
    public void execute() {
        cb = (ControlBackup) act.getBackupService();
        cb.resumeBackup(queue, backupDir, act);
    }
}

class ResumeRestoreCommand implements Command {

    private AbstractQueue<RemoteFile> queue;
    private String targetPath;
    private Activity act;
    private ControlBackup cb;

    public ResumeRestoreCommand(AbstractQueue<RemoteFile> queue, String targetPath, Activity act) {
        this.queue = queue;
        this.targetPath = targetPath;
        this.act = act;
    }

    @Override
    public void execute() {
        cb = (ControlBackup) act.getBackupService();
        cb.resumeRestore(queue, targetPath, act);
    }

}
