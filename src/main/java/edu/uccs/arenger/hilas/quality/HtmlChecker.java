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
import edu.uccs.arenger.hilas.Util.TypedContent;
import edu.uccs.arenger.hilas.Worker;
import edu.uccs.arenger.hilas.dal.DalException;
import edu.uccs.arenger.hilas.dal.LintMsg;
import edu.uccs.arenger.hilas.dal.LintMsg.Subject;
import edu.uccs.arenger.hilas.dal.Site;

public class HtmlChecker implements Worker {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(HtmlChecker.class);

   private boolean paused = false;
   private int   runCount = 0;

   public long getDelay() {
      /* To be polite, since this is a public web service:
       * - Put a reasonable, fixed delay between requests
       * - Call the WS with the HTML in the entity body, so the WS
       *   does not need to fetch the HTML
       * - GZip the entity body, so that less bandwith is used
       * - Since GZipping is more expensive than GZipping (I think),
       *   allow the requests to come across uncompressed.
       * - It happens that I need "out=gnu", which also looks to be
       *   the most concise uncompressed output format.
       */
      return 7;
   }

   public TimeUnit getTimeUnit() {
      return TimeUnit.SECONDS;
   }

   private byte[] gzip(String uncompressed) {
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

   private Set<LintMsg> uniqueMsgs(String gnuResponse) {
      Set<LintMsg> ret = new HashSet<LintMsg>();
      Pattern linePat = Pattern.compile(
         ": ((info|error|non-document-error)[^:]*):(.*)");
      String[] lines = gnuResponse.split("\n");
      for (String line : lines) {
         Matcher m = linePat.matcher(line);
         if (m.find()) {
            String msg = m.group(3).trim();
            msg = msg.replaceAll("\u201c.*?\u201d","{}");
            msg = msg.replaceAll("\\d","{}");
            ret.add(new LintMsg(Subject.HTML, m.group(1), msg));
         }
      }
      return ret;
   }

   private void wrappedRun() {
      runCount++;
      if (paused && ((runCount % 5) != 0)) { return; }
      try {
         Site site = Site.nextUnlinted();
         if (site == null) {
            if (!paused) {
               LOGGER.info("{} - PAUSING (no un-linted html)", this);
               paused = true;
            }
            return;
         }
         if (paused) {
            LOGGER.info("{} - RESUMING", this);
            paused = false;
         }

         LOGGER.debug("retrieving site {}", site.getId());
         TypedContent tc = null;
         try {
            tc = Util.getTypedContent(site.getUrl());
            if (tc.content.length() == 0) {
               throw new IOException("zero length html");
            }
         } catch (Exception e) {
            LOGGER.error("problem loading html. msg: {}", e.getMessage());
            site.setLintState(LintState.ERROR);
            site.update();
            return;
         }

         LOGGER.debug("submitting {} for html validation", site.getId());
         try {
            ByteArrayEntity entity = new ByteArrayEntity(gzip(tc.content));
            LOGGER.debug("using Content-Type: {}", tc.type);
            long start = System.currentTimeMillis();
            Set<LintMsg> uniq = uniqueMsgs(
               Request.Post("http://html5.validator.nu/?out=gnu")
                  .addHeader("Content-Type", tc.type)
                  .addHeader("Content-Encoding","gzip")
                  .body(entity).execute().returnContent().toString()
            );
            //this time will include network latency, but it's something -
            LOGGER.debug( "appx html analysis time: {} sec, size: {}",
               String.format("%.3f", (double)(System.currentTimeMillis() -
               start) / 1000), tc.content.length());

            //save results to db -
            Set<String> msgIds = new HashSet<String>();
            for (LintMsg msg : uniq) {
               msgIds.add(LintMsg.idFor(msg));
            }
            LintMsg.associate(Subject.HTML, site.getId(), msgIds);
            site.setLintState(LintState.PROCESSED);
            site.update();
         } catch (IOException e) {
            LOGGER.error("validation problem. msg: {}", e.getMessage());
            site.setLintState(LintState.ERROR);
            site.update();
         }
      } catch (DalException e) {
         LOGGER.error("dal problem", e);
      }
   }

   public void run() {
      try {
         wrappedRun();
      } catch (Exception e) {
         LOGGER.error("thread pool protection catch", e);
      }
   }

   // for testing -
   public static void main(String[] args) throws Exception {
      if (args.length != 1) {
         System.out.println("HtmlChecker url");
         System.exit(1);
      }
      HtmlChecker me = new HtmlChecker();
      TypedContent tc = Util.getTypedContent(new URL(args[0]));
      ByteArrayEntity entity = new ByteArrayEntity(me.gzip(tc.content));
      System.out.println("using Content-Type: " + tc.type);
      Set<LintMsg> uniq = me.uniqueMsgs(
         Request.Post("http://html5.validator.nu/?out=gnu")
            .addHeader("Content-Type", tc.type)
            .addHeader("Content-Encoding","gzip")
            .body(entity).execute().returnContent().toString()
      );
      for (LintMsg msg : uniq) {
         System.out.println(msg);
      }
   }

}
