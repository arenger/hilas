package edu.uccs.arenger.hilas.security;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import edu.uccs.arenger.hilas.Worker;
import edu.uccs.arenger.hilas.dal.DalException;
import edu.uccs.arenger.hilas.dal.Domain;
import edu.uccs.arenger.hilas.dal.SafeBrowseResult.Result;
import edu.uccs.arenger.hilas.dal.Sbs;

// see http://www.mywot.com/wiki/API
public class Wot implements Worker {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(Wot.class);

   // MAX_PER_REQ should not exceed 100, per WOT ToS -
   private static final int MAX_PER_REQ = 70; 

   private static final String API =
      "http://api.mywot.com/0.4/public_link_json2?hosts=%s&key=%s";

   private static final String API_KEY = "";

   private boolean paused = false;
   private int   runCount = 0;

   public long getDelay() {
      // see http://www.mywot.com/en/terms/api
      return 4;
   }

   public TimeUnit getTimeUnit() {
      return TimeUnit.SECONDS;
   }

   private String makeHostString(String[] domains) {
      if (domains == null)    { return ""; }
      if (domains.length < 1) { return ""; }
      StringBuilder sb = new StringBuilder();
      for (String d : domains) {
         sb.append(d);
         sb.append("/");
      }
      return sb.toString();
   }

   private void wrappedRun() {
      runCount++;
      if (paused && ((runCount % 5) != 0)) { return; }
      try {
         List<Domain> doms = Domain.getUnvetted(Sbs.WOT, MAX_PER_REQ);
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

         LOGGER.debug("submitting {} domains for vetting", doms.size());
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

   private class MinKeeper {
      private int min = Integer.MAX_VALUE;
      public void lower(JsonNode node) {
         int i = node.asInt(Integer.MAX_VALUE);
         if (i < min) {
            min = i;
         }
      }
      public Integer getMin() {
         //probably safe to assume that Integer.MAX_VALUE won't
         //actually be one f the minimum values...
         return ((min != Integer.MAX_VALUE) ? min : null);
      }
   }

   private Result determineResult(JsonNode node) {
      if (node == null) { return null; }
      MinKeeper mk = new MinKeeper();
      mk.lower(node.path("0").path(0));
      mk.lower(node.path("1").path(0));
      mk.lower(node.path("2").path(0));
      Integer min = mk.getMin();
      Result result = null;
      if (min != null) {
         if (min > 70) {
            result = Result.OK;
         } else if (min > 40) {
            result = Result.WARN;
         } else {
            result = Result.BAD;
         }
      }
      return result;
   }

   public static void main(String[] args) throws Exception {
      if (args.length < 1) {
         System.out.println("Wot domain [domain]*");
         System.exit(1);
      }
      Wot me = new Wot();
      HttpResponse resp = Request
         .Get(String.format(API, me.makeHostString(args), API_KEY))
         .execute().returnResponse();
      int code = resp.getStatusLine().getStatusCode();
      if (code == 200) {
         ObjectMapper mapper = new ObjectMapper();
         JsonNode root = mapper.readTree(resp.getEntity().getContent());
         for (String dom : args) {
            JsonNode sub = root.path(dom);
            Result r = me.determineResult(sub);
            if (r != null) {
               ((ObjectNode)sub).remove("target");
               ((ObjectNode)sub).remove("categories");
            }
            System.out.printf("%30s: %7s - %s\n", dom, r,
               (r != null) ? mapper.writeValueAsString(sub) : "noth'n");
         }
      } else {
         LOGGER.error("Response code: {}", code);
      }
   }

}
