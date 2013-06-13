package edu.uccs.arenger.hilas.dal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JsLintMsg {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(JsLintMsg.class);

   private static ConcurrentMap<String,String> cache
      = new ConcurrentHashMap<String,String>(); //msg->id

   private static final String INS =
      "insert into jslintmsg (id, message) values (?, ?)";
   private static final String SEL =
      "select id from jslintmsg where message = ?";

   private JsLintMsg() {}

   public static String idForMsg(String message) throws DalException {
      String id = cache.get(message);
      if (id != null) {
         return id;
      }
      id = getFromDb(message);
      if (id != null) {
         cache.put(message,id);
         return id;
      }

      id = UUID.randomUUID().toString();
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(INS)) {
         ps.setString(1, id);
         ps.setString(2, message);
         ps.executeUpdate();
         cache.put(message,id);
         LOGGER.info("new jslint message: {}", message);
      } catch (SQLException e) { throw new DalException(e); }
      return id;
   }

   private static String getFromDb(String message) throws DalException {
      String id = null;
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(SEL)) {
         ps.setString(1, message);
         ResultSet rs = ps.executeQuery();
         if (rs.next()) {
            id = rs.getString(1);
         }
      } catch (SQLException e) { throw new DalException(e); }
      return id;
   }

}
