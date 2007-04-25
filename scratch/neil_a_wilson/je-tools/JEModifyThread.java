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
import java.util.concurrent.locks.*;

import com.sleepycat.je.*;

import org.opends.server.api.*;
import org.opends.server.backends.jeb.*;
import org.opends.server.core.*;
import org.opends.server.protocols.asn1.*;
import org.opends.server.types.*;



/**
 * This class defines a thread that will repeatedly perform modifications in a
 * Berkeley DB JE backend.
 */
public class JEModifyThread
       extends Thread
{
  // The attribute type for the attribute to modify.
  private AttributeType attributeType;

  // The alphabet of characters that may be used for the constructed values.
  private byte[] alphabet;

  // The DN pattern to use for obtaining the entry DN.
  private DNPattern entryDNPattern;

  // The number of characters for the attribute to modify.
  private int valueLength;

  // The parent random number generator used to seed the individual generators.
  private static Random parentRandom = new Random();

  // The random number generator used for this thread.
  private Random random;

  // The human-readable name for the attribute to modify.
  private String attributeName;



  /**
   * Creates a new JE modify thread with the provided information.
   *
   * @param  threadNumber  The thread number assigned to this thread.
   * @param  baseStr       The base DN pattern to use for this thread.
   * @param  scope         The search scope to use for this thread.
   * @param  filterStr     The search filter to use for this thread.
   *
   * @throws  Exception  If an unexpected problem occurs.
   */
  public JEModifyThread(int threadNumber, String entryDNStr,
                        AttributeType attributeType, int valueLength)
         throws Exception
  {
    setName("JE Modify Thread " + threadNumber);

    this.attributeType = attributeType;
    this.valueLength   = valueLength;

    entryDNPattern = new DNPattern(entryDNStr);

    attributeName = attributeType.getNameOrOID();
    alphabet = "abcdefghijklmnopqrstuvwxyz".getBytes("UTF-8");
    random = new Random(parentRandom.nextLong());
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
        long startTime = System.nanoTime();

        // Get the DN of the entry to modify.
        DN entryDN = entryDNPattern.nextDN();
        Lock entryLock = LockManager.lockWrite(entryDN);

        try
        {
          // Get the appropriate backend for the entry and cast it to a JE
          // backend.
          Backend backend = DirectoryServer.getBackend(entryDN);
          BackendImpl jeBackend = (BackendImpl) backend;

          // Get the appropriate entry container for the entry.
          RootContainer  rootContainer  = jeBackend.getRootContainer();
          EntryContainer entryContainer =
               rootContainer.getEntryContainer(entryDN);

          // Construct the new value to use for the entry.
          byte[] valueBytes = new byte[valueLength];
          for (int i=0; i < valueLength; i++)
          {
            valueBytes[i] = alphabet[random.nextInt(alphabet.length)];
          }
          AttributeValue value = new AttributeValue(attributeType,
                                          new ASN1OctetString(valueBytes));
          LinkedHashSet<AttributeValue> values =
               new LinkedHashSet<AttributeValue>(1);
          values.add(value);
          ArrayList<Attribute> attrList = new ArrayList<Attribute>(1);
          attrList.add(new Attribute(attributeType, attributeName, values));

          // Operate in a loop, repeating until we have completed the update
          // successfully.
          while (true)
          {
            Transaction txn = entryContainer.beginTransaction();

            try
            {
              // Get the requested entry from the backend and duplicate it.
              EntryID entryID = entryContainer.getDN2ID().get(txn, entryDN);
              ID2Entry id2Entry = entryContainer.getID2Entry();
              Entry entry = id2Entry.get(txn, entryID);
              Entry newEntry = entry.duplicate(false);


              // Construct the new value and put it in the new entry.
              newEntry.putAttribute(attributeType, attrList);


              // Write the updated entry back into the database.
              id2Entry.put(txn, entryID, newEntry);
              txn.commit();

              int elapsedMicroseconds =
                       (int) ((System.nanoTime() - startTime) / 1000);
              JEModRate.modCounter.incrementAndGet();
              JEModRate.modTimer.addAndGet(elapsedMicroseconds);
              break;
            }
            catch (DeadlockException de)
            {
              txn.abort();
            }
          }
        }
        finally
        {
          LockManager.unlock(entryDN, entryLock);
        }
      }
    }
    catch (Exception e)
    {
      e.printStackTrace();
      return;
    }
  }
}

