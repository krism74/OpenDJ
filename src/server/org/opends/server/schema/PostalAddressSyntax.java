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



import org.opends.server.admin.std.server.AttributeSyntaxCfg;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.forgerock.opendj.ldap.ByteSequence;


import static org.opends.server.loggers.ErrorLogger.*;
import static org.opends.messages.SchemaMessages.*;
import org.forgerock.i18n.LocalizableMessageBuilder;
import static org.opends.server.schema.SchemaConstants.*;


/**
 * This class implements the postal address attribute syntax, which is a list of
 * UCS (Universal Character Set, as defined in the ISO 10646 specification and
 * includes UTF-8 and UTF-16) strings separated by dollar signs.  By default,
 * they will be treated in a case-insensitive manner, and equality and substring
 * matching will be allowed.
 */
public class PostalAddressSyntax
       extends AttributeSyntax<AttributeSyntaxCfg>
{
  // The default equality matching rule for this syntax.
  private EqualityMatchingRule defaultEqualityMatchingRule;

  // The default substring matching rule for this syntax.
  private SubstringMatchingRule defaultSubstringMatchingRule;



  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public PostalAddressSyntax()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  public void initializeSyntax(AttributeSyntaxCfg configuration)
         throws ConfigException
  {
    defaultEqualityMatchingRule =
         DirectoryServer.getEqualityMatchingRule(EMR_CASE_IGNORE_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logError(ERR_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE.get(
          EMR_CASE_IGNORE_OID, SYNTAX_DIRECTORY_STRING_NAME));
    }

    defaultSubstringMatchingRule =
         DirectoryServer.getSubstringMatchingRule(SMR_CASE_IGNORE_OID);
    if (defaultSubstringMatchingRule == null)
    {
      logError(ERR_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE.get(
          SMR_CASE_IGNORE_OID, SYNTAX_DIRECTORY_STRING_NAME));
    }
  }



  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  public String getSyntaxName()
  {
    return SYNTAX_POSTAL_ADDRESS_NAME;
  }



  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  public String getOID()
  {
    return SYNTAX_POSTAL_ADDRESS_OID;
  }



  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  public String getDescription()
  {
    return SYNTAX_POSTAL_ADDRESS_DESCRIPTION;
  }



  /**
   * Retrieves the default equality matching rule that will be used for
   * attributes with this syntax.
   *
   * @return  The default equality matching rule that will be used for
   *          attributes with this syntax, or <CODE>null</CODE> if equality
   *          matches will not be allowed for this type by default.
   */
  public EqualityMatchingRule getEqualityMatchingRule()
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
    // Ordering matching will not be allowed by default.
    return null;
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
  public ApproximateMatchingRule getApproximateMatchingRule()
  {
    // Approximate matching will not be allowed by default.
    return null;
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
    // We'll allow any value.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isBinary()
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

