package com.potatosaucevfx.whitelistsync2.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.potatosaucevfx.whitelistsync2.WhitelistSync2;
import com.potatosaucevfx.whitelistsync2.models.OpUser;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;


/**
 * Class to read json data for the OP list
 * @author Richard Nader, Jr. <nader1rm@cmich.edu>
 */
public class OPlistRead {
    
    private static JsonParser parser = new JsonParser();

    // Get whitelisted uuids as a string array list
    public static ArrayList getOpsUUIDs() {
        ArrayList<String> uuids = new ArrayList<>();
        // OMG ITS A LAMBDA EXPRESSION!!! :D
        getWhitelistJson().forEach(emp -> parseToList(emp.getAsJsonObject(), uuids, "uuid"));
        return uuids;
    }

    // Get whitelisted usernames as a string array list
    public static ArrayList getOpsNames() {
        ArrayList<String> names = new ArrayList<>();
        // WOAH ITS A LAMBDA EXPRESSION!!! CRAZY COMPLEX STUFF GOIN ON RIGHT HERE!!! :D
        getWhitelistJson().forEach(emp -> parseToList(emp.getAsJsonObject(), names, "name"));
        return names;
    }

    // Get Arraylist of whitelisted users on server.
    public static ArrayList<OpUser> getOppedUsers() {
        ArrayList<OpUser> users = new ArrayList<>();
        // HOLY SHIT.. ANOTHER LAMBDA EXPRESSION!!!!
        getWhitelistJson().forEach((user) -> {
            String uuid = ((JsonObject) user).get("uuid").toString();
            String name = ((JsonObject) user).get("name").toString();
            int level = Integer.parseInt(((JsonObject) user).get("level").toString());
            boolean bypassesPlayerLimit = Boolean.parseBoolean(((JsonObject) user).get("level").toString());

            users.add(new OpUser(uuid, name, level, bypassesPlayerLimit, true));
        });
        return users;
    }

    private static void parseToList(JsonObject oplist, List list, String key) {
        list.add(oplist.get(key).getAsString());
    }

    private static JsonArray getWhitelistJson() {
        JsonArray oplist = null;
        try {
            oplist = (JsonArray) parser.parse(new FileReader(WhitelistSync2.SERVER_FILEPATH + "/ops.json"));
        } catch (FileNotFoundException e) {
            WhitelistSync2.logger.error("ops.json file not found! :O\n" + e.getMessage());
        } catch (JsonParseException e) {
            WhitelistSync2.logger.error("ops.json parse error!! D:\n" + e.getMessage());
        }
        WhitelistSync2.logger.debug("getWhitelistJson returned an array of " + oplist.size() + " entries.");
        return oplist;
    }
    
}
