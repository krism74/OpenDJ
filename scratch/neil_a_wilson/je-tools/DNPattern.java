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
 * This class implements a data type which can either hold a complete DN or one
 * that contains a single bracketed pattern that will be dynamically replaced
 * with an integer value.  If there is a bracketed pattern, it should contain
 * a minimum and maximum value separated by a colon (for sequential iteration)
 * or a dash (for randomly-chosen values).  For example, the string
 * "uid=user.[1-1000],ou=People,dc=example,dc=com" will cause an integer value
 * to be chosen at random between 1 and 1000 to be selected and used in place of
 * the bracketed range.
 */
public class DNPattern
{
  // The iterator that will be used for sequential iteration.
  private static AtomicInteger sequentialIterator = new AtomicInteger(0);

  // Indicates whether a full, pre-parsed DN is available.
  private boolean dnAvailable;

  // Indicates whether to iterate sequentially or choose the values at random.
  private boolean sequential;

  // The fully-parsed DN to return.
  private DN parsedDN;

  // The maximum size of the missing RDN component.
  private int missingRDNSize;

  // The maximum value to use for the bracketed range.
  private int maxValue;

  // The minimum value to use for the bracketed range.
  private int minValue;

  // The slot in the RDN array of the missing component.
  private int missingSlot;

  // The span the minimum and maximum value.
  private int valueSpan;

  // The parent random number generator that will be used to seed the child
  // generators.
  private static Random parentRandom = new Random();

  // The random number generator to use for selecting a value from the bracketed
  // range.
  private Random random;

  // The RDN array that will be used to construct the DN.
  private RDN[] rdnArray;

  // The full DN pattern string as provided by the client.
  private String dnPatternString;

  // The prefix for the missing RDN value.
  private String missingRDNPrefix;

  // The suffix for the missing RDN value.
  private String missingRDNSuffix;



  /**
   * Creates a new DN pattern from the provided string.
   *
   * @param  dnPatternString  The pattern string to be decoded.
   *
   * @throws  Exception  If an error occurs while attempting to decode the
   *                     pattern string.
   */
  public DNPattern(String dnPatternString)
         throws Exception
  {
    this.dnPatternString = dnPatternString;

    dnAvailable = true;
    random = new Random(parentRandom.nextLong());

    int openPos = dnPatternString.indexOf('[');
    if (openPos > 0)
    {
      int closePos = dnPatternString.indexOf(']', openPos+1);
      if (closePos > 0)
      {
        int separatorPos = dnPatternString.indexOf(':', openPos);
        if ((separatorPos > 0) && (separatorPos < closePos))
        {
          sequential = true;
        }
        else
        {
          separatorPos = dnPatternString.indexOf('-', openPos);
          if ((separatorPos > 0) && (separatorPos < closePos))
          {
            sequential = false;
          }
        }

        if (separatorPos > 0)
        {
          minValue = Integer.parseInt(dnPatternString.substring(openPos+1,
                                                                separatorPos));
          maxValue = Integer.parseInt(dnPatternString.substring(separatorPos+1,
                                                                closePos));
          valueSpan = maxValue - minValue + 1;
          sequentialIterator.set(minValue);

          // Temporarily replace the bracketed range with a random integer
          // within the span so that we can parse the array and figure out which
          // slot has the missing value.
          while (true)
          {
            int randomValue = random.nextInt(valueSpan) + minValue;
            String valueStr = String.valueOf(randomValue);
            if (dnPatternString.indexOf(valueStr) < 0)
            {
              String tempDNString =
                   dnPatternString.substring(0, openPos) +
                        valueStr + dnPatternString.substring(closePos+1);
              DN tempDN = DN.decode(tempDNString);
              int numComponents = tempDN.getNumComponents();
              rdnArray = new RDN[numComponents];
              for (int i=0; i < numComponents; i++)
              {
                rdnArray[i] = tempDN.getRDN(i);
                String rdnStr = rdnArray[i].toString();
                int valueStrPos= rdnStr.indexOf(valueStr);
                if (valueStrPos > 0)
                {
                  missingSlot = i;
                  missingRDNPrefix = rdnStr.substring(0, valueStrPos);
                  missingRDNSuffix =
                       rdnStr.substring(valueStrPos+valueStr.length());
                  missingRDNSize = missingRDNPrefix.length() +
                                   missingRDNSuffix.length() +
                                   String.valueOf(maxValue).length();
                }
              }
              break;
            }
          }

          dnAvailable = false;
        }
      }
    }

    if (dnAvailable)
    {
      // If we get here, then we couldn't find a valid pattern, so we'll try to
      // parse it as a DN.
      parsedDN = DN.decode(dnPatternString);
    }
  }



  /**
   * Retrieves the next DN that should be used based on this pattern.
   *
   * @return  The next DN that should be used based on this pattern.
   *
   * @throws  Exception  If a problem occurs while constructing the DN.
   */
  public DN nextDN()
         throws Exception
  {
    if (dnAvailable)
    {
      return parsedDN;
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

      StringBuilder buffer = new StringBuilder(missingRDNSize);
      buffer.append(missingRDNPrefix);
      buffer.append(value);
      buffer.append(missingRDNSuffix);
      rdnArray[missingSlot] = RDN.decode(buffer.toString());
      return new DN(rdnArray);
    }
  }



  /**
   * Retrieves a string representation of this DN pattern.
   *
   * @return  A string representation of this DN pattern.
   */
  public String toString()
  {
    return dnPatternString;
  }
}

