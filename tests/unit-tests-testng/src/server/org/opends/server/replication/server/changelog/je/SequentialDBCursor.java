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
 *      Copyright 2013-2014 ForgeRock AS
 */
package org.opends.server.replication.server.changelog.je;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.opends.server.replication.protocol.UpdateMsg;
import org.opends.server.replication.server.changelog.api.DBCursor;

@SuppressWarnings("javadoc")
class SequentialDBCursor implements DBCursor<UpdateMsg>
{

  private final List<UpdateMsg> msgs;
  private UpdateMsg current;

  public SequentialDBCursor(UpdateMsg... msgs)
  {
    this.msgs = new ArrayList<UpdateMsg>(Arrays.asList(msgs));
    next();
  }

  public void add(UpdateMsg msg)
  {
    this.msgs.add(msg);
  }

  /** {@inheritDoc} */
  @Override
  public UpdateMsg getRecord()
  {
    return current;
  }

  /** {@inheritDoc} */
  @Override
  public boolean next()
  {
    if (!msgs.isEmpty())
    {
      current = msgs.remove(0);
      return current != null;
    }
    current = null;
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void close()
  {
    // nothing to do
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
		return getClass().getSimpleName() + "(currentRecord=" + current
				+ " nextMessages=" + msgs + ")";
  }

}