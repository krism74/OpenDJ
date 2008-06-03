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
package org.opends.scratch.txn;



/**
 * The type of lock to be taken on during transacted backend
 * operations.
 */
public enum LockType
{
  /**
   * Read committed isolation. A read lock will be taken on the entry
   * and released immediately afterwards.
   */
  READ_COMMITTED("READ_COMMITTED"),

  /**
   * Repeatable read isolation with a shared lock. A shared lock will
   * be taken on the entry and held until the transaction is either
   * committed or aborted.
   */
  SHARED("SHARED"),

  /**
   * Repeatable read isolation with an exclusive lock. A exclusive
   * lock will be taken on the entry and held until the transaction is
   * either committed or aborted.
   */
  EXCLUSIVE("EXCLUSIVE");

  // String representation.
  private final String name;



  // Constructor.
  private LockType(String name)
  {
    this.name = name;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return name;
  }

}
