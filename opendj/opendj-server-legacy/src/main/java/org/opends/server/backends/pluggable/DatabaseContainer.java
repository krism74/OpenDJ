/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2015 ForgeRock AS
 */
package org.opends.server.backends.pluggable;

import org.opends.server.backends.pluggable.spi.ReadableTransaction;
import org.opends.server.backends.pluggable.spi.StorageRuntimeException;
import org.opends.server.backends.pluggable.spi.TreeName;
import org.opends.server.backends.pluggable.spi.WriteableTransaction;

/**
 * This class is a wrapper around the tree object and provides basic
 * read and write methods for entries.
 */
interface DatabaseContainer
{
  /**
   * Opens a database in this database container. If the provided database configuration is
   * transactional, a transaction will be created and used to perform the open.
   *
   * @param txn
   *          a non null database transaction
   * @throws StorageRuntimeException
   *           if a database error occurs while opening the index.
   */
  void open(WriteableTransaction txn) throws StorageRuntimeException;

  /**
   * Deletes this database and all of its contents.
   *
   * @param txn
   *          a non null database transaction
   * @throws StorageRuntimeException
   *           if a database error occurs while deleting the index.
   */
  void delete(WriteableTransaction txn) throws StorageRuntimeException;

  /**
   * Returns the number of key/value pairs in this database container.
   *
   * @param txn
   *          a non null database transaction
   * @return the number of key/value pairs in the provided tree.
   * @throws StorageRuntimeException
   *           If an error occurs in the DB operation.
   */
  long getRecordCount(ReadableTransaction txn) throws StorageRuntimeException;

  /**
   * Get the database name for this database container.
   *
   * @return database name for this database container.
   */
  TreeName getName();

  /**
   * Set the database name to use for this container.
   *
   * @param name The database name to use for this container.
   */
  void setName(TreeName name);
}
