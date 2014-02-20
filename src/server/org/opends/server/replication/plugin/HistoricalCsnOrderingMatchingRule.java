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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2014 ForgeRock AS.
 */
package org.opends.server.replication.plugin;

import java.util.Collection;
import java.util.Collections;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteStringBuilder;
import org.forgerock.opendj.ldap.DecodeException;
import org.opends.server.schema.AbstractOrderingMatchingRule;

import static org.opends.messages.ReplicationMessages.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * Used to establish an order between historical information and index them.
 */
public class HistoricalCsnOrderingMatchingRule
       extends AbstractOrderingMatchingRule
{
  /**
   * The serial version identifier required to satisfy the compiler because this
   * class implements the <CODE>java.io.Serializable</CODE> interface.  This
   * value was generated using the <CODE>serialver</CODE> command-line utility
   * included with the Java SDK.
   */
  private static final long serialVersionUID = -3424403930225609943L;



  /**
   * Construct a new  HistoricalCsnOrderingMatchingRule object.
   *
   */
  public HistoricalCsnOrderingMatchingRule()
  {
    super();
  }

  /**
   * Compare two ByteString values containing historical information.
   * @param value1 first value to compare
   * @param value2 second value to compare
   * @return 0 when equals, -1 or 1 to establish order
   */
  @Override
  public int compareValues(ByteSequence value1, ByteSequence value2)
  {
    return value1.compareTo(value2);
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public Collection<String> getNames()
  {
    return Collections.singleton("historicalCsnOrderingMatch");
  }

  /**
   * Get the OID of the class.
   * @return the OID of the class in String form.
   */
  @Override
  public String getOID()
  {
    return "1.3.6.1.4.1.26027.1.4.4";
  }

  /**
   * Get the Syntax OID for this class.
   * @return the syntax OID in String form
   */
  @Override
  public String getSyntaxOID()
  {
    return "1.3.6.1.4.1.1466.115.121.1.40";
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ByteString normalizeAttributeValue(ByteSequence value)
      throws DecodeException
  {
    /*
     * Change the format of the value to index and start with the serverId. In
     * that manner, the search response time is optimized for a particular
     * serverId. The format of the key is now : serverId + timestamp + seqNum
     */
    try
    {
      int csnIndex = value.toString().indexOf(':') + 1;
      String csn = value.subSequence(csnIndex, csnIndex + 28).toString();
      ByteStringBuilder builder = new ByteStringBuilder(14);
      builder.append(hexStringToByteArray(csn.substring(16, 20)));
      builder.append(hexStringToByteArray(csn.substring(0, 16)));
      builder.append(hexStringToByteArray(csn.substring(20, 28)));
      return builder.toByteString();
    }
    catch (Exception e)
    {
      // This should never occur in practice since these attributes are managed
      // internally.
      throw DecodeException.error(WARN_INVALID_SYNC_HIST_VALUE.get(value), e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int compare(byte[] b1, byte[] b2)
  {
    /*
     * See OPENDJ-992: do not use StaticUtils.compare() because it performs
     * unsigned comparisons whereas the 2.4 implementation (below) performs
     * signed comparisons. Changes to indexing comparators require that the
     * index be rebuilt, otherwise the DB can fail unexpectedly.
     */
    int minLength = Math.min(b1.length, b2.length);

    for (int i = 0; i < minLength; i++)
    {
      if (b1[i] == b2[i])
      {
        continue;
      }
      else if (b1[i] < b2[i])
      {
        return -1;
      }
      else if (b1[i] > b2[i])
      {
        return 1;
      }
    }

    if (b1.length == b2.length)
    {
      return 0;
    }
    else if (b1.length < b2.length)
    {
      return -1;
    }
    else
    {
      return 1;
    }
  }

}
