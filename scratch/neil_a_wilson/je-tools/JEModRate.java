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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.tools.je;



import java.text.*;
import java.util.*;
import java.util.concurrent.atomic.*;

import org.opends.server.core.*;
import org.opends.server.extensions.*;
import org.opends.server.types.*;
import org.opends.server.util.args.*;



/**
 * This program provides a utility for performing repeated modifications in a
 * multithreaded environment against a database using the Berkeley DB Java
 * Edition.  It attempts to mimic only the backend processing performed by the
 * server without any of the core or protocol layer involved.
 */
public class JEModRate
{
  // The counter that will be used to keep track of the number of modifications
  // performed.
  static AtomicInteger modCounter = new AtomicInteger(0);
  static AtomicInteger modTimer   = new AtomicInteger(0);



  /**
   * Parse the command line arguments, load the database, and create the
   * appropriate number of worker threads to search the DB.
   *
   * @param  args  The command line arguments provided to this program.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public static void main(String[] args)
         throws Exception
  {
    // Create the argument parser, define all the arguments, and parse them.
    ArgumentParser argParser =
         new ArgumentParser(JEModRate.class.getName(),
                  "Simulate modify operations in the Berkeley DB JE backend",
                  false);

    StringArgument configClassArg =
         new StringArgument("configclass", 'C', "configClass", false, false,
                            true, "{configClass}",
                            ConfigFileHandler.class.getName(), null, -1);
    configClassArg.setHidden(true);
    argParser.addArgument(configClassArg);

    StringArgument configFileArg =
         new StringArgument("configfile", 'F', "configFile", true, false,
                            true, "{configFile}", null, null, -1);
    configFileArg.setHidden(true);
    argParser.addArgument(configFileArg);

    StringArgument entryDNArg =
         new StringArgument("entrydn", 'b', "entryDN", false, false, true,
                            "{baseDN}", "", null, -1);
    argParser.addArgument(entryDNArg);

    IntegerArgument numThreadsArg =
         new IntegerArgument("threads", 't', "numThreads", false, false, true,
                             "{numThreads}", 1, null, true, 1, false, 0, -1);
    argParser.addArgument(numThreadsArg);

    StringArgument attributeArg =
         new StringArgument("attribute", 'a', "attribute", false, false,
                            true, "{attribute}", "description", null, -1);
    argParser.addArgument(attributeArg);

    IntegerArgument valueLengthArg =
         new IntegerArgument("length", 'l', "valueLength", false, false, true,
                             "{valueLength}", 12, null, true, 1, false, 0, -1);
    argParser.addArgument(valueLengthArg);

    argParser.parseArguments(args);
    if (argParser.usageDisplayed())
    {
      return;
    }


    // Create and start the Directory Server instance.
    String[] directoryServerArgs =
    {
      "--configClass", configClassArg.getValue(),
      "--configFile", configFileArg.getValue(),
      "--nodetach"
    };
    DirectoryServer.main(directoryServerArgs);


    // Get the decoded values from the arguments.
    AttributeType attributeType =
         DirectoryServer.getAttributeType(attributeArg.getValue().toLowerCase(),
                                          true);
    int numThreads = numThreadsArg.getIntValue();
    int valueLength = valueLengthArg.getIntValue();


    // Create the decimal formatter that will be used to render the output.
    DecimalFormat decimalFormat = new DecimalFormat("0.00");


    // Create and start all of the threads.
    JEModifyThread[] modifyThreads = new JEModifyThread[numThreads];
    for (int i=0; i < numThreads; i++)
    {
      modifyThreads[i] = new JEModifyThread(i, entryDNArg.getValue(),
                                            attributeType, valueLength);
    }

    for (int i=0; i < numThreads; i++)
    {
      modifyThreads[i].start();
    }


    // Operate in a loop, sleeping for 5 seconds at a time.  Then, wake up and
    // print the total number of operations performed.
    while (true)
    {
      Thread.sleep(5000);

      int modsCompleted = JEModRate.modCounter.getAndSet(0);
      int modTimeMicros = JEModRate.modTimer.getAndSet(0);

      double avgModsPerSecond = 1.0 * modsCompleted / 5;
      double avgModDuration = 1.0 * modTimeMicros / modsCompleted;

      StringBuilder buffer = new StringBuilder();
      buffer.append(decimalFormat.format(avgModsPerSecond));
      buffer.append(" modifies/second (");
      buffer.append(decimalFormat.format(avgModDuration));
      buffer.append(" microseconds/modify)");

      System.out.println(buffer.toString());
    }
  }
}

