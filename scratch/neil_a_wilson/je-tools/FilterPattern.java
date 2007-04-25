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



import java.util.*;
import java.util.concurrent.atomic.*;

import org.opends.server.types.*;



/**
 * This class implements a data type which can either hold a complete search
 * filter or one that contains a single bracketed pattern that will be
 * dynamically replaced with an integer value.  If there is a bracketed pattern,
 * it should contain a minimum and maximum value separated by a colon (for
 * sequential iteration) or a dash (for randomly-chosen values).  For example,
 * the string "(uid=user.[1-1000])" will cause an integer value to be chosen at
 * random between 1 and 1000 to be selected and used in place of the bracketed
 * range.
 */
public class FilterPattern
{
  // The iterator that will be used for sequential iteration.
  private static AtomicInteger sequentialIterator = new AtomicInteger(0);

  // Indicates whether a full, pre-parsed filter is available.
  private boolean filterAvailable;

  // Indicates whether to iterate sequentially or choose the values at random.
  private boolean sequential;

  // The maximum size for the filter string.
  private int filterSize;

  // The maximum value to use for the bracketed range.
  private int maxValue;

  // The minimum value to use for the bracketed range.
  private int minValue;

  // The span the minimum and maximum value.
  private int valueSpan;

  // The parent random number generator that will be used to seed the child
  // generators.
  private static Random parentRandom = new Random();

  // The random number generator to use for selecting a value from the bracketed
  // range.
  private Random random;

  // The fully-parsed filter to return.
  private SearchFilter parsedFilter;

  // The full DN pattern string as provided by the client.
  private String filterPatternString;

  // The prefix for the filter string.
  private String filterPrefix;

  // The suffix for the filter string.
  private String filterSuffix;



  /**
   * Creates a new filter pattern from the provided string.
   *
   * @param  filterPatternString  The pattern string to be decoded.
   *
   * @throws  Exception  If an error occurs while attempting to decode the
   *                     pattern string.
   */
  public FilterPattern(String filterPatternString)
         throws Exception
  {
    this.filterPatternString = filterPatternString;

    filterAvailable = true;
    random = new Random(parentRandom.nextLong());

    int openPos = filterPatternString.indexOf('[');
    if (openPos > 0)
    {
      int closePos = filterPatternString.indexOf(']', openPos+1);
      if (closePos > 0)
      {
        int separatorPos = filterPatternString.indexOf(':', openPos);
        if ((separatorPos > 0) && (separatorPos < closePos))
        {
          sequential = true;
        }
        else
        {
          separatorPos = filterPatternString.indexOf('-', openPos);
          if ((separatorPos > 0) && (separatorPos < closePos))
          {
            sequential = false;
          }
        }

        if (separatorPos > 0)
        {
          minValue =
               Integer.parseInt(filterPatternString.substring(openPos+1,
                                                              separatorPos));
          maxValue =
               Integer.parseInt(filterPatternString.substring(separatorPos+1,
                                                              closePos));
          valueSpan = maxValue - minValue + 1;
          sequentialIterator.set(minValue);

          filterPrefix    = filterPatternString.substring(0, openPos);
          filterSuffix    = filterPatternString.substring(closePos+1);
          filterSize      = filterPrefix.length() + filterSuffix.length() +
                            String.valueOf(maxValue).length();
          filterAvailable = false;
        }
      }
    }

    if (filterAvailable)
    {
      // If we get here, then we couldn't find a valid pattern, so we'll try to
      // parse it as a search filter.
      parsedFilter = SearchFilter.createFilterFromString(filterPatternString);
    }
  }



  /**
   * Retrieves the next search filter that should be used based on this pattern.
   *
   * @return  The next filter that should be used based on this pattern.
   *
   * @throws  Exception  If a problem occurs while constructing the filter.
   */
  public SearchFilter nextFilter()
         throws Exception
  {
    if (filterAvailable)
    {
      return parsedFilter;
    }
    else
    {
      int value;
      if (sequential)
      {
        value = sequentialIterator.getAndIncrement();
        if (value > maxValue)
        {
          if (sequentialIterator.compareAndSet(value, minValue+1))
          {
            value = minValue;
          }
          else
          {
            value = sequentialIterator.getAndIncrement();
          }
        }
      }
      else
      {
        value = random.nextInt(valueSpan) + minValue;
      }

      StringBuilder buffer = new StringBuilder(filterSize);
      buffer.append(filterPrefix);
      buffer.append(value);
      buffer.append(filterSuffix);
      return SearchFilter.createFilterFromString(buffer.toString());
    }
  }



  /**
   * Retrieves a string representation of this filter pattern.
   *
   * @return  A string representation of this filter pattern.
   */
  public String toString()
  {
    return filterPatternString;
  }
}

