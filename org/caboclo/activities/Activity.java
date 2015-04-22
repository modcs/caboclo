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


package org.caboclo.activities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.caboclo.service.BackupService;
import org.caboclo.service.Command;
import org.caboclo.service.ConsoleOutput;

public class Activity implements ConsoleOutput {

    private String id;
    private ActivityStatus status;
    private boolean isCanceled;
    private String description;
    private String date;
    private Command resumeCommand;
    private Map<String, Object> objects;
    private transient List<ActivityListener> listeners;
    private transient ActivityManager manager;
    private transient Thread thread;
    private transient BackupService backupService;

    private transient ConsoleOutput console = new ConsoleOutput() {
        @Override
        public void printOutput(String str) {
            System.out.println(str);
        }
    };

    public Activity() {
        id = UUID.randomUUID().toString();
        isCanceled = false;
        this.objects = new HashMap<>();
        listeners = new ArrayList<>();

        setStatus(ActivityStatus.NOT_STARTED);
    }

    public String getId() {
        return id;
    }

    public void storeObject(String key, Object obj) {
        objects.put(key, obj);
    }

    public Object getObject(String key) {
        return objects.get(key);
    }

    public boolean hasObject(String key) {
        return objects.containsKey(key);
    }

    public void removeObject(String key) {
        objects.remove(key);
    }

    public void startActivity(Runnable runnable) {

        date = new java.util.Date().toString();

        thread = new Thread(runnable);

        setStatus(ActivityStatus.RUNNING);

        thread.start();
    }

    public void suspendActivity() {
        setStatus(ActivityStatus.SUSPENDED);
    }

    public void resumeActivity() {
        thread = new Thread() {

            @Override
            public void run() {
                resumeCommand.execute();
            }

        };

        setStatus(ActivityStatus.RUNNING);

        thread.start();

    }

    public boolean isFinished() {
        return status == ActivityStatus.FINISHED;
    }

    public final void setStatus(ActivityStatus status) {
        this.status = status;

        notifyListeners();

        saveActivity();
    }

    private void notifyListeners() {

        for (ActivityListener listener : listeners) {
            switch (status) {
                case FINISHED:
                    listener.activityFinished(this);
                    break;
                case CANCELED:
                    listener.activityCanceled(this);
                    break;
                case SUSPENDED:
                    listener.activitySuspended(this);
                    break;
                case RUNNING:
                    listener.activityStarted(this);
                    break;
                case FAILED:
                    listener.activityFailed(this);
                    break;
            }
        }
    }

    public ActivityStatus getStatus() {
        return status;
    }

    public void cancel() {
        isCanceled = true;
        setStatus(ActivityStatus.CANCELED);
    }

    boolean isCanceled() {
        return isCanceled;
    }

    public void setConsole(ConsoleOutput console) {
        this.console = console;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public String getDate() {
        return date;
    }

    public void setResumeCommand(Command comm) {
        this.resumeCommand = comm;
    }

    public Command getResumeCommand() {
        return resumeCommand;
    }

    public void setBackupService(BackupService backupService) {
        this.backupService = backupService;
    }

    public BackupService getBackupService() {
        return backupService;
    }

    public void addListener(ActivityListener listener) {
        listeners.add(listener);
    }

    public void removeAllListeners() {
        listeners.clear();
    }

    @Override
    public void printOutput(String str) {
        console.printOutput(str);
    }

    public void setManager(ActivityManager manager) {
        this.manager = manager;
    }

    public void saveActivity() {
        if (manager != null) {
            this.manager.saveActivity(this);
        }
    }

    public boolean hasListener(ActivityListener listener) {
        return listeners.contains(listener);
    }

}
