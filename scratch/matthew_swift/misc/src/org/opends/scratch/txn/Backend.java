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



import org.opends.scratch.txn.dummy.DN;
import org.opends.scratch.txn.dummy.DirectoryException;
import org.opends.scratch.txn.dummy.Entry;



/**
 * Proposed backend API changes for transactions.
 * <h2>TODO</h2>
 * <ul>
 * <li>long running backend primitives (deleteSubtree, renameSubtree,
 * and search) need to handle operation cancelation. This was
 * previously done using the Operation class. Should the cancelation
 * handler be defined by the transaction configuration, rather than
 * per primitive?
 * <li>how should transaction timeouts be managed?
 * <li>how should deadlocks be managed? Is DirectoryException enough?
 * <li>how will ensure that re-entrant code re-uses existing txn
 * context? For example, access control checks accessing the entry
 * being modified via an internal operation or
 * DirectoryServer.getEntry(DN).
 * </ul>
 */
public abstract class Backend
{

  // This interface will define all the existing Backend API methods
  // with the following exceptions:
  //
  // search(SearchOperation)
  //
  // hasSubordinates(DN)
  // numSubordinates(DN)
  //
  // addEntry(Entry, AddOperation)
  // deleteEntry(DN, DeleteOperation)
  // replaceEntry(Entry, ModifyOperation)
  // renameEntry(DN, Entry, ModifyDNOperation)

  /**
   * Creates a new backend.
   */
  protected Backend()
  {
    // Protected constructor.
  }



  /**
   * Creates a new backend transaction which will be used to process a
   * transacted set of operations against this backend. The returned
   * backend transaction will have a default configuration.
   *
   * @return A new backend transaction which will be used to process a
   *         transacted set of operations against this backend.
   * @throws DirectoryException
   *           If the backend transaction could not be created.
   */
  public final BackendTxn createBackendTxn() throws DirectoryException
  {
    return createBackendTxn(getDefaultBackendTxnCfg());
  }



  /**
   * Creates a new backend transaction which will be used to process a
   * transacted set of operations against this backend.
   *
   * @param cfg
   *          The configuration to use for the new backend
   *          transaction.
   * @return A new backend transaction which will be used to process a
   *         transacted set of operations against this backend.
   * @throws DirectoryException
   *           If the backend transaction could not be created.
   */
  public abstract BackendTxn createBackendTxn(BackendTxnCfg cfg)
      throws DirectoryException;



  /**
   * Indicates whether an entry exists.
   *
   * @param dn
   *          The name of the entry to make the determination for.
   * @return <code>true</code> if the entry exists.
   * @throws DirectoryException
   *           If a problem occurs while trying to make the
   *           determination.
   */
  public final boolean entryExists(DN dn) throws DirectoryException
  {
    BackendTxn txn = createBackendTxn();
    boolean entryExists = txn.entryExists(dn, LockType.READ_COMMITTED);
    txn.commit();
    return entryExists;
  }



  /**
   * Gets an entry and locks it if requested and if the entry exists.
   *
   * @param dn
   *          The name of the entry to be retrieved and locked.
   * @return The entry, locked if requested, or <code>null</code> if
   *         the entry does not exist.
   * @throws DirectoryException
   *           If a problem occurs while trying to retrieve the entry.
   */
  public final Entry getEntry(DN dn) throws DirectoryException
  {
    BackendTxn txn = createBackendTxn();
    Entry entry = txn.getEntry(dn, LockType.READ_COMMITTED);
    txn.commit();
    return entry;
  }



  /**
   * Returns the default backend transaction configuration for use
   * with this backend.
   *
   * @return The default backend transaction configuration for use
   *         with this backend.
   */
  protected abstract BackendTxnCfg getDefaultBackendTxnCfg();
}
