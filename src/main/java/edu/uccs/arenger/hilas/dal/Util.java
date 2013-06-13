package edu.uccs.arenger.hilas.dal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class Util {
   private static final Logger LOGGER = LoggerFactory.getLogger(Util.class);

   private Util() {}

   public static void notNull(Object o, String name) throws DalException {
      if (o == null) {
         throw new DalException(name + " can't be null");
      }
   }

   public static void close(PreparedStatement ps) {
      if (ps != null) {
         try {
            ps.close();
         } catch (SQLException e) {
            LOGGER.error("problem while closing", e);
         }
      }
   }

   public static void close(Connection c) {
      if (c != null) {
         try {
            c.close();
         } catch (SQLException e) {
            LOGGER.error("problem while closing", e);
         }
      }
   }

   public static void setAutoCommit(Connection c, boolean active) {
      if (c != null) {
         try {
            c.setAutoCommit(active);
         } catch (SQLException e) {
            LOGGER.error("problem setting autocommit", e);
         }
      }
   }
}
