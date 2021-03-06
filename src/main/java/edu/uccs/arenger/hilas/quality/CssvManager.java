package edu.uccs.arenger.hilas.quality;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uccs.arenger.hilas.Worker;
import edu.uccs.arenger.hilas.dal.Css;
import edu.uccs.arenger.hilas.dal.DalException;

public class CssvManager extends Worker {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(CssvManager.class);

   private static final long MAX_LINT_RUNTIME = 300; //seconds

   private ExecutorService validationService
      = Executors.newSingleThreadExecutor();

   public long getDelay() {
      return 1;
   }

   public TimeUnit getTimeUnit() {
      return TimeUnit.SECONDS;
   }

   protected void wrappedRun() {
      try {
         Css css = Css.nextUnlinted();
         if (css == null) {
            if (!paused) {
               LOGGER.info("{} - PAUSING (no un-linted css entries)", this);
               paused = true;
            }
            return;
         }
         if (paused) {
            LOGGER.info("{} - RESUMING", this);
            paused = false;
         }
         CssChecker checker = new CssChecker(css);
         Future<?> future = validationService.submit(checker);
         try {
            future.get(MAX_LINT_RUNTIME, TimeUnit.SECONDS);
         } catch (CancellationException | ExecutionException |
                  InterruptedException  | TimeoutException e ) {
            LOGGER.warn("problem with submission to validationService: {}",
               e.getClass().getName());
         }
         if (!future.isDone()) {
            LOGGER.warn("attemping to cancel CssChecker");
            future.cancel(true);
         }
      } catch (DalException e) {
         LOGGER.error("dal problem", e);
      }
   }

}
