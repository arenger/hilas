package edu.uccs.arenger.hilas.dal;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import com.jolbox.bonecp.BoneCP;
import com.jolbox.bonecp.BoneCPConfig;

public final class Pool {
   private static BoneCP pool;

   private Pool() {}

   public static void init(Properties props) throws DalException {
      if (pool != null) { return; }
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
      } catch (SQLException e) {
         throw new DalException(e);
      }
   }

   protected static Connection getConnection() throws SQLException {
      return pool.getConnection();
   }

   public static void shutdown() {
      if (pool != null) {
         pool.shutdown();
      }
      pool = null;
   }
}
