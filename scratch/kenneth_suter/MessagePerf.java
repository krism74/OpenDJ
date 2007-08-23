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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

import org.opends.messages.CoreMessages;
import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;

import java.util.ResourceBundle;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 *
 */
public class MessagePerf {

  static private final int TEST_ITERATIONS = 10;

  static private final int WORK_ITERATIONS = 1 * 1000 * 1000;

  static private final int PRIME_ITERATIONS = 2;

  public static void main(String[] args) {

    HashMap<Integer,String> MAP = new HashMap<Integer,String>();
    MAP.put(1, "abc %s");

    long min = Long.MAX_VALUE;
    long max = Long.MIN_VALUE;
    List<Long> perSecs = new ArrayList<Long>();

    for (int i = 0; i < TEST_ITERATIONS; i++) {
      long start = System.currentTimeMillis();
      for (int j = 0; j < WORK_ITERATIONS; j++) {

        // alone after 262K - 280K o/s
        // alone before 93K o/s
        // alone cache format string 41,000 - 55,000K
        CoreMessages.ERR_ADD_CANNOT_ADD_ROOT_DSE.get().toString();

        // MAP.get(1).toString();

        // String.format(MAP.get(1), "123").toString();

        // ResourceBundle rb = ResourceBundle.getBundle("messages/core");
        // String s= rb.getString("MILD_ERR_ABANDON_OP_NO_SUCH_OPERATION_223");
        // String.format(s, 27).toString();

        // ResourceBundle rb = ResourceBundle.getBundle("messages/core");
        // rb.getString("MILD_ERR_ADD_CANNOT_ADD_ROOT_DSE_230").toString();

      }
      long duration = (System.currentTimeMillis() - start);
      long perSec = (WORK_ITERATIONS / duration);
      if (i >= PRIME_ITERATIONS) {
        min = Math.min(min, perSec);
        max = Math.max(max, perSec);
        perSecs.add(perSec);
      }
      // System.out.println(perSec + " K");
    }
    long total = 0;
    for (Long l : perSecs) {
      total += l;
    }
    report("test toString() no arg message: ", min, max, total);


    min = Long.MAX_VALUE;
    max = Long.MIN_VALUE;
    perSecs = new ArrayList<Long>();
    for (int i = 0; i < TEST_ITERATIONS; i++) {
      long start = System.currentTimeMillis();
      for (int j = 0; j < WORK_ITERATIONS; j++) {

        // alone after 95K o/s
        // alone before 99K o/s
        // alone cache format string 153 - 209K
        CoreMessages.ERR_ABANDON_OP_NO_SUCH_OPERATION.get(27).toString();
      }
      long duration = (System.currentTimeMillis() - start);
      long perSec = (WORK_ITERATIONS / duration);
      if (i >= PRIME_ITERATIONS) {
        min = Math.min(min, perSec);
        max = Math.max(max, perSec);
        perSecs.add(perSec);
      }
      // System.out.println(perSec + " K");
    }
    total = 0;
    for (Long l : perSecs) {
      total += l;
    }
    report("test toString 1 numeric arg message: ", min, max, total);

    min = Long.MAX_VALUE;
    max = Long.MIN_VALUE;
    perSecs = new ArrayList<Long>();
    for (int i = 0; i < TEST_ITERATIONS; i++) {
      long start = System.currentTimeMillis();
      for (int j = 0; j < WORK_ITERATIONS; j++) {

        // alone cache format string 196K
        CoreMessages.ERR_ADD_ASSERTION_FAILED.get("abc").toString();
      }
      long duration = (System.currentTimeMillis() - start);
      long perSec = (WORK_ITERATIONS / duration);
      if (i >= PRIME_ITERATIONS) {
        min = Math.min(min, perSec);
        max = Math.max(max, perSec);
        perSecs.add(perSec);
      }
      // System.out.println(perSec + " K");
    }
    total = 0;
    for (Long l : perSecs) {
      total += l;
    }
    report("test toString 1 string arg message: ", min, max, total);

    min = Long.MAX_VALUE;
    max = Long.MIN_VALUE;
    perSecs = new ArrayList<Long>();
    for (int i = 0; i < TEST_ITERATIONS; i++) {
      long start = System.currentTimeMillis();
      for (int j = 0; j < WORK_ITERATIONS; j++) {

        // alone cache format string 189K
        CoreMessages.ERR_ADD_CANNOT_LOCK_PARENT.get("abc", "123").toString();
      }
      long duration = (System.currentTimeMillis() - start);
      long perSec = (WORK_ITERATIONS / duration);
      if (i >= PRIME_ITERATIONS) {
        min = Math.min(min, perSec);
        max = Math.max(max, perSec);
        perSecs.add(perSec);
      }
      // System.out.println(perSec + " K");
    }
    total = 0;
    for (Long l : perSecs) {
      total += l;
    }
    report("test toString 2 string arg message: ", min, max, total);

    min = Long.MAX_VALUE;
    max = Long.MIN_VALUE;
    perSecs = new ArrayList<Long>();
    for (int i = 0; i < TEST_ITERATIONS; i++) {
      long start = System.currentTimeMillis();
      for (int j = 0; j < WORK_ITERATIONS; j++) {
        // alone after 1,112K o/s
        // alone before 765K o/s
        // alone cache format string 980K - 1,050K
        Message.raw("abc").toString();
      }
      long duration = (System.currentTimeMillis() - start);
      long perSec = (WORK_ITERATIONS / duration);
      if (i >= PRIME_ITERATIONS) {
        min = Math.min(min, perSec);
        max = Math.max(max, perSec);
        perSecs.add(perSec);
      }
      // System.out.println(perSec + " K");
    }
    total = 0;
    for (Long l : perSecs) {
      total += l;
    }
    report("test toString no arg raw message5: ", min, max, total);

    min = Long.MAX_VALUE;
    max = Long.MIN_VALUE;
    perSecs = new ArrayList<Long>();
    for (int i = 0; i < TEST_ITERATIONS; i++) {
      long start = System.currentTimeMillis();
      for (int j = 0; j < WORK_ITERATIONS; j++) {
        // alone after 1,112K o/s
        // alone before 765K o/s
        // alone cache format string 980K - 1,050K
        MessageBuilder mb = new MessageBuilder();
        mb.append("abc");
        mb.append("123");
        mb.toString();
      }
      long duration = (System.currentTimeMillis() - start);
      long perSec = (WORK_ITERATIONS / duration);
      if (i >= PRIME_ITERATIONS) {
        min = Math.min(min, perSec);
        max = Math.max(max, perSec);
        perSecs.add(perSec);
      }
      // System.out.println(perSec + " K");
    }
    total = 0;
    for (Long l : perSecs) {
      total += l;
    }
    report("test MessageBuilder.toString: ", min, max, total);


  }

  static void report(String test, long min, long max, long total) {
    System.out.println(test);
    System.out.println(" min:" + min + "K");
    System.out.println(" max:" + max + "K");
    System.out.println(" avg:" + total/(TEST_ITERATIONS - PRIME_ITERATIONS) + "K");
    System.out.println();
  }

  // alone 345K-350K o/s
  // ResourceBundle rb = ResourceBundle.getBundle("messages/core");
  // rb.getString("MILD_ERR_ADD_CANNOT_ADD_ROOT_DSE_230").toString();

  // alone 107K o/s
  // ResourceBundle rb = ResourceBundle.getBundle("messages/core");
  // String s= rb.getString("MILD_ERR_ABANDON_OP_NO_SUCH_OPERATION_223");
  // String.format(s, 27).toString();

  // alone 424 - 430K o/s
  // String.format(MAP.get(1), "123").toString();

  // alone 666,000K
  // MAP.get(1).toString();



}
