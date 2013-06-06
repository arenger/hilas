package edu.uccs.arenger.hilas.dal;

import java.sql.Connection;
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

   public static void close(Connection c) {
      if (c != null) {
         try {
            c.close();
         } catch (SQLException e) {
            LOGGER.error("error closing connection",e);
         }
      }
   }
}
