package edu.uccs.arenger.hilas.quality;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.IOUtils;
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
import edu.uccs.arenger.hilas.dal.LintMsg;
import edu.uccs.arenger.hilas.dal.LintMsg.Subject;

public class JsHinter extends Worker implements AutoCloseable {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(JsHinter.class);
   private static final String RSRC_PATH = "quality";
   private static final long MAX_LINT_RUNTIME = 300; //seconds

   private ScheduledExecutorService timeoutService
      = Executors.newSingleThreadScheduledExecutor();

   private static String jshintSrc;
   private static String runSrc;

   public JsHinter() {
      /* There was some problem loading these in a static block,
       * so we'll test for null in the constructor... */
      if ((jshintSrc == null) || (runSrc == null)) {
         GZIPInputStream in = null;
         try {
            InputStream stream = ClassLoader.getSystemResourceAsStream(
               RSRC_PATH + "/jshint.js.gz");
            if (stream == null) {
               throw new RuntimeException("blah!");
            }
            in = new GZIPInputStream(stream);
            jshintSrc = IOUtils.toString(in);
            runSrc = IOUtils.toString(ClassLoader.getSystemResourceAsStream(
               RSRC_PATH + "/run_jshint.js"));
         } catch (IOException e) {
            LOGGER.error("error loading resource: {}", e.getMessage());
         } finally {
            if (in != null) {
               try { in.close(); } catch (IOException e) {}
            }
         }
      }
   }

   public long getDelay() {
      return 1;
   }

   public TimeUnit getTimeUnit() {
      return TimeUnit.SECONDS;
   }

   private Set<LintMsg> jshint(String src) throws IOException {
      Set<LintMsg> ret  = new HashSet<LintMsg>();
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
         cx.evaluateString(scope, jshintSrc, "jshint.js"    , 1, null);
         cx.evaluateString(scope, runSrc   , "run_jshint.js", 1, null);
         future.cancel(true);
      } finally {
         Context.exit();
      }
      LOGGER.debug( "jshint analysis time: {} sec, js size: {}",
         String.format("%.3f", (double)(System.currentTimeMillis() -
         start) / 1000), src.length());
      for (String msg : raws) {
         //just for consistent (logback-like) parameterization format -
         msg = msg.replaceAll("\\{\\w\\}","{}");
         ret.add(new LintMsg(Subject.JS, "general", msg));
      }
      return ret;
   }

   protected void wrappedRun() {
      try {
         JavaScript js = JavaScript.nextUnhinted();
         if (js == null) {
            if (!paused) {
               LOGGER.info("{} - PAUSING (no un-hinted js entries)", this);
               paused = true;
            }
            return;
         }
         if (paused) {
            LOGGER.info("{} - RESUMING", this);
            paused = false;
         }
         LOGGER.debug("retrieving js {}", js.getId());
         String src = null;
         try {
            src = Util.getTypedContent(js.getUrl()).content;
            if (src.length() == 0) {
               throw new IOException("zero length js");
            }
         } catch (Exception e) {
            LOGGER.error("problem loading js src. msg: {}", e.getMessage());
            js.setHintState(LintState.ERROR);
            js.update();
            return;
         }
         
         if (!Util.md5(src).equals(js.getId())) {
            // not considering this an error, b/c i'm sure there are urls
            // that yield dynamic js.  but it may be worth noting -
            LOGGER.warn("md5 mismatch for js {}", js.getId());
         }

         try {
            LOGGER.info("starting lint for js {}", js.getId());
            Set<LintMsg>  msgs = jshint(src);
            Set<String> msgIds = new HashSet<String>();
            for (LintMsg msg : msgs) {
               msgIds.add(LintMsg.idFor(msg));
            }
            LintMsg.associate(Subject.JS, js.getId(), msgIds);
            js.setHintState(LintState.PROCESSED);
         } catch (JavaScriptException|JsInterruptedException|IOException e) {
            LOGGER.warn("{} for js id {} - {}",
               e.getClass().getName(), js.getId(), e.getMessage());
            js.setHintState(LintState.ERROR);
         }
         js.update();
      } catch (DalException e) {
         LOGGER.error("dal problem", e);
      }
   }

   public void close() {
      timeoutService.shutdownNow();
   }

   public static void main(String[] args) throws Exception {
      if (args.length != 1) {
         System.out.println("js url required as 1st and only arg");
         System.exit(1);
      }
      String src = Util.getTypedContent(new URL(args[0])).content;
      try (JsHinter me = new JsHinter()) {
         Set<LintMsg> msgs = me.jshint(src);
         for (LintMsg msg : msgs) {
            System.out.println(msg);
         }
      }
   }
}
