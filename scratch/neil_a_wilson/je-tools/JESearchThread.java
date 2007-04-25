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



import org.opends.server.api.*;
import org.opends.server.backends.jeb.*;
import org.opends.server.core.*;
import org.opends.server.types.*;



/**
 * This class defines a thread that will repeatedly perform searches in a
 * Berkeley DB JE backend.
 */
public class JESearchThread
       extends Thread
{
  // The DN pattern to use for obtaining the base DN.
  private DNPattern baseDNPattern;

  // The filter pattern to use for obtaining the search filter.
  private FilterPattern filterPattern;

  // The search scope to use for this thread.
  private SearchScope scope;



  /**
   * Creates a new JE search thread with the provided information.
   *
   * @param  threadNumber  The thread number assigned to this thread.
   * @param  baseStr       The base DN pattern to use for this thread.
   * @param  scope         The search scope to use for this thread.
   * @param  filterStr     The search filter to use for this thread.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public JESearchThread(int threadNumber, String baseStr, SearchScope scope,
                        String filterStr)
         throws Exception
  {
    setName("JE Search Thread " + threadNumber);

    this.scope = scope;

    baseDNPattern = new DNPattern(baseStr);
    filterPattern = new FilterPattern(filterStr);
  }



  /**
   * Operate in a loop, repeatedly searching in the JE backend.
   */
  public void run()
  {
    try
    {
      while (true)
      {
        // Get the base DN and filter to use for this search.
        DN baseDN = baseDNPattern.nextDN();
        SearchFilter filter = filterPattern.nextFilter();

        // Get the appropriate backend for the search and cast it to a JE
        // backend.
        Backend backend = DirectoryServer.getBackend(baseDN);
        BackendImpl jeBackend = (BackendImpl) backend;

        // Get the appropriate entry container for the base DN.
        RootContainer  rootContainer  = jeBackend.getRootContainer();
        EntryContainer entryContainer = rootContainer.getEntryContainer(baseDN);

        // If it's a base-level search, then get the base entry and match it
        // against the filter.  Otherwise, use the indexes to process the
        // search.
        if (scope == SearchScope.BASE_OBJECT)
        {
          EntryID entryID = entryContainer.getDN2ID().get(null, baseDN);
          Entry entry = entryContainer.getID2Entry().get(null, entryID);
          if (filter.matchesEntry(entry))
          {
            JESearchRate.entryCounter.incrementAndGet();
          }
        }
        else
        {
          AttributeType attributeType;
          AttributeIndex attributeIndex;
          EntryIDSet idSet = null;

          switch (filter.getFilterType())
          {
            case AND:
              // NYI
              break;

            case OR:
              // NYI
              break;

            case NOT:
              // NYI
              break;

            case EQUALITY:
              attributeType = filter.getAttributeType();
              attributeIndex = entryContainer.getAttributeIndex(attributeType);
              idSet = attributeIndex.evaluateEqualityFilter(filter);
              break;

            case SUBSTRING:
              attributeType = filter.getAttributeType();
              attributeIndex = entryContainer.getAttributeIndex(attributeType);
              idSet = attributeIndex.evaluateSubstringFilter(filter);
              break;

            case GREATER_OR_EQUAL:
              attributeType = filter.getAttributeType();
              attributeIndex = entryContainer.getAttributeIndex(attributeType);
              idSet = attributeIndex.evaluateGreaterOrEqualFilter(filter);
              break;

            case LESS_OR_EQUAL:
              attributeType = filter.getAttributeType();
              attributeIndex = entryContainer.getAttributeIndex(attributeType);
              idSet = attributeIndex.evaluateLessOrEqualFilter(filter);
              break;

            case PRESENT:
              attributeType = filter.getAttributeType();
              attributeIndex = entryContainer.getAttributeIndex(attributeType);
              idSet = attributeIndex.evaluatePresenceFilter(filter);
              break;

            case APPROXIMATE_MATCH:
              attributeType = filter.getAttributeType();
              attributeIndex = entryContainer.getAttributeIndex(attributeType);
              idSet = attributeIndex.evaluateApproximateFilter(filter);
              break;

            case EXTENSIBLE_MATCH:
              // NYI
              break;

            default:
              throw new IllegalArgumentException("Invalid filter type " +
                                                 filter.getFilterType());
          }

          if (idSet != null)
          {
            ID2Entry id2Entry = entryContainer.getID2Entry();
            for (EntryID id : idSet)
            {
              Entry entry = id2Entry.get(null, id);
              if (entry != null)
              {
                if (entry.matchesBaseAndScope(baseDN, scope) &&
                    filter.matchesEntry(entry))
                {
                  JESearchRate.entryCounter.incrementAndGet();
                }
              }
            }
          }
        }

        JESearchRate.searchCounter.incrementAndGet();
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
      return;
    }
  }
}

