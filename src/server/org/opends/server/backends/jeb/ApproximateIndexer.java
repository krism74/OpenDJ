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
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.backends.jeb;

import java.util.Collection;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.spi.IndexingOptions;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.ExtensibleIndexer;
import org.opends.server.types.AttributeType;

/**
 * An implementation of an Indexer for attribute approximate matching.
 */
public class ApproximateIndexer extends ExtensibleIndexer
{

  /**
   * The attribute type approximate matching rule.
   */
  private ApproximateMatchingRule approximateRule;

  /**
   * Create a new attribute approximate indexer for the given index
   * configuration.
   * @param attributeType The attribute type for which an indexer is
   * required.
   */
  public ApproximateIndexer(AttributeType attributeType)
  {
    this.approximateRule = attributeType.getApproximateMatchingRule();
  }

  /** {@inheritDoc} */
  @Override
  public String getIndexID()
  {
    // TODO Auto-generated method stub
    throw new RuntimeException();
  }

  /** {@inheritDoc} */
  @Override
  public String getExtensibleIndexID()
  {
    return "approximate";
  }

  /** {@inheritDoc} */
  @Override
  public void createKeys(Schema schema, ByteSequence value,
      IndexingOptions options, Collection<ByteString> keys)
      throws DecodeException
  {
    keys.add(approximateRule.normalizeAttributeValue(value));
  }

}
