package edu.uccs.arenger.hilas.quality;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uccs.arenger.hilas.Worker;
import edu.uccs.arenger.hilas.dal.DalException;

public class CssvManager implements Worker {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(CssvManager.class);

   public long getDelay() {
      return 10;
   }

   public TimeUnit getTimeUnit() {
      return TimeUnit.SECONDS;
   }

   private void wrappedRun() {
      try {
         LOGGER.debug("cp: {}",
            System.getProperty("java.class.path","not set"));
      } catch (Exception e) {
      //} catch (DalException e) {
         LOGGER.error("dal problem", e);
      }
   }

   public void run() {
      try {
         wrappedRun();
      } catch (Exception e) {
         LOGGER.error("thread pool protection catch",e);
      }
   }

}
