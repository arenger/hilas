package edu.uccs.arenger.hilas.security;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uccs.arenger.hilas.Worker;
import edu.uccs.arenger.hilas.dal.DalException;
import edu.uccs.arenger.hilas.dal.Sbs;
import edu.uccs.arenger.hilas.dal.Site;

// see https://developers.google.com/safe-browsing/lookup_guide
public class GoogleSb implements Worker {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(GoogleSb.class);

   // MAX_PER_REQ should not exceed 500, per GBS ToS -
   //private static final int MAX_PER_REQ = 300; 
   private static final int MAX_PER_REQ = 2; // for testing

   private static final String API =
      "https://sb-ssl.google.com/safebrowsing/api/lookup?" +
      "client=hilas&apikey=%s&appver=1.0&pver=3.0";

   private static final String API_KEY =
      "ABQIAAAAiZbM519ge5FvFY6mSe-wKRSuWng5CV9QQZARtMvgwl8mjUX7SA";

   private boolean paused = false;
   private int   runCount = 0;

   public long getDelay() {
      // see https://developers.google.com/
      // safe-browsing/lookup_guide#AcceptableUsage - 
      // no more than 10,000 requests per day...
      // (60*60*24)/9 = 9600
      return 9;
   }

   public TimeUnit getTimeUnit() {
      return TimeUnit.SECONDS;
   }

   private void wrappedRun() {
      runCount++;
      if (paused && ((runCount % 5) != 0)) { return; }
      try {
         List<String> urls = Site.getUnvettedUrls(Sbs.GOOGLE, MAX_PER_REQ);
         if (urls.size() == 0) {
            if (!paused) {
               LOGGER.info("{} - PAUSING (no urls to vet)", this);
               paused = true;
            }
            return;
         }
         if (paused) {
            LOGGER.info("{} - RESUMING", this);
            paused = false;
         }

         LOGGER.debug("submitting {} urls for vetting", urls.size());
         StringBuilder sb = new StringBuilder(String.valueOf(urls.size()));
         for (String url : urls) {
            sb.append("\n");
            sb.append(url);
         }
         try {
            Response resp = Request.Post(String.format(API, API_KEY))
               .body(new StringEntity(sb.toString())).execute();
            String toParse = resp.returnContent().toString();
            int code = resp.returnResponse()
               .getStatusLine().getStatusCode();
            //TODO react to code...

            //TODO save results to db -
         } catch (IOException e) {
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
      /*
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
      */
   }

}
