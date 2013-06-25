package edu.uccs.arenger.hilas.dal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CssValidMsg {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(CssValidMsg.class);

   private String id; //will be null until this obj is saved
   private String message;
   private Type   type;
   //private int    severity; -- not supported

   private static final String SEL =
      "select id from cssvalidmsg where message = ? and type = ?";
   private static final String INS =
      "insert into cssvalidmsg (id, message, type, severity) " +
      "values (?, ?, ?, ?)";

   public enum Type {WARN, ERROR};

   public CssValidMsg(Type type, String message) {
      id = null;
      this.type = type;
      this.message = message;
   }

   public void save() throws DalException {
      id = lookup();
      if (id == null) {
         id = UUID.randomUUID().toString();
         try (Connection conn = Pool.getConnection();
              PreparedStatement ps = conn.prepareStatement(INS)) {
            ps.setString(1, id);
            ps.setString(2, message);
            ps.setString(3, type.toString());
            ps.setInt(4, initSeverity(type));
            ps.executeUpdate();
            LOGGER.info("new cssvalidmsg message: {}", message);
         } catch (SQLException e) { throw new DalException(e); }
      }
   }

   /* severity can be adjusted later (by hand when analyzing), but
    * it will be initialized according to message type.  this class
    * does not currently support changing or even viewing severity. */
   private int initSeverity(Type type) {
      if ( type == Type.WARN ) {
         return 1;
      } else {
         return 2;
      }
   }

   private String lookup() throws DalException {
      String id = null;
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(SEL)) {
         ps.setString(1, message);
         ps.setString(2, type.toString());
         ResultSet rs = ps.executeQuery();
         if (rs.next()) {
            id = rs.getString("id");
         }
      } catch (SQLException e) { throw new DalException(e); }
      return id;
   }

   public String toString() {
      return message;
   }

   //returns null if this obj has not yet been saved to the db -
   public String getId() {
      return id;
   }

   private String uniqueKey() {
      return String.format("%s: %s", type, message);
   }

   public boolean equals(Object o) {
      if ((o == null) || !(o instanceof CssValidMsg)) {
         return false;
      } else {
         return ((CssValidMsg)o).uniqueKey().equals(uniqueKey());
      }
   }

   public int hashCode() {
      return uniqueKey().hashCode();
   }

}
