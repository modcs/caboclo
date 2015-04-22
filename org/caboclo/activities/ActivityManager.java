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
import java.util.List;
import org.neodatis.odb.ODB;
import org.neodatis.odb.ODBFactory;
import org.neodatis.odb.Objects;
import org.neodatis.odb.core.query.IQuery;
import org.neodatis.odb.core.query.criteria.Where;
import org.neodatis.odb.impl.core.query.criteria.CriteriaQuery;


public class ActivityManager {

    private static ActivityManager instance;
    private static final String ODB_NAME = "activities.db";

    public static ActivityManager getInstance() {
        if (instance == null) {
            instance = new ActivityManager();
        }

        return instance;
    }

    private final ODB odb;

    public ActivityManager() {
        odb = ODBFactory.open(ODB_NAME);
    }

    public Activity getActivity(String id) {
        IQuery query = new CriteriaQuery(Activity.class, Where.equal("id", id));
        Objects<Activity> objs = odb.getObjects(query);

        return objs.getFirst();
    }

    public void addActivity(Activity act) {
        saveActivity(act);
    }

    public List<Activity> getActivities() {

        IQuery query = new CriteriaQuery(Activity.class);
        Objects<Activity> objs = odb.getObjects(query);

        List<Activity> list = new ArrayList<>();

        for (Activity act : objs) {
            act.setManager(this);
            list.add(act);
        }
        
        return list;
    }

    public void saveActivity(Activity act) {
        odb.store(act);
        odb.commit();
    }

    public void suspendAllActivities() {
        for (Activity act : getActivities()) {
            if (act.getStatus() == ActivityStatus.RUNNING) {
                act.suspendActivity();
            }
        }
    }

    public void closeDB() {
        odb.close();
    }
}
