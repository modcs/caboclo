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


package org.caboclo.webservices;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.caboclo.activities.Activity;
import org.caboclo.service.BackupEngine;

import flexjson.JSONSerializer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.DELETE;
import org.codehaus.jettison.json.JSONArray;
import org.caboclo.activities.ActivityListener;
import org.caboclo.activities.ActivityStatus;


@Path("backups")
public class BackupResource {

    private static final String AUTHENTICATION_NEEDED = "{"
            + "\"sucess\": false,"
            + "\"message\": \"Not authenticated\""
            + "}";

    @GET
    @Path("{service}/all")
    public String listAllBackups(@PathParam("service") String service) {

        BackupEngine engine = getEngine(service);

        if (!engine.isAuthenticated()) {
            return AUTHENTICATION_NEEDED;
        }

        List<String> list = engine.listBackups();

        return new JSONSerializer().deepSerialize(list);
    }

    @GET
    @Path("{service}/auth/parameters")
    public String getAuthParameters(@PathParam("service") String service) {
        BackupEngine engine = getEngine(service);

        JSONSerializer serializer = new JSONSerializer();

        return serializer.deepSerialize(engine.getAuthenticationParameters());
    }

    @GET
    @Path("{service}/auth/parameters/{parameter}")
    public String getAuthParameters(@PathParam("service") String service, 
            @PathParam("parameter") String parameter) {
        BackupEngine engine = getEngine(service);

        String result = engine.getAuthenticationParameter(parameter);
        
        JSONObject json = new JSONObject();
        
        try {
            json.put(parameter, result);
        } catch (JSONException jSONException) {}
        
        System.out.println(result);
        System.out.println(json.toString());
        return json.toString();        
    }

    @GET
    @Path("{service}/isauth")
    public String isAuthenticated(@PathParam("service") String service) {
        BackupEngine engine = getEngine(service);
        try {
            boolean success = engine.isAuthenticated();
            JSONObject json = new JSONObject();
            json.put("success", success);
            return json.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"success\":false}";
        }
    }

    @POST
    @Path("{service}/auth")
    public String authenticate(@PathParam("service") String service,
            String parameters) {
        try {
            BackupEngine engine = getEngine(service);

            JSONObject jdata = new JSONObject(parameters);
            Iterator<String> nameItr = jdata.keys();
            Map<String, String> map = new HashMap<>();
            while (nameItr.hasNext()) {
                String name = nameItr.next();
                map.put(name, jdata.getString(name));
            }

            boolean success = engine.authenticate(map);

            JSONObject json = new JSONObject();
            json.put("success", success);

            return json.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"success\":false}";
        }
    }
 
    @POST
    @Path("{service}/doBackupMultiObject")
    public String doBackupMultiObject(@PathParam("service") String service, String body) {
        try {

            BackupEngine engine = getEngine(service);

            if (!engine.isAuthenticated()) {
                return AUTHENTICATION_NEEDED;
            }
            
            ArrayList<String> paths = new ArrayList<String>();
            
            JSONArray input = new JSONArray(body);
            
            for (int i = 0; i < input.length(); i++) {
                JSONObject element = input.getJSONObject(i);
                if (!element.isNull("path")) {
                    paths.add(element.getString("path"));
                }
            }
            
            String id = engine.doBackup(paths, ActivityListener.EMPTY_LISTENER);

            JSONObject response = new JSONObject();
            response.put("id", id);
            response.put("success", true);

            return response.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return "{\"success\": false}";

    }

    @POST
    @Path("{service}/doBackup")
    public String doBackup(@PathParam("service") String service, String body) {
        try {

            BackupEngine engine = getEngine(service);

            if (!engine.isAuthenticated()) {
                return AUTHENTICATION_NEEDED;
            }

            JSONObject json = new JSONObject(body);

            String path = json.getString("path");

            String id = engine.doBackup(path, ActivityListener.EMPTY_LISTENER);

            JSONObject response = new JSONObject();
            response.put("id", id);
            response.put("success", true);

            return response.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return "{\"success\": false}";

    }

    @POST
    @Path("{service}/restore")
    public String restoreBackup(@PathParam("service") String service, String body) {
        try {
            JSONObject json = new JSONObject(body);

            String backupId = json.getString("backupId");
            String targetPath = json.getString("targetPath");

            BackupEngine engine = getEngine(service);

            if (!engine.isAuthenticated()) {
                return AUTHENTICATION_NEEDED;
            }

            String id = engine.doRestore(backupId, targetPath);

            JSONObject response = new JSONObject();
            response.put("id", id);
            response.put("success", true);

            return response.toString();
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return "{\"success\": false}";

    }

    @GET
    @Path("{service}/activity/{id}")
    public String getActivity(@PathParam("service") String service,
            @PathParam("id") String id) {
        BackupEngine engine = getEngine(service);

        if (!engine.isAuthenticated()) {
            return AUTHENTICATION_NEEDED;
        }

        Activity act = engine.getActivity(id);

        if (act == null) {
            return "{}";
        }

        try {
            JSONObject json = new JSONObject();

            json.put("finished", act.isFinished());
            json.put("status", act.getStatus().toString());

            return json.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            return "{}";
        }
    }

    @DELETE
    @Path("{service}/activity/{id}")
    public String cancelActivity(@PathParam("service") String service,
            @PathParam("id") String id) {
        BackupEngine engine = getEngine(service);

        if (!engine.isAuthenticated()) {
            return AUTHENTICATION_NEEDED;
        }

        Activity act = engine.getActivity(id);

        if (act == null || act.getStatus() != ActivityStatus.RUNNING) {
            return "{\"success\":false}";
        }

        act.cancel();

        return "{\"success\":true}";
    }

    @POST
    @Path("{service}/activity/{id}/suspend")
    public String suspendActivity(@PathParam("service") String service,
            @PathParam("id") String id) {

        BackupEngine engine = getEngine(service);

        if (!engine.isAuthenticated()) {
            return AUTHENTICATION_NEEDED;
        }

        Activity act = engine.getActivity(id);
        JSONObject response = new JSONObject();

        if (act == null || act.getStatus() != ActivityStatus.RUNNING) {
            try {
                response.put("success", false);
                return response.toString();
            } catch (JSONException ex) {
                Logger.getLogger(BackupResource.class.getName()).log(Level.SEVERE, null, ex);
                return "{\"success\":false}";
            }
        } else {

            act.suspendActivity();
            System.out.println("Suspending");
            try {
                response.put("success", true);
                return response.toString();
            } catch (JSONException ex) {
                Logger.getLogger(BackupResource.class.getName()).log(Level.SEVERE, null, ex);
                return "{\"success\":true}";
            }
        }

    }

    @POST
    @Path("{service}/activity/{id}/resume")
    public String resumeActivity(@PathParam("service") String service,
            @PathParam("id") String id) {

        BackupEngine engine = getEngine(service);

        if (!engine.isAuthenticated()) {
            return AUTHENTICATION_NEEDED;
        }

        Activity act = engine.getActivity(id);

        if (act == null || act.getStatus() != ActivityStatus.SUSPENDED) {
            return "{\"success\":false}";
        }

        act.resumeActivity();
        System.out.println("Resuming");

        return "{\"success\":true}";
    }

    @GET
    @Path("{service}/activities")
    public String getActivities(@PathParam("service") String service) {
        BackupEngine engine = getEngine(service);

        if (!engine.isAuthenticated()) {
            return AUTHENTICATION_NEEDED;
        }

        List<Activity> list = engine.getActivities();

        return new JSONSerializer().deepSerialize(list);
    }

    private BackupEngine getEngine(String service) {

        BackupEngine engine = null;

        if (service.equals("googledrive")) {
            engine = BackupEngine.getGoogleDriveInstance();
        } else if (service.equals("dropbox")) {
            engine = BackupEngine.getDropboxInstance();
        } else if (service.equals("onedrive")) {
            engine = BackupEngine.getOneDriveInstance();
        } else if (service.equals("amazon")) {
            engine = BackupEngine.getAmazonInstance();
        } else if (service.equals("openstack")) {
            engine = BackupEngine.getOpenStackInstance();
        }

        return engine;
    }
}
