package edu.uccs.arenger.hilas.dal;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jolbox.bonecp.BoneCP;
import com.jolbox.bonecp.BoneCPConfig;

public final class Pool {
   private static final Logger LOGGER = LoggerFactory.getLogger(Pool.class);

   private static BoneCP pool;

   private Pool() {}

   public static boolean init(Properties props) {
      if (pool != null) { return true; }
      boolean success = false;
      BoneCPConfig config = new BoneCPConfig();
      config.setJdbcUrl(String.format("jdbc:mysql://%s:%s/%s",
         props.getProperty("db.host"), props.getProperty("db.port"),
         props.getProperty("db.name")));
      config.setUsername(props.getProperty("db.username"));
      config.setPassword(props.getProperty("db.password"));
      config.setMinConnectionsPerPartition(1);
      config.setMaxConnectionsPerPartition(10);
      try {
         pool = new BoneCP(config);
         success = true;
      } catch (SQLException e) {
         LOGGER.error("DB connection problem", e);
      }
      return success;
   }

   public static Connection getConnection() throws SQLException {
      return pool.getConnection();
   }

   public static void shutdown() {
      if (pool != null) {
         pool.shutdown();
      }
      pool = null;
   }
}
