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
 *      Portions Copyright 2012-2015 ForgeRock AS
 *      Portions Copyright 2012 Manuel Gaupp
 */
package org.opends.server.schema;


import java.util.List;
import org.forgerock.i18n.slf4j.LocalizedLogger;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.CountryStringAttributeSyntaxCfg;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.config.server.ConfigChangeResult;
import static org.opends.messages.SchemaMessages.*;
import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import static org.opends.server.schema.PrintableString.*;
import static org.opends.server.schema.SchemaConstants.*;

/**
 * This class defines the country string attribute syntax, which should be a
 * two-character ISO 3166 country code.  However, for maintainability, it will
 * accept any value consisting entirely of two printable characters.  In most
 * ways, it will behave like the directory string attribute syntax.
 */
public class CountryStringSyntax
       extends AttributeSyntax<CountryStringAttributeSyntaxCfg>
       implements ConfigurationChangeListener<CountryStringAttributeSyntaxCfg>
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The default approximate matching rule for this syntax. */
  private MatchingRule defaultApproximateMatchingRule;

  /** The default equality matching rule for this syntax. */
  private MatchingRule defaultEqualityMatchingRule;

  /** The default ordering matching rule for this syntax. */
  private MatchingRule defaultOrderingMatchingRule;

  /** The default substring matching rule for this syntax. */
  private MatchingRule defaultSubstringMatchingRule;

  /** The current configuration. */
  private volatile CountryStringAttributeSyntaxCfg config;

  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public CountryStringSyntax()
  {
    super();
  }

  /** {@inheritDoc} */
  public void initializeSyntax(CountryStringAttributeSyntaxCfg configuration)
         throws ConfigException
  {
    defaultApproximateMatchingRule =
         DirectoryServer.getMatchingRule(AMR_DOUBLE_METAPHONE_OID);
    if (defaultApproximateMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_APPROXIMATE_MATCHING_RULE,
          AMR_DOUBLE_METAPHONE_OID, SYNTAX_COUNTRY_STRING_NAME);
    }

    defaultEqualityMatchingRule =
         DirectoryServer.getMatchingRule(EMR_CASE_IGNORE_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE,
          EMR_CASE_IGNORE_OID, SYNTAX_COUNTRY_STRING_NAME);
    }

    defaultOrderingMatchingRule =
         DirectoryServer.getMatchingRule(OMR_CASE_IGNORE_OID);
    if (defaultOrderingMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE,
          OMR_CASE_IGNORE_OID, SYNTAX_COUNTRY_STRING_NAME);
    }

    defaultSubstringMatchingRule =
         DirectoryServer.getMatchingRule(SMR_CASE_IGNORE_OID);
    if (defaultSubstringMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE,
          SMR_CASE_IGNORE_OID, SYNTAX_COUNTRY_STRING_NAME);
    }

    this.config = configuration;
    config.addCountryStringChangeListener(this);
  }

  /** {@inheritDoc} */
  public boolean isConfigurationChangeAcceptable(
      CountryStringAttributeSyntaxCfg configuration,
      List<LocalizableMessage> unacceptableReasons)
  {
    // The configuration is always acceptable.
    return true;
  }

  /** {@inheritDoc} */
  public ConfigChangeResult applyConfigurationChange(
      CountryStringAttributeSyntaxCfg configuration)
  {
    this.config = configuration;
    return new ConfigChangeResult();
  }




  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  public String getName()
  {
    return SYNTAX_COUNTRY_STRING_NAME;
  }

  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  public String getOID()
  {
    return SYNTAX_COUNTRY_STRING_OID;
  }

  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  public String getDescription()
  {
    return SYNTAX_COUNTRY_STRING_DESCRIPTION;
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
  public MatchingRule getOrderingMatchingRule()
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
  public MatchingRule getSubstringMatchingRule()
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
    String stringValue = value.toString();
    if (stringValue.length() != 2)
    {
      invalidReason.append(
              ERR_ATTR_SYNTAX_COUNTRY_STRING_INVALID_LENGTH.get(stringValue));
      return false;
    }

    if (config.isStrictFormat())
    {
      // Check for a string containing [A-Z][A-Z]
      if (stringValue.charAt(0) < 'A' || stringValue.charAt(0) > 'Z' ||
          stringValue.charAt(1) < 'A' || stringValue.charAt(1) > 'Z')
        {
          invalidReason.append(ERR_ATTR_SYNTAX_COUNTRY_NO_VALID_ISO_CODE.get(value));
          return false;
        }
    }
    else
    {
      // Just validate as string containing 2 printable characters
      if ((! isPrintableCharacter(stringValue.charAt(0))) ||
          (! isPrintableCharacter(stringValue.charAt(1))))
      {
        invalidReason.append(
                ERR_ATTR_SYNTAX_COUNTRY_STRING_NOT_PRINTABLE.get(stringValue));
        return false;
      }
    }
    return true;
  }

  /** {@inheritDoc} */
  public boolean isBEREncodingRequired()
  {
    return false;
  }

  /** {@inheritDoc} */
  public boolean isHumanReadable()
  {
    return true;
  }
}

