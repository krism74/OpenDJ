/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.scratch;



import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;



/**
 *
 */
public final class Timer {

  /**
   * Operation class.
   */
  private static final class Operation {

    // Operation id.
    private final long id;

    // Indicates whether this operation is done.
    private boolean isDone = false;

    // The future used to cancel this operation if available.
    private ScheduledFuture<?> cancelFuture = null;



    /**
     * Create a new operation.
     *
     * @param id
     *          operation id.
     */
    public Operation(long id) {
      this.id = id;
    }



    /**
     * Cancels this operation.
     */
    public final void cancel() {
      if (cancelFuture != null) {
        System.out.println("Request cancel: " + this + " [already cancelled]");
        return;
      }

      if (isDone) {
        System.out.println("Request cancel: " + this + " [already done]");
        return;
      }

      // Schedule cancel request.
      System.out.println("Request cancel: " + this + " [scheduled]");
      Runnable r = new Runnable() {

        public void run() {
          if (isDone()) {
            System.out.println("Force cancel: " + Operation.this
                + " [already done]");
          } else {
            System.out.println("Force cancel: " + Operation.this + " [forced]");
            setDone(true);
          }
        }

      };

      cancelFuture = Timer.schedule(r, 5, TimeUnit.SECONDS);
    }



    /**
     * Gets the isDone.
     *
     * @return Returns the isDone.
     */
    public final boolean isDone() {
      return isDone;
    }



    /**
     * Sets the isDone.
     *
     * @param isDone
     *          The isDone.
     */
    public final void setDone(boolean isDone) {
      this.isDone = isDone;

      if (cancelFuture != null) {
        cancelFuture.cancel(false);
      }
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public final String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("Operation[");
      builder.append(id);
      builder.append(':');
      builder.append(isDone);
      builder.append(']');
      return builder.toString();
    }

  }



  /**
   * Timer job.
   */
  private static final class TimeInfo implements Runnable {

    // The calendar holding the current time.
    private GregorianCalendar calendar;

    // The date for this time thread.
    private Date date;

    // The timestamp for this time thread in the generalized time
    // format.
    private String generalizedTime;

    // The date formatter that will be used to obtain the generalized
    // time.
    private final SimpleDateFormat generalizedTimeFormatter;

    // The timestamp for this time thread in GMT.
    private String gmtTimestamp;

    // The date formatter that will be used to obtain the GMT
    // timestamp.
    private final SimpleDateFormat gmtTimestampFormatter;

    // The current time in HHmm form as an integer.
    private int hourAndMinute;

    // The timestamp for this time thread in the local time zone.
    private String localTimestamp;

    // The date formatter that will be used to obtain the local
    // timestamp.
    private final SimpleDateFormat localTimestampFormatter;

    // The current time in nanoseconds.
    private long nanoTime;

    // The current time in milliseconds since the epoch.
    private long time;



    /**
     * Create a new job with the specified delay.
     */
    public TimeInfo() {
      TimeZone utcTimeZone = TimeZone.getTimeZone("UTC");

      generalizedTimeFormatter = new SimpleDateFormat("yyyyMMddHHmmss.SSS'Z'");
      generalizedTimeFormatter.setTimeZone(utcTimeZone);

      gmtTimestampFormatter = new SimpleDateFormat("yyyyMMddHHmmss'Z'");
      gmtTimestampFormatter.setTimeZone(utcTimeZone);

      localTimestampFormatter = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z");

      // Populate initial values.
      run();
    }



    /**
     * {@inheritDoc}
     */
    public void run() {
      calendar = new GregorianCalendar();
      date = calendar.getTime();
      time = date.getTime();
      nanoTime = System.nanoTime();
      generalizedTime = generalizedTimeFormatter.format(date);
      localTimestamp = localTimestampFormatter.format(date);
      gmtTimestamp = gmtTimestampFormatter.format(date);
      hourAndMinute = (calendar.get(Calendar.HOUR_OF_DAY) * 100)
          + calendar.get(Calendar.MINUTE);

      System.out.println("Updated time: " + time);
    }
  }



  /**
   * Thread factory used by the scheduled execution service.
   */
  private static final class TimerThreadFactory implements ThreadFactory {

    /**
     * {@inheritDoc}
     */
    public Thread newThread(Runnable r) {
      Thread t = new Thread(r, "Time Thread");
      t.setDaemon(true);
      return t;
    }

  }

  // Singleton instance.
  private static final Timer INSTANCE = new Timer();



  /**
   * Retrieves a <CODE>Calendar</CODE> containing the time at the
   * last update.
   *
   * @return A <CODE>Calendar</CODE> containing the time at the last
   *         update.
   */
  public static Calendar getCalendar() {
    return INSTANCE.timeInfo.calendar;
  }



  /**
   * Retrieves a <CODE>Date</CODE> containing the time at the last
   * update.
   *
   * @return A <CODE>Date</CODE> containing the time at the last
   *         update.
   */
  public static Date getDate() {
    return INSTANCE.timeInfo.date;
  }



  /**
   * Retrieves a string containing a normalized representation of the
   * current time in a generalized time format. The timestamp will
   * look like "20050101000000.000Z".
   *
   * @return A string containing a normalized representation of the
   *         current time in a generalized time format.
   */
  public static String getGeneralizedTime() {
    return INSTANCE.timeInfo.generalizedTime;
  }



  /**
   * Retrieves a string containing the current time in GMT. The
   * timestamp will look like "20050101000000Z".
   *
   * @return A string containing the current time in GMT.
   */
  public static String getGMTTime() {
    return INSTANCE.timeInfo.gmtTimestamp;
  }



  /**
   * Retrieves an integer containing the time in HHmm format at the
   * last update. It will be calculated as "(hourOfDay*100) +
   * minuteOfHour".
   *
   * @return An integer containing the time in HHmm format at the last
   *         update.
   */
  public static int getHourAndMinute() {
    return INSTANCE.timeInfo.hourAndMinute;
  }



  /**
   * Retrieves a string containing the current time in the local time
   * zone. The timestamp format will look like "01/Jan/2005:00:00:00
   * -0600".
   *
   * @return A string containing the current time in the local time
   *         zone.
   */
  public static String getLocalTime() {
    return INSTANCE.timeInfo.localTimestamp;
  }



  /**
   * Retrieves the time in nanoseconds from the most precise available
   * system timer. The value retured represents nanoseconds since some
   * fixed but arbitrary time.
   *
   * @return The time in nanoseconds from some fixed but arbitrary
   *         time.
   */
  public static long getNanoTime() {
    return INSTANCE.timeInfo.nanoTime;
  }



  /**
   * Retrieves the time in milliseconds since the epoch at the last
   * update.
   *
   * @return The time in milliseconds since the epoch at the last
   *         update.
   */
  public static long getTime() {
    return INSTANCE.timeInfo.time;
  }



  /**
   * @param args
   */
  public static void main(String[] args) {
    System.out.println("Starting...");

    Operation op1 = new Operation(1);
    Operation op2 = new Operation(2);
    Operation op3 = new Operation(3);

    op1.cancel();
    op2.cancel();
    op3.cancel();

    try {
      op1.setDone(true);

      System.out.println("Sleeping for 2s...");
      Thread.sleep(2000);

      op3.setDone(true);

      System.out.println("Sleeping for 1s...");
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      System.out.println("Couldn't sleep - was interrupted!");
    }

    System.out.println("Exiting");
  }



  /**
   * Creates and executes a one-shot action that becomes enabled after
   * the given delay.
   *
   * @param command
   *          the task to execute
   * @param delay
   *          the time from now to delay execution
   * @param unit
   *          the time unit of the delay parameter
   * @return a ScheduledFuture representing pending completion of the
   *         task and whose <tt>get()</tt> method will return
   *         <tt>null</tt> upon completion
   * @throws RejectedExecutionException
   *           if the task cannot be scheduled for execution
   * @throws NullPointerException
   *           if command is null
   * @see java.util.concurrent.ScheduledExecutorService#schedule(java.lang.Runnable,
   *      long, java.util.concurrent.TimeUnit)
   */
  public static ScheduledFuture<?> schedule(Runnable command, long delay,
      TimeUnit unit) {
    return INSTANCE.scheduler.schedule(command, delay, unit);
  }



  /**
   * Creates and executes a periodic action that becomes enabled first
   * after the given initial delay, and subsequently with the given
   * period; that is executions will commence after
   * <tt>initialDelay</tt> then <tt>initialDelay+period</tt>,
   * then <tt>initialDelay + 2 * period</tt>, and so on. If any
   * execution of the task encounters an exception, subsequent
   * executions are suppressed. Otherwise, the task will only
   * terminate via cancellation or termination of the executor. If any
   * execution of this task takes longer than its period, then
   * subsequent executions may start late, but will not concurrently
   * execute.
   *
   * @param command
   *          the task to execute
   * @param initialDelay
   *          the time to delay first execution
   * @param period
   *          the period between successive executions
   * @param unit
   *          the time unit of the initialDelay and period parameters
   * @return a ScheduledFuture representing pending completion of the
   *         task, and whose <tt>get()</tt> method will throw an
   *         exception upon cancellation
   * @throws RejectedExecutionException
   *           if the task cannot be scheduled for execution
   * @throws NullPointerException
   *           if command is null
   * @throws IllegalArgumentException
   *           if period less than or equal to zero
   * @see java.util.concurrent.ScheduledExecutorService#scheduleAtFixedRate(java.lang.Runnable,
   *      long, long, java.util.concurrent.TimeUnit)
   */
  public static ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
      long initialDelay, long period, TimeUnit unit) {
    return INSTANCE.scheduler.scheduleAtFixedRate(command, initialDelay,
        period, unit);
  }



  /**
   * Creates and executes a periodic action that becomes enabled first
   * after the given initial delay, and subsequently with the given
   * delay between the termination of one execution and the
   * commencement of the next. If any execution of the task encounters
   * an exception, subsequent executions are suppressed. Otherwise,
   * the task will only terminate via cancellation or termination of
   * the executor.
   *
   * @param command
   *          the task to execute
   * @param initialDelay
   *          the time to delay first execution
   * @param delay
   *          the delay between the termination of one execution and
   *          the commencement of the next
   * @param unit
   *          the time unit of the initialDelay and delay parameters
   * @return a ScheduledFuture representing pending completion of the
   *         task, and whose <tt>get()</tt> method will throw an
   *         exception upon cancellation
   * @throws RejectedExecutionException
   *           if the task cannot be scheduled for execution
   * @throws NullPointerException
   *           if command is null
   * @throws IllegalArgumentException
   *           if delay less than or equal to zero
   * @see java.util.concurrent.ScheduledExecutorService#scheduleWithFixedDelay(java.lang.Runnable,
   *      long, long, java.util.concurrent.TimeUnit)
   */
  public static ScheduledFuture<?> scheduleWithFixedDelay(Runnable command,
      long initialDelay, long delay, TimeUnit unit) {
    return INSTANCE.scheduler.scheduleWithFixedDelay(command, initialDelay,
        delay, unit);
  }

  // The scheduler.
  private final ScheduledExecutorService scheduler = Executors
      .newSingleThreadScheduledExecutor(new TimerThreadFactory());

  // Time information.
  private final TimeInfo timeInfo = new TimeInfo();



  // Prevent instantiation.
  private Timer() {
    this.scheduler.scheduleWithFixedDelay(timeInfo, 0, 1000,
        TimeUnit.MILLISECONDS);
  }
}
