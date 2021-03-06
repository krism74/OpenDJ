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
 *      Copyright 2009 Sun Microsystems, Inc.
 *      Portions copyright 2012-2013 ForgeRock AS
 */
package org.opends.server.replication.plugin;

import org.opends.server.replication.common.CSN;
import org.opends.server.replication.protocol.DeleteMsg;
import org.opends.server.replication.protocol.ReplicationMsg;
import org.opends.server.types.DN;

/**
 * This class if used to build pseudo DEL Operation from the historical
 * information that stay in the entry in the database.
 *
 * This is useful when a LDAP server can't find a LDAP server that
 * has already seen all its changes and therefore need to retransmit them.
 */
public class FakeDelOperation extends FakeOperation
{
  private final DN dn;
  private final String entryUUID;

  /**
   * Creates a new FakeDelOperation from the provided information.
   *
   * @param dn             The dn of the entry that was deleted.
   * @param csn   The CSN of the operation.
   * @param entryUUID      The Unique ID of the deleted entry.
   */
  public FakeDelOperation(DN dn, CSN csn, String entryUUID)
  {
    super(csn);
    this.dn = dn;
    this.entryUUID = entryUUID;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public ReplicationMsg generateMessage()
  {
    return new DeleteMsg(dn, getCSN(), entryUUID);
  }

  /**
   * Retrieves the Unique ID of the entry that was deleted with this operation.
   *
   * @return  The Unique ID of the entry that was deleted with this operation.
   */
  public String getEntryUUID()
  {
    return entryUUID;
  }
}
