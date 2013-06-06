package edu.uccs.arenger.hilas.dal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Util {
   private static final Logger LOGGER = LoggerFactory.getLogger(Util.class);

   private Util() {}

   public static void notNull(Object o, String name) throws DalException {
      if (o == null) {
         throw new DalException(name + " can't be null");
      }
   }

   //many of the hilas tables have a single primary key of type varchar
   public static boolean simplePkExists(
      Connection conn, String sql, String id) throws SQLException {
      boolean ret = true;
      PreparedStatement ps = null;
      ResultSet rs = null;
      try {
         ps = conn.prepareStatement(sql);
         ps.setString(1,id);
         rs = ps.executeQuery();
         ret = rs.next();
      } finally {
         close(rs);
         close(ps);
      }
      return ret;
   }

   public static void close(Connection c) {
      if (c != null) {
         try {
            c.close();
         } catch (SQLException e) {
            LOGGER.error("error closing connection",e);
         }
      }
   }

   public static void close(PreparedStatement ps) {
      if (ps != null) {
         try {
            ps.close();
         } catch (SQLException e) {
            LOGGER.error("error closing statement",e);
         }
      }
   }

   public static void close(ResultSet rs) {
      if (rs != null) {
         try {
            rs.close();
         } catch (SQLException e) {
            LOGGER.error("error closing result set",e);
         }
      }
   }
}
