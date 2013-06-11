package edu.uccs.arenger.hilas.dal;

public class UkViolation extends DalException {
   private static final long serialVersionUID = 1L;

   public UkViolation(String msg) {
      super(msg);
   }

   public UkViolation(Throwable cause) {
      super(cause);
   }

   public UkViolation(String msg, Throwable cause) {
      super(msg, cause);
   }
}
