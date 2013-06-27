package edu.uccs.arenger.hilas;

import java.util.concurrent.TimeUnit;

public interface Worker extends Runnable {
   long getDelay();
   TimeUnit getTimeUnit();
}
