package edu.uccs.arenger.hilas.quality;

import java.util.concurrent.Callable;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.debug.DebugFrame;
import org.mozilla.javascript.debug.DebuggableScript;
import org.mozilla.javascript.debug.Debugger;

/* Thanks to syam: http://stackoverflow.com/questions/10246030/
 * stopping-the-rhino-engine-in-middle-of-execution */

class RhinoStopper implements Debugger, Callable<Void> {
   
   private ObservingDebugFrame debugFrame = null;

   public Void call() {
      if (debugFrame != null) {
         debugFrame.interrupt();
      }
      return null;
   }

   public DebugFrame getFrame(Context cx, DebuggableScript fnOrScript) {
      if (debugFrame == null) {
         debugFrame = new ObservingDebugFrame();
      }
      return debugFrame;
   }

   public void handleCompilationDone(
      Context arg0, DebuggableScript arg1, String arg2) {}

   public static class ObservingDebugFrame implements DebugFrame {
      private boolean keepGoing = true;

      public void interrupt() {
         keepGoing = false;
      }

      public void onEnter(Context cx, Scriptable activation,
            Scriptable thisObj, Object[] args) {}

      public void onLineChange(Context cx, int lineNumber) {
         if (!keepGoing) {
            throw new JsInterruptedException();
         }
      }

      public void onExceptionThrown(Context cx, Throwable ex) {}

      public void onExit(Context cx, boolean byThrow,
         Object resultOrException) {}

      public void onDebuggerStatement(Context arg0) {}
   }
}
