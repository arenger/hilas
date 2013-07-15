package edu.uccs.arenger.hilas;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uccs.arenger.hilas.dal.DalException;
import edu.uccs.arenger.hilas.dal.Domain;

public class DomainCounter implements Worker {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(DomainCounter.class);

   public long getDelay() {
      return 1;
   }

   public TimeUnit getTimeUnit() {
      return TimeUnit.MINUTES;
   }

   private void wrappedRun() {
      try {
         LOGGER.info("{}", Domain.getCount());
      } catch (DalException e) {
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
