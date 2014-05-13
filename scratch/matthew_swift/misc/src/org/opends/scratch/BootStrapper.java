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



import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;



/**
 * A demo application which could be used in a bootstrap phase of a
 * command line application to figure out the host architecture and
 * spawn off a suitably tuned JVM.
 */
public final class BootStrapper {

  // Prevent instantiation.
  private BootStrapper() {
    // Do nothing.
  }



  /**
   * Main application.
   *
   * @param args
   *          The command line arguments.
   */
  public static void main(String[] args) {
    Long physicalMemory = -1L;
    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    try {
      // Assuming the RuntimeMXBean has been registered in the MBean
      // server.
      ObjectName oname = new ObjectName(
          ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);

      // Check if this MXBean contains Sun's extension
      if (mbs.isInstanceOf(oname, "com.sun.management.OperatingSystemMXBean")) {
        physicalMemory = (Long) mbs.getAttribute(oname,
            "TotalPhysicalMemorySize");
      }
    } catch (Exception e) {
      // Do nothing.
    }

    System.out.println("Vendor:          " + System.getProperty("java.vendor"));
    System.out
        .println("Version:         " + System.getProperty("java.version"));
    System.out.println("OS Name:         " + System.getProperty("os.name"));
    System.out.println("OS Arch:         " + System.getProperty("os.arch"));
    System.out.println("CPUs:            "
        + Runtime.getRuntime().availableProcessors());
    System.out.println("Physical Memory: " + physicalMemory);
    System.out.println("Max Heap:        " + Runtime.getRuntime().maxMemory());
  }

}
