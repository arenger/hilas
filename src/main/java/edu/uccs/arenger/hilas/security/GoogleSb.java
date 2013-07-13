package edu.uccs.arenger.hilas.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uccs.arenger.hilas.Hilas;
import edu.uccs.arenger.hilas.Worker;
import edu.uccs.arenger.hilas.dal.DalException;
import edu.uccs.arenger.hilas.dal.SafeBrowseResult;
import edu.uccs.arenger.hilas.dal.SafeBrowseResult.Result;
import edu.uccs.arenger.hilas.dal.Sbs;
import edu.uccs.arenger.hilas.dal.Site;

// see https://developers.google.com/safe-browsing/lookup_guide
public class GoogleSb implements Worker {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(GoogleSb.class);

   // MAX_PER_REQ should not exceed 500, per GBS ToS -
   private static final int MAX_PER_REQ = 490; 

   private static final String API =
      "https://sb-ssl.google.com/safebrowsing/api/lookup?" +
      "client=hilas&apikey=%s&appver=1.0&pver=3.0";

   private static final String API_KEY = Hilas.getProp("gsb.apiKey");

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

   private List<SafeBrowseResult> parseResponse(List<Site> sites,
      HttpResponse resp) throws IOException {
      List<SafeBrowseResult> ret = null;
      int code = resp.getStatusLine().getStatusCode();
      if (code == 204) {
         ret = new ArrayList<SafeBrowseResult>();
         for (Site s : sites) {
            SafeBrowseResult r = new SafeBrowseResult(
               s.getDomainId(), Sbs.GOOGLE);
            r.setResult(Result.OK);
            ret.add(r);
         }
      } else if (code == 200) {
         ret = new ArrayList<SafeBrowseResult>();
         String content = IOUtils.toString(resp.getEntity().getContent());
         String[] lines = content.split("\n");
         if (lines.length != sites.size()) {
            LOGGER.error("Response length mismatch ({} != {})",
               lines.length, sites.size());
         } else {
            for (int i = 0; i < lines.length; i++) {
               SafeBrowseResult r = new SafeBrowseResult(
                  sites.get(i).getDomainId(), Sbs.GOOGLE);
               if (lines[i].equalsIgnoreCase("ok")) {
                  r.setResult(Result.OK);
               } else {
                  r.setResult(Result.BAD);
                  r.setExtra(lines[i]);
               }
               ret.add(r);
            }
         }
      } else {
         LOGGER.error("Response code: {}", code);
         paused = true;
      }
      return ret;
   }

   private void wrappedRun() {
      runCount++;
      if (paused && ((runCount % 5) != 0)) { return; }
      try {
         List<Site> sites = Site.getUnvetted(Sbs.GOOGLE, MAX_PER_REQ);
         if (sites.size() == 0) {
            if (!paused) {
               LOGGER.info("{} - PAUSING (no sites to vet)", this);
               paused = true;
            }
            return;
         }
         if (paused) {
            LOGGER.info("{} - RESUMING", this);
            paused = false;
         }

         LOGGER.debug("submitting {} urls for vetting", sites.size());
         StringBuilder sb = new StringBuilder(String.valueOf(sites.size()));
         for (Site site : sites) {
            sb.append("\n");
            sb.append(site.getUrl());
         }
         try {
            HttpResponse resp = Request.Post(String.format(API, API_KEY))
               .body(new StringEntity(sb.toString()))
               .execute().returnResponse();
            List<SafeBrowseResult> batch = parseResponse(sites, resp);
            if (batch != null) {
               SafeBrowseResult.batchInsert(batch);
            }
         } catch (IOException e) {
            LOGGER.error("IOException: ", e.getMessage());
            paused = true;
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

}
