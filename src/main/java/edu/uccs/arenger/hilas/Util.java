package edu.uccs.arenger.hilas;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uccs.arenger.hilas.dal.DalException;

public final class Util {
   private static final Logger LOGGER = LoggerFactory.getLogger(Util.class);
   private static final String DEFAULT_CHARSET = "ISO-8859-1";
   private static final Pattern CHARSET_PAT
      = Pattern.compile("text/html;\\s+charset=([^\\s]+)\\s*");
   private static final int SOCKET_TIMEOUT = 30000; //millis

   private Util() {}

   public static TypedContent getTypedContent(URL url) throws IOException {
      TypedContent ret = new TypedContent();
      HttpResponse resp = Request
         .Get(url.toString()).socketTimeout(3000)
         .execute().returnResponse();
      int code = resp.getStatusLine().getStatusCode();
      if (code == 200) {
         HttpEntity entity = resp.getEntity();
         if (entity.getContentType() != null) {
            ret.type = entity.getContentType().getValue();
         }
         String charset = DEFAULT_CHARSET;
         Matcher m = CHARSET_PAT.matcher(ret.type);
         if (m.matches()) { charset = m.group(1); }
         ret.content = IOUtils.toString(entity.getContent(), charset).trim();
      } else {
         throw new IOException(String.format("Response code %d or %s",
            code, url.toString()));
      }
      if (ret.type == null) { ret.type = "unknown"; }
      return ret;
   }

   public static class TypedContent {
      public String content;
      public String type;
   }

   public static void trustAllSslCerts() {
      TrustManager[] trustAllCerts = new TrustManager[]{
         new X509TrustManager(){
            public X509Certificate[] getAcceptedIssuers(){return null;}
            public void checkClientTrusted(
               X509Certificate[] certs, String authType){}
            public void checkServerTrusted(
               X509Certificate[] certs, String authType){}
      }};

      try {
         SSLContext sc = SSLContext.getInstance("TLS");
         sc.init(null, trustAllCerts, new SecureRandom());
         HttpsURLConnection.setDefaultSSLSocketFactory(
            sc.getSocketFactory());
      } catch (Exception e) {
         LOGGER.error("problem installing trust manager", e);
      }
   }

   public static boolean protocolOk(URL url) {
      String prot = url.getProtocol(); //always returns lowercase
      return prot.equals("http") || prot.equals("https");
   }

   public static String md5(String content) {
      String hash = "error";
      try {
         MessageDigest md = MessageDigest.getInstance("MD5");
         BigInteger bi = new BigInteger(1,md.digest(content.getBytes()));
         hash = bi.toString(16);
      } catch (NoSuchAlgorithmException e) {
         LOGGER.error("MD5 DNE.  That's odd.");
      }
      return hash;
   }

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

   public static void main(String[] args) throws Exception {
      if (args.length != 1) {
         System.out.println("see usage");
         System.exit(1);
      }
      TypedContent tc = Util.getTypedContent(new URL(args[0]));
      System.out.println(tc.type);
      System.out.println(tc.content);
   }

}
