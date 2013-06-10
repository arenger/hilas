package edu.uccs.arenger.hilas.dal;

import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class Util {
   private static final Logger LOGGER = LoggerFactory.getLogger(Util.class);

   public static final int MYSQL_DUP_CODE = 1062;

   private Util() {}

   public static void notNull(Object o, String name) throws DalException {
      if (o == null) {
         throw new DalException(name + " can't be null");
      }
   }

   // For use when we want to filter out (and only log warnings for)
   // violations to unique key constraints.  This method will still
   // throw an exception for PK violoations.
   public static void warnIfDup(SQLException e) throws DalException {
      if ((e.getErrorCode() == MYSQL_DUP_CODE) &&
          !e.getMessage().contains("PRIMARY")) {
         LOGGER.warn(e.getMessage());
      } else {
         throw new DalException("Error code " + e.getErrorCode(), e);
      }
   }
}
