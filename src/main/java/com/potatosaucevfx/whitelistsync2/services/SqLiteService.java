package com.potatosaucevfx.whitelistsync2.services;

import com.mojang.authlib.GameProfile;
import com.potatosaucevfx.whitelistsync2.WhitelistSync2;
import com.potatosaucevfx.whitelistsync2.config.ConfigHandler;
import com.potatosaucevfx.whitelistsync2.json.OPlistRead;
import com.potatosaucevfx.whitelistsync2.json.WhitelistRead;
import com.potatosaucevfx.whitelistsync2.models.OpUser;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * Service for SQLITE Databases
 *
 * @author Richard Nader, Jr. <nader1rm@cmich.edu>
 */
public class SqLiteService implements BaseService {

    // TODO: Prepared statements.
    private File databaseFile;
    private Connection conn = null;

    public SqLiteService() {
        WhitelistSync2.logger.info("Setting up the SQLITE service...");
        this.databaseFile = new File(ConfigHandler.sqliteDatabasePath);
        loadDatabase();
    }

    /**
     * Method to load database on startup.
     * @return success
     */
    private Boolean loadDatabase() {
        // If database does not exist.
        if (!databaseFile.exists()) {
            createNewDatabase();
        }

        // Create whitelist table if it doesn't exist.
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + ConfigHandler.sqliteDatabasePath);
            WhitelistSync2.logger.info("Connected to SQLite database successfully!");

            // SQL statement for creating a new table
            String sql = "CREATE TABLE IF NOT EXISTS whitelist (\n"
                    + "	uuid text NOT NULL PRIMARY KEY,\n"
                    + "	name text,\n"
                    + " whitelisted integer NOT NULL);";
            Statement stmt = conn.createStatement();
            stmt.execute(sql);

            if (ConfigHandler.SYNC_OP_LIST) {
                // SQL statement for creating a new table
                sql = "CREATE TABLE IF NOT EXISTS op (\n"
                        + "	uuid text NOT NULL PRIMARY KEY,\n"
                        + "	name text,\n"
                        + "	level integer,\n"
                        + "	bypassesPlayerLimit integer,\n"
                        + " isOp integer NOT NULL);";
                Statement stmt2 = conn.createStatement();
                stmt2.execute(sql);
            }

        } catch (SQLException e) {
            WhitelistSync2.logger.error("Error creating op or whitelist table!\n" + e.getMessage());
            return false;
        } finally {
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException ex) {
                WhitelistSync2.logger.error("Error closing connection for creating whitelist table!\n" + ex.getMessage());
            }
        }
        return true;
    }

    /**
     * Pushes local json whitelist to the database
     * @param server
     */
    @Override
    public void pushLocalWhitelistToDatabase(MinecraftServer server) {
        // Load local whitelist to memory.
        ArrayList<String> uuids = WhitelistRead.getWhitelistUUIDs();
        ArrayList<String> names = WhitelistRead.getWhitelistNames();

        // Start job on thread to avoid lag.
        new Thread(() -> {
            // Keep track of records.
            int records = 0;
            try {
                // Connect to database.
                Connection conn1 = DriverManager.getConnection("jdbc:sqlite:" + ConfigHandler.sqliteDatabasePath);
                Statement stmt = conn1.createStatement();
                long startTime = System.currentTimeMillis();
                // Loop through local whitelist and insert into database.
                for (int i = 0; i < uuids.size() || i < names.size(); i++) {
                    if ((uuids.get(i) != null) && (names.get(i) != null)) {
                        String sql = "INSERT OR REPLACE INTO whitelist(uuid, name, whitelisted) VALUES (\'" + uuids.get(i) + "\', \'" + names.get(i) + "\', 1);";
                        stmt.execute(sql);
                        records++;
                    }
                }
                // Record time taken.
                long timeTaken = System.currentTimeMillis() - startTime;
                WhitelistSync2.logger.debug("Whitelist Table Updated | Took " + timeTaken + "ms | Wrote " + records + " records.");
                stmt.close();
                conn1.close();
                
            } catch (SQLException e) {
                WhitelistSync2.logger.error("Failed to update database with local records.\n" + e.getMessage());
            }
        }).start();
    }

    /**
     * Pushes local json op list to the database
     * @param server
     */
    @Override
    public void pushLocalOpListToDatabase(MinecraftServer server) {
        // Load local ops to memory.
        ArrayList<OpUser> opUsers = OPlistRead.getOppedUsers();

        // Start job on thread to avoid lag.
        new Thread(() -> {
            // Keep track of records.
            int records = 0;
            try {
                // Connect to database.
                Connection conn1 = DriverManager.getConnection("jdbc:sqlite:" + ConfigHandler.sqliteDatabasePath);

                // If syncing op list
                if (ConfigHandler.SYNC_OP_LIST) {
                    records = 0;
                    long opStartTime = System.currentTimeMillis();
                    // Loop through ops list and add to DB
                    for (OpUser opUser : opUsers) {
                        try {
                            PreparedStatement sql = conn1.prepareStatement("INSERT IGNORE INTO " + ConfigHandler.mySQL_DBname + ".op(uuid, name, level, bypassesPlayerLimit, isOp) VALUES (?, ?, ?, ?, true)");
                            sql.setString(1, opUser.getUuid());
                            sql.setString(2, opUser.getName());
                            sql.setInt(3, opUser.getLevel());
                            sql.setInt(4, opUser.isBypassesPlayerLimit() ? 1 : 0);
                            sql.executeUpdate();
                            records++;
                        } catch (ClassCastException e) {
                            e.printStackTrace();
                        }
                        records++;
                    }
                    // Record time taken.
                    long opTimeTaken = System.currentTimeMillis() - opStartTime;
                    WhitelistSync2.logger.info("Wrote " + records + " to op table in " + opTimeTaken + "ms.");
                    WhitelistSync2.logger.debug("Op Table Updated | Took " + opTimeTaken + "ms | Wrote " + records + " records.");
                } else {
                    // If op syncing not enabled
                    WhitelistSync2.logger.error("Op list syncing is currently disabled in your config. "
                            + "Please enable it and restart the server to use this feature");
                }
                conn1.close();
            } catch (SQLException e) {
                WhitelistSync2.logger.error("Failed to update database with local records.\n" + e.getMessage());
            }
        }).start();
    }

    
    /**
     * Pull uuids of whitelisted players from database
     * @param server
     * @return List of whitelisted player uuids
     */
    @Override
    public ArrayList<String> pullWhitelistedUuidsFromDatabase(MinecraftServer server) {
        // ArrayList for uuids.
        ArrayList<String> uuids = new ArrayList<>();

        try {
            // Keep track of records.
            int records = 0;

            // Connect to database.
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + ConfigHandler.sqliteDatabasePath);
            Statement stmt = conn.createStatement();
            String sql = "SELECT uuid, whitelisted FROM whitelist;";

            // Start time of querry.
            long startTime = System.currentTimeMillis();

            stmt.execute(sql);
            ResultSet rs = stmt.executeQuery(sql);

            // Add querried results to arraylist.
            while (rs.next()) {
                if (rs.getInt("whitelisted") == 1) {
                    uuids.add(rs.getString("uuid"));
                }
                records++;
            }

            // Time taken
            long timeTaken = System.currentTimeMillis() - startTime;

            WhitelistSync2.logger.debug("Database Pulled whitelist uuids | Took " + timeTaken + "ms | Read " + records + " records.");

            stmt.close();
            conn.close();
        } catch (SQLException e) {
            WhitelistSync2.logger.error("Error querrying uuids from database!\n" + e.getMessage());
        }
        return uuids;
    }

    /**
     * Pull uuids of opped players from database
     * @param server
     * @return List of opped player uuids
     */
    @Override
    public ArrayList<String> pullOpUuidsFromDatabase(MinecraftServer server) {

        if (ConfigHandler.SYNC_OP_LIST) {

            // ArrayList for uuids.
            ArrayList<String> uuids = new ArrayList<>();

            try {
                // Keep track of records.
                int records = 0;

                // Connect to database.
                Connection conn = DriverManager.getConnection("jdbc:sqlite:" + ConfigHandler.sqliteDatabasePath);
                Statement stmt = conn.createStatement();
                String sql = "SELECT uuid, isOp, FROM op;";

                // Start time of querry.
                long startTime = System.currentTimeMillis();

                stmt.execute(sql);
                ResultSet rs = stmt.executeQuery(sql);

                // Add querried results to arraylist.
                while (rs.next()) {
                    if (rs.getInt("isOp") == 1) {
                        uuids.add(rs.getString("uuid"));
                    }
                    records++;
                }

                // Time taken
                long timeTaken = System.currentTimeMillis() - startTime;

                WhitelistSync2.logger.debug("Database Pulled op uuids | Took " + timeTaken + "ms | Read " + records + " records.");

                stmt.close();
                conn.close();
            } catch (SQLException e) {
                WhitelistSync2.logger.error("Error querrying uuids from sqlite database!\n" + e.getMessage());
            }
            return uuids;

        } else {

            WhitelistSync2.logger.error("Op list syncing is currently disabled in your config. "
                    + "Please enable it and restart the server to use this feature");

            return new ArrayList<>();
        }
    }

    
    /**
     * Pull names of whitelisted players from database
     * @param server
     * @return List of whitelisted players names
     */
    @Override
    public ArrayList<String> pullWhitelistedNamesFromDatabase(MinecraftServer server) {
        // ArrayList for names.
        ArrayList<String> names = new ArrayList<>();

        try {

            // Keep track of records.
            int records = 0;

            // Connect to database.
            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + ConfigHandler.sqliteDatabasePath);
            Statement stmt = conn.createStatement();
            String sql = "SELECT name, whitelisted FROM whitelist;";

            // Start time of querry.
            long startTime = System.currentTimeMillis();

            stmt.execute(sql);
            ResultSet rs = stmt.executeQuery(sql);

            // Save querried return to names list.
            while (rs.next()) {
                if (rs.getInt("whitelisted") == 1) {
                    names.add(rs.getString("name"));
                }
                records++;
            }

            // Total time taken.
            long timeTaken = System.currentTimeMillis() - startTime;

            WhitelistSync2.logger.debug("Database Pulled whitelist names | Took " + timeTaken + "ms | Read " + records + " records.");

            stmt.close();
            conn.close();
        } catch (SQLException e) {
            WhitelistSync2.logger.error("Error querrying names from database!\n" + e.getMessage());
        }
        return names;
    }
    
    /**
     * Pull names of opped players from database
     * @param server
     * @return List of opped players names
     */
    @Override
    public ArrayList<String> pullOppedNamesFromDatabase(MinecraftServer server) {

        if (ConfigHandler.SYNC_OP_LIST) {
            // ArrayList for names.
            ArrayList<String> names = new ArrayList<>();

            try {

                // Keep track of records.
                int records = 0;

                // Connect to database.
                Connection conn = DriverManager.getConnection("jdbc:sqlite:" + ConfigHandler.sqliteDatabasePath);
                Statement stmt = conn.createStatement();
                String sql = "SELECT name, isOp FROM op;";

                // Start time of querry.
                long startTime = System.currentTimeMillis();

                stmt.execute(sql);
                ResultSet rs = stmt.executeQuery(sql);

                // Save querried return to names list.
                while (rs.next()) {
                    if (rs.getInt("isOp") == 1) {
                        names.add(rs.getString("name"));
                    }
                    records++;
                }

                // Total time taken.
                long timeTaken = System.currentTimeMillis() - startTime;

                WhitelistSync2.logger.debug("Database Pulled op names | Took " + timeTaken + "ms | Read " + records + " records.");

                stmt.close();
                conn.close();
            } catch (SQLException e) {
                WhitelistSync2.logger.error("Error querrying names from database!\n" + e.getMessage());
            }
            return names;

        } else {
            WhitelistSync2.logger.error("Op list syncing is currently disabled in your config. "
                    + "Please enable it and restart the server to use this feature");

            return new ArrayList<>();
        }
    }
    
    /**
     * Method adds player to whitelist in database
     * @param player 
     */
    // TODO: Add some sort of feedback
    @Override
    public void addPlayerToDatabaseWhitelist(GameProfile player) {
        new Thread(() -> {
            try {
                // Open connection
                Connection conn1 = DriverManager.getConnection("jdbc:sqlite:" + ConfigHandler.sqliteDatabasePath);
                Statement stmt = conn1.createStatement();
                String sql = "INSERT OR REPLACE INTO whitelist(uuid, name, whitelisted) VALUES (\'" + player.getId() + "\', \'" + player.getName() + "\', 1);";
                // Start time.
                long startTime = System.currentTimeMillis();
                // Execute statement.
                stmt.execute(sql);
                // Time taken.
                long timeTaken = System.currentTimeMillis() - startTime;
                WhitelistSync2.logger.debug("Database Added " + player.getName() + " to whitelist | Took " + timeTaken + "ms");
                stmt.close();
                conn1.close();
            } catch (SQLException e) {
                WhitelistSync2.logger.error("Error adding " + player.getName() + " to whitelist database!\n" + e.getMessage());
            }
        }).start();
    }

    /**
     * Method removes player from whitelist in database
     * @param player 
     */
    // TODO: Add some sort of feedback
    @Override
    public void removePlayerFromDatabaseWhitelist(GameProfile player) {
        new Thread(() -> {
            try {
                // Open connection
                Connection conn1 = DriverManager.getConnection("jdbc:sqlite:" + ConfigHandler.sqliteDatabasePath);
                Statement stmt = conn1.createStatement();
                String sql = "INSERT OR REPLACE INTO whitelist(uuid, name, whitelisted) VALUES "
                        + "(\'" + player.getId() + "\', \'" + player.getName() + "\', 0);";
                // Start time.
                long startTime = System.currentTimeMillis();
                // Execute SQL.
                stmt.execute(sql);
                // Time taken
                long timeTaken = System.currentTimeMillis() - startTime;
                WhitelistSync2.logger.debug("Database Removed " + player.getName() + " from whitelist | Took " + timeTaken + "ms");
                stmt.close();
                conn1.close();
            } catch (SQLException e) {
                WhitelistSync2.logger.error("Error removing " + player.getName() + " from whitelist database!\n" + e.getMessage());
            }
        }).start();
    }
    
    /**
     * Method adds player to op list in database
     * @param player 
     */
    // TODO: Add some sort of feedback
    @Override
    public void addPlayerToDatabaseOp(GameProfile player) {
        new Thread(() -> {
            try {
                ArrayList<OpUser> oppedUsers = OPlistRead.getOppedUsers();
                // Open connection
                Connection conn1 = DriverManager.getConnection("jdbc:sqlite:" + ConfigHandler.sqliteDatabasePath);
                Statement stmt = conn1.createStatement();
                
                int playerOpLevel = 1;
                int isBypassesPlayerLimit = 1;
                
                for (OpUser opUser : oppedUsers) {
                    if (opUser.getUuid().equalsIgnoreCase(player.getId().toString())) {
                        playerOpLevel = opUser.getLevel();
                        isBypassesPlayerLimit = opUser.isBypassesPlayerLimit() ? 1 : 0;
                    }
                }
                
                String sql = "INSERT OR REPLACE INTO op(uuid, name, level, isOp, bypassesPlayerLimit) VALUES "
                        + "(\'" + player.getId() + "\', \'" + player.getName() + "\', " + playerOpLevel +  ", 1, " + isBypassesPlayerLimit + ");";
                // Start time.
                long startTime = System.currentTimeMillis();
                // Execute statement.
                stmt.execute(sql);
                // Time taken.
                long timeTaken = System.currentTimeMillis() - startTime;
                WhitelistSync2.logger.debug("Database Added " + player.getName() + " to ops | Took " + timeTaken + "ms");
                stmt.close();
                conn1.close();
            } catch (SQLException e) {
                WhitelistSync2.logger.error("Error adding " + player.getName() + " to op database!\n" + e.getMessage());
            }
        }).start();
    }

    /**
     * Method removes player from op list in database
     * @param player 
     */
    // TODO: Add some sort of feedback
    @Override
    public void removePlayerFromDatabaseOp(GameProfile player) {
        new Thread(() -> {
            try {
                // Open connection
                Connection conn1 = DriverManager.getConnection("jdbc:sqlite:" + ConfigHandler.sqliteDatabasePath);
                Statement stmt = conn1.createStatement();
                String sql = "INSERT OR REPLACE INTO op(uuid, name, isOp) VALUES (\'" + player.getId() + "\', \'" + player.getName() + "\', 0);";
                // Start time.
                long startTime = System.currentTimeMillis();
                // Execute SQL.
                stmt.execute(sql);
                // Time taken
                long timeTaken = System.currentTimeMillis() - startTime;
                WhitelistSync2.logger.debug("Database Removed " + player.getName() + " from ops | Took " + timeTaken + "ms");
                stmt.close();
                conn1.close();
            } catch (SQLException e) {
                WhitelistSync2.logger.error("Error removing " + player.getName() + " from op database!\n" + e.getMessage());
            }
        }).start();
    }

    /**
     * Method pulls whitelist from database and merges it into the local whitelist
     * @param server 
     */
    @Override
    public void updateLocalWhitelistFromDatabase(MinecraftServer server) {
        new Thread(() -> {
            try {
                int records = 0;
                Connection conn1 = DriverManager.getConnection("jdbc:sqlite:" + ConfigHandler.sqliteDatabasePath);
                Statement stmt = conn1.createStatement();
                String sql = "SELECT name, uuid, whitelisted FROM whitelist;";
                long startTime = System.currentTimeMillis();
                stmt.execute(sql);
                ResultSet rs = stmt.executeQuery(sql);
                ArrayList<String> localUuids = WhitelistRead.getWhitelistUUIDs();
                while (rs.next()) {
                    int whitelisted = rs.getInt("whitelisted");
                    String uuid = rs.getString("uuid");
                    String name = rs.getString("name");
                    GameProfile player = new GameProfile(UUID.fromString(uuid), name);

                    if (whitelisted == 1) {
                        if (!localUuids.contains(uuid)) {
                            try {
                                server.getPlayerList().addWhitelistedPlayer(player);
                            } catch (NullPointerException e) {
                                WhitelistSync2.logger.error("Player is null?\n" + e.getMessage());
                            }
                        }
                    } else {
                        WhitelistSync2.logger.debug(uuid + " is NOT whitelisted.");
                        if (localUuids.contains(uuid)) {
                            server.getPlayerList().removePlayerFromWhitelist(player);
                            WhitelistSync2.logger.info("Removed player " + name);
                        }
                    }
                    records++;
                }
                long timeTaken = System.currentTimeMillis() - startTime;
                WhitelistSync2.logger.debug("Whitelist Table Pulled | Took " + timeTaken + "ms | Wrote " + records + " records.");
                //WhitelistSync2.logger.info("Local whitelist.json up to date!");

                stmt.close();
                conn1.close();
            } catch (SQLException e) {
                WhitelistSync2.logger.error("Error querying whitelisted players from database!\n" + e.getMessage());
            }
        }).start();
    }
    
    /**
     * Method pulls op list from database and merges it into the local op list
     * @param server 
     */
    @Override
    public void updateLocalOpListFromDatabase(MinecraftServer server) {
        new Thread(() -> {
            try {
                int records = 0;
                Connection conn1 = DriverManager.getConnection("jdbc:sqlite:" + ConfigHandler.sqliteDatabasePath);
                Statement stmt = conn1.createStatement();
                String sql = "SELECT name, uuid, isOp FROM op;";
                long startTime = System.currentTimeMillis();
                stmt.execute(sql);
                ResultSet rs = stmt.executeQuery(sql);
                ArrayList<String> localUuids = OPlistRead.getOpsUUIDs();
                while (rs.next()) {
                    
                    String uuid = rs.getString("uuid");
                    String name = rs.getString("name");
                    int opped = rs.getInt("isOp");
                    
                    GameProfile player = new GameProfile(UUID.fromString(uuid), name);

                    if (opped == 1) {
                        if (!localUuids.contains(uuid)) {
                            try {
                                server.getPlayerList().addOp(player);
                            } catch (NullPointerException e) {
                                WhitelistSync2.logger.error("Player is null?\n" + e.getMessage());
                            }
                        }
                    } else {
                        WhitelistSync2.logger.debug(uuid + " is NOT opped.");
                        if (localUuids.contains(uuid)) {
                            server.getPlayerList().removeOp(player);
                            WhitelistSync2.logger.debug("Removed player " + name);
                        }
                    }
                    records++;
                }
                long timeTaken = System.currentTimeMillis() - startTime;
                WhitelistSync2.logger.debug("Op Table Pulled | Took " + timeTaken + "ms | Wrote " + records + " records.");
                //WhitelistSync2.logger.info("Local ops.json up to date!");

                stmt.close();
                conn1.close();
            } catch (SQLException e) {
                WhitelistSync2.logger.error("Error querying whitelisted players from database!\n" + e.getMessage());
            }
        }).start();
    }

    /**
     * Method to create a new sqlite database file
     */
    private void createNewDatabase() {
        String url = "jdbc:sqlite:" + ConfigHandler.sqliteDatabasePath;
        try {
            Connection conn = DriverManager.getConnection(url);
            if (conn != null) {
                WhitelistSync2.logger.info("A new database \"" + ConfigHandler.sqliteDatabasePath + "\" has been created.");
            }
        } catch (SQLException e) {
            WhitelistSync2.logger.error("Error creating non-existing database!\n" + e.getMessage());
        }
    }

}
