package edu.uccs.arenger.hilas.quality;

import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uccs.arenger.hilas.Hilas;
import edu.uccs.arenger.hilas.Util;
import edu.uccs.arenger.hilas.Worker;
import edu.uccs.arenger.hilas.dal.DalException;
import edu.uccs.arenger.hilas.dal.Pool;
import edu.uccs.arenger.hilas.quality.CssChecker;

/* The CssValidator code appears to get into an infinite loop on occasion,
 * and/or hang for some unknown reason.  I tested the theory that it was
 * due to concurrency issues with the Rhino/JsHint process, by ensuring
 * that they "took turns" running in a single thread pool -- but that 
 * didn't solve the problem.  In the end, it appears that the CssValidator
 * code can be "trusted" to play nicely in the same JVM with the rest of
 * the Hilas code, so I'll try this ugly approach toward greater stability. */
final class CssvContainer {
   private final Logger logger;

   private Properties props;
   private ScheduledExecutorService runPool;

   private CssvContainer() {
      System.setProperty("logback.configurationFile", "logback.cssv.xml");
      logger = LoggerFactory.getLogger(CssvContainer.class);
   }

   private void shutdown() {
      logger.info("Shutting down");
      Pool.shutdown();
      if (runPool != null) {
         runPool.shutdownNow();
      }
   }

   private void startWorker(ScheduledExecutorService exec, Worker w) {
      exec.scheduleWithFixedDelay(w, 1, w.getDelay(), w.getTimeUnit());
   }

   private void run() {
      Runtime.getRuntime().addShutdownHook(new Thread() {
         public void run() { shutdown(); }
      });
      props = Hilas.getProps();
      try {
         Pool.init(props);
         Util.trustAllSslCerts();
         runPool = Executors.newSingleThreadScheduledExecutor();
         startWorker(runPool, new CssChecker());
      } catch (DalException e) {
         logger.error("problem", e);
      }
   }

   public static void main(String[] args) {
      new CssvContainer().run();
   }
}
