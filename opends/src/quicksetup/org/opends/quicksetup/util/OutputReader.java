/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.util;

import java.io.BufferedReader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
   * This class is used to read an input stream and process ouput.
 */
abstract public class OutputReader {

  static private Logger LOG = Logger.getLogger(OutputReader.class.getName());

  /**
   * Called whenever new input is read from the reader.
   * @param line String representing new input
   */
  public abstract void processLine(String line);

  /**
   * The protected constructor.
   *
   * @param reader  the BufferedReader of the stop process.
   */
  public OutputReader(final BufferedReader reader) {
    Thread t = new Thread(new Runnable() {
      public void run() {
        try {
          String line;
          while (null != (line = reader.readLine())) {
            processLine(line);
          }
        } catch (Throwable t) {
          LOG.log(Level.INFO, "error reading output", t);
        }
      }
    });
    t.start();
  }
}
