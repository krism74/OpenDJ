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
 *      Portions Copyright 2012-2014 ForgeRock AS
 */
package org.opends.server.schema;



import java.util.HashSet;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import java.util.StringTokenizer;

import org.opends.server.admin.std.server.AttributeSyntaxCfg;
import org.opends.server.api.MatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.forgerock.opendj.ldap.ByteSequence;


import static org.opends.messages.SchemaMessages.*;
import org.forgerock.i18n.LocalizableMessageBuilder;
import static org.opends.server.schema.SchemaConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines the delivery method attribute syntax.  This contains one
 * or more of a fixed set of values.  If there are multiple values, then they
 * are separated by spaces with a dollar sign between them.  The allowed values
 * include:
 *
 * <UL>
 *   <LI>any</LI>
 *   <LI>mhs</LI>
 *   <LI>physical</LI>
 *   <LI>telex</LI>
 *   <LI>teletex</LI>
 *   <LI>g3fax</LI>
 *   <LI>g4fax</LI>
 *   <LI>ia5</LI>
 *   <LI>videotex</LI>
 *   <LI>telephone</LI>
 * </UL>
 */
public class DeliveryMethodSyntax
       extends AttributeSyntax<AttributeSyntaxCfg>
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The set of values that may be used as delivery methods.
   */
  private static final String[] ALLOWED_VALUES =
  {
    "any",
    "mhs",
    "physical",
    "telex",
    "teletex",
    "g3fax",
    "g4fax",
    "ia5",
    "videotex",
    "telephone"
  };



  // The hash set containing the allowed values.
  private HashSet<String> allowedValues;

  // The default approximate matching rule for this syntax.
  private MatchingRule defaultApproximateMatchingRule;

  // The default equality matching rule for this syntax.
  private MatchingRule defaultEqualityMatchingRule;

  // The default ordering matching rule for this syntax.
  private OrderingMatchingRule defaultOrderingMatchingRule;

  // The default substring matching rule for this syntax.
  private SubstringMatchingRule defaultSubstringMatchingRule;



  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public DeliveryMethodSyntax()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  public void initializeSyntax(AttributeSyntaxCfg configuration)
         throws ConfigException
  {
    allowedValues = new HashSet<String>(ALLOWED_VALUES.length);
    for (String s : ALLOWED_VALUES)
    {
      allowedValues.add(s);
    }

    defaultApproximateMatchingRule =
         DirectoryServer.getApproximateMatchingRule(AMR_DOUBLE_METAPHONE_OID);
    if (defaultApproximateMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_APPROXIMATE_MATCHING_RULE,
          AMR_DOUBLE_METAPHONE_OID, SYNTAX_DELIVERY_METHOD_NAME);
    }

    defaultEqualityMatchingRule =
         DirectoryServer.getEqualityMatchingRule(EMR_CASE_IGNORE_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE,
          EMR_CASE_IGNORE_OID, SYNTAX_DELIVERY_METHOD_NAME);
    }

    defaultOrderingMatchingRule =
         DirectoryServer.getOrderingMatchingRule(OMR_CASE_IGNORE_OID);
    if (defaultOrderingMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE,
          OMR_CASE_IGNORE_OID, SYNTAX_DELIVERY_METHOD_NAME);
    }

    defaultSubstringMatchingRule =
         DirectoryServer.getSubstringMatchingRule(SMR_CASE_IGNORE_OID);
    if (defaultSubstringMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE,
          SMR_CASE_IGNORE_OID, SYNTAX_DELIVERY_METHOD_NAME);
    }
  }



  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  public String getName()
  {
    return SYNTAX_DELIVERY_METHOD_NAME;
  }



  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  public String getOID()
  {
    return SYNTAX_DELIVERY_METHOD_OID;
  }



  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  public String getDescription()
  {
    return SYNTAX_DELIVERY_METHOD_DESCRIPTION;
  }



  /**
   * Retrieves the default equality matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default equality matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if equality
   *          matches will not be allowed for this type by default.
   */
  public MatchingRule getEqualityMatchingRule()
  {
    return defaultEqualityMatchingRule;
  }



  /**
   * Retrieves the default ordering matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default ordering matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if ordering
   *          matches will not be allowed for this type by default.
   */
  public OrderingMatchingRule getOrderingMatchingRule()
  {
    return defaultOrderingMatchingRule;
  }



  /**
   * Retrieves the default substring matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default substring matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if substring
   *          matches will not be allowed for this type by default.
   */
  public SubstringMatchingRule getSubstringMatchingRule()
  {
    return defaultSubstringMatchingRule;
  }



  /**
   * Retrieves the default approximate matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default approximate matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if approximate
   *          matches will not be allowed for this type by default.
   */
  public MatchingRule getApproximateMatchingRule()
  {
    return defaultApproximateMatchingRule;
  }



  /**
   * Indicates whether the provided value is acceptable for use in an attribute
   * with this syntax.  If it is not, then the reason may be appended to the
   * provided buffer.
   *
   * @param  value          The value for which to make the determination.
   * @param  invalidReason  The buffer to which the invalid reason should be
   *                        appended.
   *
   * @return  <CODE>true</CODE> if the provided value is acceptable for use with
   *          this syntax, or <CODE>false</CODE> if not.
   */
  public boolean valueIsAcceptable(ByteSequence value,
                                   LocalizableMessageBuilder invalidReason)
  {
    String stringValue = toLowerCase(value.toString());
    StringTokenizer tokenizer = new StringTokenizer(stringValue, " $");
    if (! tokenizer.hasMoreTokens())
    {
      invalidReason.append(ERR_ATTR_SYNTAX_DELIVERY_METHOD_NO_ELEMENTS.get(value));
      return false;
    }

    while (tokenizer.hasMoreTokens())
    {
      String token = tokenizer.nextToken();
      if (! allowedValues.contains(token))
      {
        invalidReason.append(ERR_ATTR_SYNTAX_DELIVERY_METHOD_INVALID_ELEMENT.get(value, token));
        return false;
      }
    }

    return true;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isBEREncodingRequired()
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isHumanReadable()
  {
    return true;
  }
}

