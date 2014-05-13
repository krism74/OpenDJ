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



import org.opends.scratch.txn.dummy.Entry;
import org.opends.scratch.txn.dummy.Reference;



/**
 * A call-back interface for processing backend search results.
 */
public interface SearchResultHandler
{

  /**
   * Handles an entry returned from a backend search.
   *
   * @param entry
   *          The entry.
   */
  void handleEntry(Entry entry);



  /**
   * Handles a reference returned from a backend search.
   *
   * @param reference
   *          The reference.
   */
  void handleReference(Reference reference);
}
