package edu.uccs.arenger.hilas.security;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uccs.arenger.hilas.Worker;
import edu.uccs.arenger.hilas.dal.DalException;
import edu.uccs.arenger.hilas.dal.Domain;
import edu.uccs.arenger.hilas.dal.SafeBrowseResult;
import edu.uccs.arenger.hilas.dal.SafeBrowseResult.Result;
import edu.uccs.arenger.hilas.dal.Sbs;

public class NortonSw extends Worker {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(NortonSw.class);

   private static final String R_OK      = "g";
   private static final String R_WARN    = "w";
   private static final String R_BAD     = "b";
   private static final String R_SECURE  = "r";
   private static final String R_UNKNOWN = "u";

   private static final String API =
      "http://ratings-wrs.symantec.com/rating?url=%s";

   private static final Pattern PAT_SITE
      = Pattern.compile("<site\\s(.+?)\\/>", Pattern.MULTILINE);
   private static final Pattern PAT_RATE = Pattern.compile("r=\"(\\w)\"");

   public long getDelay() {
      return 5; //no published ToS.  We'll assume this is cool.
   }

   public TimeUnit getTimeUnit() {
      return TimeUnit.SECONDS;
   }

   private Result parseResponse(HttpResponse resp) {
      Result ret = Result.ERROR;
      try {
         String content
            = IOUtils.toString(resp.getEntity().getContent()).trim();
         Matcher m = PAT_SITE.matcher(content);
         if (m.find()) {
            m = PAT_RATE.matcher(m.group(1));
            if (m.find()) {
               String r = m.group(1);
               if (r.equals(R_OK)) {
                  ret = Result.OK;
               } else if (r.equals(R_SECURE)) {
                  ret = Result.OK;
               } else if (r.equals(R_WARN)) {
                  ret = Result.WARN;
               } else if (r.equals(R_BAD)) {
                  ret = Result.BAD;
               } else if (r.equals(R_UNKNOWN)) {
                  ret = null;
               } else {
                  LOGGER.error("unexpected vet code: {}", r);
               }
            } else {
               LOGGER.error("PAT_RATE no match: {}", content);
            }
         } else {
            LOGGER.error("PAT_SITE no match: {}", content);
         }
      } catch (IOException e) {
         LOGGER.error("error while parsing response: {}", e.getMessage());
      }
      return ret;
   }

   protected void wrappedRun() {
      try {
         List<Domain> doms = Domain.getUnvetted(Sbs.NORTON, 1);
         if (doms.size() == 0) {
            if (!paused) {
               LOGGER.info("{} - PAUSING (no doms to vet)", this);
               paused = true;
            }
            return;
         }
         if (paused) {
            LOGGER.info("{} - RESUMING", this);
            paused = false;
         }
         LOGGER.debug("submitting 1 domain for vetting");
         HttpResponse resp = Request
            .Get(String.format(API, doms.get(0).getDomain()))
            .execute().returnResponse();
         int code = resp.getStatusLine().getStatusCode();
         if (code == 200) {
            SafeBrowseResult r = new SafeBrowseResult(
               doms.get(0).getId(), Sbs.NORTON);
            r.setResult(parseResponse(resp));
            r.insert();
         } else {
            LOGGER.error("Response code: {}", code);
         }
      } catch (IOException e) {
         LOGGER.error("problem likely related to http request", e);
         paused = true;
      } catch (DalException e) {
         LOGGER.error("dal problem", e);
         paused = true;
      }
   }

   public static void main(String[] args) throws Exception {
      if (args.length < 1) {
         System.out.println("NortonSw domain");
         System.exit(1);
      }
      NortonSw me = new NortonSw();
      HttpResponse resp = Request
         .Get(String.format(API, args[0])).socketTimeout(10000)
         .execute().returnResponse();
      int code = resp.getStatusLine().getStatusCode();
      if (code == 200) {
         System.out.println(me.parseResponse(resp));
      } else {
         LOGGER.error("Response code: {}", code);
      }
   }

}
