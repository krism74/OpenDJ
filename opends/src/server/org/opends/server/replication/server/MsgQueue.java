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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 */
package org.opends.server.replication.server;

import java.util.SortedMap;
import java.util.TreeMap;

import org.opends.server.replication.common.ChangeNumber;
import org.opends.server.replication.protocol.UpdateMessage;

/**
 * This class is used to build ordered lists of UpdateMessage.
 * The order is defined by the order of the ChangeNumber of the UpdateMessage.
 */

public class MsgQueue
{
  private SortedMap<ChangeNumber, UpdateMessage>  map =
    new TreeMap<ChangeNumber, UpdateMessage>();

  // The total number of bytes for all the message in the queue.
  private int bytesCount = 0;

  /**
   * Return the first UpdateMessage in the MsgQueue.
   *
   * @return The first UpdateMessage in the MsgQueue.
   */
  public UpdateMessage first()
  {
    return map.get(map.firstKey());
  }

  /**
   * Return the last UpdateMessage in the MsgQueue.
   *
   * @return The last UpdateMessage in the MsgQueue.
   */
  public UpdateMessage last()
  {
    return map.get(map.lastKey());
  }

  /**
   * Returns the number of elements in this MsgQueue.
   *
   * @return The number of elements in this MsgQueue.
   */
  public int count()
  {
    return map.size();
  }

  /**
   * Returns the number of bytes in this MsgQueue.
   *
   * @return The number of bytes in this MsgQueue.
   */
  public int bytesCount()
  {
    return bytesCount;
  }

  /**
   * Returns <tt>true</tt> if this MsgQueue contains no UpdateMessage.
   *
   * @return <tt>true</tt> if this MsgQueue contains no UpdateMessage.
   */
  public boolean isEmpty()
  {
    return map.isEmpty();
  }


  /**
   * Add an UpdateMessage to this MessageQueue.
   *
   * @param update The UpdateMessage to add to this MessageQueue.
   */
  public void add(UpdateMessage update)
  {
    map.put(update.getChangeNumber(), update);
    bytesCount += update.size();
  }

  /**
   * Get and remove the first UpdateMessage in this MessageQueue.
   *
   * @return The first UpdateMessage in this MessageQueue.
   */
  public UpdateMessage removeFirst()
  {
    UpdateMessage msg = map.get(map.firstKey());
    map.remove(msg.getChangeNumber());
    bytesCount -= msg.size();
    return msg;
  }

  /**
   * Returns <tt>true</tt> if this map contains an UpdateMessage
   * with the same ChangeNumber as the given UpdateMessage.
   *
   * @param msg UpdateMessage whose presence in this queue is to be tested.
   *
   * @return <tt>true</tt> if this map contains an UpdateMessage
   *         with the same ChangeNumber as the given UpdateMessage.
   *
   */
  public boolean contains(UpdateMessage msg)
  {
    return map.containsKey(msg.getChangeNumber());
  }

  /**
   * Removes all UpdateMessage form this queue.
   */
  public void clear()
  {
    map.clear();
    bytesCount = 0;
  }
}
