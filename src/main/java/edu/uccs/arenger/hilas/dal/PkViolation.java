package edu.uccs.arenger.hilas.dal;

public class PkViolation extends DalException {
   private static final long serialVersionUID = 1L;

   public PkViolation(String msg) {
      super(msg);
   }

   public PkViolation(Throwable cause) {
      super(cause);
   }

   public PkViolation(String msg, Throwable cause) {
      super(msg, cause);
   }
}
