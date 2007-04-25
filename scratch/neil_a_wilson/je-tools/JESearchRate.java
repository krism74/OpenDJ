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
 * This program provides a utility for performing repeated searches in a
 * multithreaded environment against a database using the Berkeley DB Java
 * Edition.  It attempts to mimic only the backend processing performed by the
 * server without any of the core or protocol layer involved.
 */
public class JESearchRate
{
  // The counter that will be used to keep track of the number of searches
  // performed.
  static AtomicInteger searchCounter = new AtomicInteger(0);

  // The counter that will be used to keep track of the number of entries
  // returned.
  static AtomicInteger entryCounter = new AtomicInteger(0);



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
         new ArgumentParser(JESearchRate.class.getName(),
                  "Simulate search operations in the Berkeley DB JE backend",
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

    StringArgument baseDNArg =
         new StringArgument("basedn", 'b', "baseDN", false, false, true,
                            "{baseDN}", "", null, -1);
    argParser.addArgument(baseDNArg);

    LinkedHashSet<String> scopeValues = new LinkedHashSet<String>(4);
    scopeValues.add("base");
    scopeValues.add("one");
    scopeValues.add("sub");
    scopeValues.add("subordinate");
    MultiChoiceArgument scopeArg =
         new MultiChoiceArgument("scope", 's', "scope", false, false, true,
                                 "{scope}", "sub", null, scopeValues, false,
                                 -1);
    argParser.addArgument(scopeArg);

    IntegerArgument numThreadsArg =
         new IntegerArgument("threads", 't', "numThreads", false, false, true,
                             "{numThreads}", 1, null, true, 1, false, 0, -1);
    argParser.addArgument(numThreadsArg);

    StringArgument filterArg =
         new StringArgument("filter", 'f', "filter", true, false, true,
                            "{filter}", null, null, -1);
    argParser.addArgument(filterArg);

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
    int numThreads = numThreadsArg.getIntValue();

    SearchScope searchScope = SearchScope.WHOLE_SUBTREE;
    String scopeStr = scopeArg.getValue().toLowerCase();
    if (scopeStr.equals("base"))
    {
      searchScope = SearchScope.BASE_OBJECT;
    }
    else if (scopeStr.equals("one"))
    {
      searchScope = SearchScope.SINGLE_LEVEL;
    }
    else if (scopeStr.equals("subordinate"))
    {
      searchScope = SearchScope.SUBORDINATE_SUBTREE;
    }


    // Create the decimal formatter that will be used to render the output.
    DecimalFormat decimalFormat = new DecimalFormat("0.00");


    // Create and start all of the threads.
    JESearchThread[] searchThreads = new JESearchThread[numThreads];
    for (int i=0; i < numThreads; i++)
    {
      searchThreads[i] = new JESearchThread(i, baseDNArg.getValue(),
                                            searchScope, filterArg.getValue());
    }

    for (int i=0; i < numThreads; i++)
    {
      searchThreads[i].start();
    }


    // Operate in a loop, sleeping for 5 seconds at a time.  Then, wake up and
    // print the total number of operations performed.
    while (true)
    {
      Thread.sleep(5000);

      int searchesCompleted = JESearchRate.searchCounter.getAndSet(0);
      int entriesReturned   = JESearchRate.entryCounter.getAndSet(0);

      double avgSearchesPerSecond = 1.0 * searchesCompleted / 5;
      double avgEntriesPerSearch  = 1.0 * entriesReturned / searchesCompleted;
      double avgEntriesPerSecond  = 1.0 * entriesReturned / 5;

      StringBuilder buffer = new StringBuilder();
      buffer.append(decimalFormat.format(avgSearchesPerSecond));
      buffer.append(" searches/second (");
      buffer.append(decimalFormat.format(avgEntriesPerSearch));
      buffer.append(" entries/search; ");
      buffer.append(decimalFormat.format(avgEntriesPerSecond));
      buffer.append(" entries/second)");

      System.out.println(buffer.toString());
    }
  }
}

