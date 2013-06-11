package edu.uccs.arenger.hilas.dal;

import java.sql.SQLException;

public class DalException extends Exception {
   private static final long serialVersionUID = 1L;
   private static final int  MYSQL_DUP_CODE = 1062;

   public DalException(String msg) {
      super(msg);
   }

   public DalException(Throwable cause) {
      super(cause);
   }

   public DalException(String msg, Throwable cause) {
      super(msg, cause);
   }

   //attemp to discern between unique and primary key violations -
   public static DalException of(SQLException e) {
      if (e.getErrorCode() == MYSQL_DUP_CODE) {
         if (e.getMessage().contains("PRIMARY")) {
            return new PkViolation(e.getMessage());
         } else {
            return new UkViolation(e.getMessage());
         }
      } else {
         return new DalException("Error code " + e.getErrorCode(), e);
      }
   }
}
