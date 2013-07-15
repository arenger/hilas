package edu.uccs.arenger.hilas;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Worker implements Runnable {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(Worker.class);

   public abstract long getDelay();
   public abstract TimeUnit getTimeUnit();

   protected boolean paused = false;
   protected int   runCount = 0;

   protected abstract void wrappedRun();

   public final void run() {
      runCount++;
      if (paused && ((runCount % 5) != 0)) { return; }
      try {
         wrappedRun();
      } catch (Exception e) {
         LOGGER.error("thread pool protection catch", e);
      }
   }
}
