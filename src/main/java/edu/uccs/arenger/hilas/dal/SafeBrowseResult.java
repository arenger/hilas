package edu.uccs.arenger.hilas.dal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

public final class SafeBrowseResult {
   private String  domainId;
   private Sbs     sbs;
   private Integer result;
   private String  extra;

   private static final String INS =
      "insert into SafeBrowseResult (sbsId, domainId, result, extra) " +
      "values (?, ?, ?, ?)";

   public SafeBrowseResult(String domainId, Sbs sbs) {
      this.domainId = domainId;
      this.sbs = sbs;
   }

   public void setResult(Integer result) {
      this.result = result;
   }

   public void setExtra(String extra) {
      this.extra = extra;
   }

   public void insert() throws DalException {
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(INS)) {
         ps.setInt(1, sbs.getId());
         ps.setString(2, domainId);
         if (result != null) {
            ps.setInt(3, result);
         } else { 
            ps.setNull(3, Types.INTEGER);
         }
         if (extra != null) {
            ps.setString(3, extra);
         } else { 
            ps.setNull(3, Types.VARCHAR);
         }
      } catch (SQLException e) {
         throw new DalException(e);
      }
   }

   public static void batchInsert(List<SafeBrowseResult> batch) 
      throws DalException {
      // TODO
   }
}
