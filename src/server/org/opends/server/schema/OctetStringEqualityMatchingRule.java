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
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.schema;



import java.util.Collection;
import java.util.Collections;

import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DecodeException;
import org.opends.server.api.EqualityMatchingRule;

import static org.opends.server.schema.SchemaConstants.*;



/**
 * This class defines the octetStringMatch matching rule defined in X.520.  It
 * will be used as the default equality matching rule for the binary and octet
 * string syntaxes.
 */
class OctetStringEqualityMatchingRule
       extends EqualityMatchingRule
{
  /**
   * Creates a new instance of this caseExactMatch matching rule.
   */
  public OctetStringEqualityMatchingRule()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public Collection<String> getNames()
  {
    return Collections.singleton(EMR_OCTET_STRING_NAME);
  }


  /**
   * Retrieves the OID for this matching rule.
   *
   * @return  The OID for this matching rule.
   */
  @Override
  public String getOID()
  {
    return EMR_OCTET_STRING_OID;
  }



  /**
   * Retrieves the description for this matching rule.
   *
   * @return  The description for this matching rule, or <CODE>null</CODE> if
   *          there is none.
   */
  @Override
  public String getDescription()
  {
    // There is no standard description for this matching rule.
    return null;
  }



  /**
   * Retrieves the OID of the syntax with which this matching rule is
   * associated.
   *
   * @return  The OID of the syntax with which this matching rule is associated.
   */
  @Override
  public String getSyntaxOID()
  {
    return SYNTAX_OCTET_STRING_OID;
  }



  /**
   * Retrieves the normalized form of the provided value, which is best suited
   * for efficiently performing matching operations on that value.
   *
   * @param  value  The value to be normalized.
   *
   * @return  The normalized version of the provided value.
   *
   * @throws  DecodeException  If the provided value is invalid according to
   *                              the associated attribute syntax.
   */
  @Override
  public ByteString normalizeAttributeValue(ByteSequence value)
         throws DecodeException
  {
    return value.toByteString();
  }
}
