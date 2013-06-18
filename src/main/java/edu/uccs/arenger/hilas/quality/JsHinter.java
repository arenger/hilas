package edu.uccs.arenger.hilas.quality;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uccs.arenger.hilas.Util;
import edu.uccs.arenger.hilas.Worker;
import edu.uccs.arenger.hilas.dal.DalException;
import edu.uccs.arenger.hilas.dal.JavaScript;

public class JsHinter implements Worker, AutoCloseable {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(JsHinter.class);
   private static final String RSRC_PATH = "quality/";
   private static final long MAX_LINT_RUNTIME = 3; //seconds

   private ScheduledFuture<?> scheduledFuture;

   private ScheduledExecutorService timeoutService
      = Executors.newSingleThreadScheduledExecutor();

   public long getDelay() {
      return 1;
   }

   public TimeUnit getTimeUnit() {
      return TimeUnit.SECONDS;
   }

   public void setScheduledFuture(ScheduledFuture<?> scheduledFuture) {
      this.scheduledFuture = scheduledFuture;
   }

   private void stopWorking() {
      if (scheduledFuture != null) {
         LOGGER.info("stopping: {}", this);
         scheduledFuture.cancel(false);
      } else {
         LOGGER.info("scheduledFuture not set for {}", this);
      }
   }

   private void runJs(Context cx, Scriptable scope,
      String name, String path) throws IOException {
      URL script = ClassLoader.getSystemResource(path);
      if (script == null) {
         throw new IOException("resource not found");
      }
      try (InputStreamReader in =
           new InputStreamReader(script.openStream())) {
         cx.evaluateReader(scope, in, name, 1, null);
      }
   }

   private List<String> jshint(String src) throws IOException {
      List<String> raws = new ArrayList<String>();
      long start = System.currentTimeMillis();
      try {
         Context cx = Context.enter();
         RhinoStopper stopper = new RhinoStopper();
         cx.setDebugger(stopper, null);
         cx.setGeneratingDebug(true);
         cx.setOptimizationLevel(-1);
         ScheduledFuture<Void> future = timeoutService.schedule(
            stopper, MAX_LINT_RUNTIME, TimeUnit.SECONDS);
         Scriptable scope = cx.initStandardObjects();
         ScriptableObject.putProperty(scope, "raws" , raws);
         ScriptableObject.putProperty(scope, "input", src);
         runJs(cx, scope, "jshint-load", RSRC_PATH + "jshint-2.1.3.js");
         runJs(cx, scope, "jshint-run" , RSRC_PATH + "run_jshint.js");
         future.cancel(true);
      } finally {
         Context.exit();
      }
      LOGGER.debug( "jshint analysis time: {} sec, js size: {}",
         String.format("%.3f", (double)(System.currentTimeMillis() -
         start) / 1000), src.length());
      return raws;
   }

   private void wrappedRun() {
      try {
         JavaScript js = JavaScript.nextUnhinted();
         if (js == null) {
            LOGGER.info("no un-hinted js entries");
            //stopWorking();
            return;
         }
         LOGGER.info("starting lint for js {}", js.getId());
         String src = null;
         try {
            src = Util.getContent(js.getUrl());
            if (src.length() == 0) {
               throw new IOException("zero length js");
            }
         } catch (Exception e) {
            LOGGER.error("problem loading js src. msg: {}", e.getMessage());
            js.setHintState(JavaScript.State.ERROR);
            js.update();
            return;
         }
         
         if (!Util.md5(src).equals(js.getId())) {
            // not considering this an error, b/c i'm sure there are urls
            // that yield dynamic js.  but it may be worth noting -
            LOGGER.warn("md5 mismatch for js {}", js.getId());
         }

         Set<String> msgSet = new HashSet<String>();
         try {
            msgSet.addAll(jshint(src));
            JavaScript.linkHintMessages(js.getId(), msgSet);
            js.setHintState(JavaScript.State.PROCESSED);
         } catch (JavaScriptException|JsInterruptedException|IOException e){
            LOGGER.warn("{} for js id {} - {}",
               e.getClass().getName(), js.getId(), e.getMessage());
            js.setHintState(JavaScript.State.ERROR);
         }
         js.update();
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

   public void close() {
      timeoutService.shutdownNow();
   }

   public static void main(String[] args) throws Exception {
      if (args.length != 1) {
         System.out.println("js file required as 1st and only arg");
         System.exit(1);
      }
      StringBuilder sb = new StringBuilder();
      try (BufferedReader in = new BufferedReader(new FileReader(args[0]))) {
         String line;
         while ((line = in.readLine()) != null) {
            sb.append(line);
            sb.append("\n");
         }
      }
      try (JsHinter me = new JsHinter()) {
         List<String> msgs = me.jshint(sb.toString());
         for (String msg : msgs) {
            System.out.println(msg);
         }
      }
   }
}
