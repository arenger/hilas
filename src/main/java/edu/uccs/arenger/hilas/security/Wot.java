package edu.uccs.arenger.hilas.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import edu.uccs.arenger.hilas.Hilas;
import edu.uccs.arenger.hilas.Worker;
import edu.uccs.arenger.hilas.dal.DalException;
import edu.uccs.arenger.hilas.dal.Domain;
import edu.uccs.arenger.hilas.dal.SafeBrowseResult;
import edu.uccs.arenger.hilas.dal.SafeBrowseResult.Result;
import edu.uccs.arenger.hilas.dal.Sbs;

// see http://www.mywot.com/wiki/API
public class Wot extends Worker {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(Wot.class);

   // MAX_PER_REQ should not exceed 100, per WOT ToS -
   private static final int MAX_PER_REQ = 70; 

   private static final String API =
      "http://api.mywot.com/0.4/public_link_json2?hosts=%s&key=%s";

   private static final String API_KEY = Hilas.getProp("wot.apiKey");

   public long getDelay() {
      // see http://www.mywot.com/en/terms/api
      return 5;
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

   private String makeHostString(List<Domain> domains) {
      if (domains == null)    { return ""; }
      if (domains.size() < 1) { return ""; }
      StringBuilder sb = new StringBuilder();
      for (Domain d : domains) {
         sb.append(d.getDomain());
         sb.append("/");
      }
      return sb.toString();
   }

   private List<SafeBrowseResult> parseResponse(
      List<Domain> doms, HttpResponse resp) {
      List<SafeBrowseResult> ret = new ArrayList<SafeBrowseResult>();
      try {
         ObjectMapper mapper = new ObjectMapper();
         JsonNode root = mapper.readTree(resp.getEntity().getContent());
         for (Domain dom : doms) {
            JsonNode sub = root.path(dom.getDomain());
            Result r = determineResult(sub);
            if (r != null) {
               ((ObjectNode)sub).remove("target");
               ((ObjectNode)sub).remove("categories");
            }
            SafeBrowseResult sbr = new SafeBrowseResult(dom.getId(), Sbs.WOT);
            if (r != null) {
               sbr.setResult(r);
               sbr.setExtra(mapper.writeValueAsString(sub));
            }
            ret.add(sbr);
         }
      } catch (IOException e) {
         LOGGER.error("{}: {}", e.getClass().getName(), e.getMessage());
      }
      return ret;
   }

   protected void wrappedRun() {
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
         String reqString = String.format(API, makeHostString(doms), API_KEY);
         HttpResponse resp = Request.Get(reqString)
            .socketTimeout(10000).execute().returnResponse();
         int code = resp.getStatusLine().getStatusCode();
         if (code == 200) {
            SafeBrowseResult.batchInsert(parseResponse(doms, resp));
         } else {
            LOGGER.error("Response code: {} from GET: {}", code, reqString);
            paused = true;
         }
      } catch (IOException e) {
         LOGGER.error("problem likely related to http request", e);
         paused = true;
      } catch (DalException e) {
         LOGGER.error("dal problem", e);
         paused = true;
      }
   }

   private class MinFinder {
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
      MinFinder mf = new MinFinder();
      mf.lower(node.path("0").path(0));
      mf.lower(node.path("1").path(0));
      mf.lower(node.path("2").path(0));
      // ignoring child safety rating ("4"), as it probably has only
      // an indirect relation to website security.
      Integer min = mf.getMin();
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
      LOGGER.error("using key: {}", API_KEY);
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
