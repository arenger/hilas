package edu.uccs.arenger.hilas.quality;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ByteArrayEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uccs.arenger.hilas.Util;
import edu.uccs.arenger.hilas.Worker;

public class HtmlChecker implements Worker {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(CssChecker.class);

   public long getDelay() {
      /* To be polite, since this is a public web service:
       * - Put a fixed delay between requests
       * - Call the WS with the HTML in the entity body, so the WS
       *   does not need to fetch the HTML
       * - GZip the entity body, so that less bandwith is used
       * - Since GZipping is more expensive than GZipping (I think),
       *   allow the requests to come across uncompressed.
       * - It happens that I need "out=gnu", which looks to the most
       *   concise uncompressed output format.
       */
      return 5;
   }

   public TimeUnit getTimeUnit() {
      return TimeUnit.SECONDS;
   }

   public void run() {}

   private static byte[] gzip(String uncompressed) {
      if (uncompressed == null) { return null; }
      try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
           GZIPOutputStream      gzos = new GZIPOutputStream(baos)) {
         byte[] bytes = uncompressed.getBytes();
         gzos.write(bytes, 0, bytes.length);
         gzos.close();
         return baos.toByteArray();
      } catch(IOException e) {
         return null;
      }
   }

   // TODO Set<dal.HtmlMsg> or something, whereby we set the "score"
   // according to the message type
   private static Set<String> uniqueMsgs(String gnuResponse) {
      Set<String> set = new HashSet<String>();
      Pattern linePat = Pattern.compile(
         ": ((info|error|non-document-error)[^:]*):(.*)");
      String[] lines = gnuResponse.split("\n");
      for (String line : lines) {
         Matcher m = linePat.matcher(line);
         if (m.find()) {
            String msg = m.group(3).trim();
            msg = msg.replaceAll("\u201c.*?\u201d","?");
            msg = msg.replaceAll("\\d","?");
            set.add(String.format("%s: %s", m.group(1), msg));
         }
      }
      return set;
   }

   // for testing -
   public static void main(String[] args) throws Exception {
      if (args.length != 1) {
         System.out.println("HtmlChecker url");
         System.exit(1);
      }
      ByteArrayEntity entity = new ByteArrayEntity(
         gzip(Util.getContent(new URL(args[0]))));
      // TODO make a guess at the Content-Type, based on file extension,
      // if present, similar to html5check.py.  default to "text/html"
      Set<String> uniq = uniqueMsgs(
         Request.Post("http://html5.validator.nu/?out=gnu")
            .addHeader("Content-Type","text/html")
            .addHeader("Content-Encoding","gzip")
            .body(entity).execute().returnContent().toString()
      );
      for (String msg : uniq) {
         System.out.println(msg);
      }
   }

}
