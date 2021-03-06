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
 *
 */
package org.opends.server.schema;

import static org.opends.messages.SchemaMessages.*;
import static org.opends.server.schema.SchemaConstants.*;

import java.util.List;
import org.forgerock.i18n.slf4j.LocalizedLogger;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.JPEGAttributeSyntaxCfg;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.config.server.ConfigChangeResult;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;

/**
 * This class implements the JPEG attribute syntax.  This is actually
 * two specifications - JPEG and JFIF. As an extension we allow JPEG
 * and Exif, which is what most digital cameras use. We only check for
 * valid JFIF and Exif headers.
 */
public class JPEGSyntax
       extends AttributeSyntax<JPEGAttributeSyntaxCfg>
       implements ConfigurationChangeListener<JPEGAttributeSyntaxCfg>
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** The default equality matching rule for this syntax. */
  private MatchingRule defaultEqualityMatchingRule;

  /** The default ordering matching rule for this syntax. */
  private MatchingRule defaultOrderingMatchingRule;

  /** The default substring matching rule for this syntax. */
  private MatchingRule defaultSubstringMatchingRule;

  /** The current configuration for this JPEG syntax. */
  private volatile JPEGAttributeSyntaxCfg config;

  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public JPEGSyntax()
  {
    super();
  }

  /** {@inheritDoc} */
  public void initializeSyntax(JPEGAttributeSyntaxCfg configuration)
         throws ConfigException
  {
    defaultEqualityMatchingRule =
         DirectoryServer.getMatchingRule(EMR_OCTET_STRING_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE, EMR_OCTET_STRING_OID, SYNTAX_JPEG_NAME);
    }

    defaultOrderingMatchingRule =
         DirectoryServer.getMatchingRule(OMR_OCTET_STRING_OID);
    if (defaultOrderingMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_ORDERING_MATCHING_RULE, OMR_OCTET_STRING_OID, SYNTAX_JPEG_NAME);
    }

    defaultSubstringMatchingRule =
         DirectoryServer.getMatchingRule(SMR_OCTET_STRING_OID);
    if (defaultSubstringMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_SUBSTRING_MATCHING_RULE, SMR_OCTET_STRING_OID, SYNTAX_JPEG_NAME);
    }

    this.config = configuration;
    config.addJPEGChangeListener(this);
  }

  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  public String getName()
  {
    return SYNTAX_JPEG_NAME;
  }

  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  public String getOID()
  {
    return SYNTAX_JPEG_OID;
  }

  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  public String getDescription()
  {
    return SYNTAX_JPEG_DESCRIPTION;
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
    // There is no approximate matching rule by default.
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
    // anything is acceptable if we're not strict.
    if (!config.isStrictFormat())
        return true;

    /* JFIF files start:
     * 0xff 0xd8 0xff 0xe0 LH LL 0x4a 0x46 0x49 0x46 ...
     * SOI       APP0      len   "JFIF"
     *
     * Exif files (from most digital cameras) start:
     * 0xff 0xd8 0xff 0xe1 LH LL 0x45 0x78 0x69 0x66 ...
     * SOI       APP1      len   "Exif"
     *
     * So all legal values must be at least 10 bytes long
     */
    if (value.length() < 10)
        return false;

    if (value.byteAt(0) != (byte)0xff && value.byteAt(1) != (byte)0xd8)
        return false;

    if (value.byteAt(2) == (byte)0xff && value.byteAt(3) == (byte)0xe0 &&
        value.byteAt(6) == 'J' && value.byteAt(7) == 'F' &&
        value.byteAt(8) == 'I' && value.byteAt(9) == 'F')
        return true;

    if (value.byteAt(2) == (byte)0xff && value.byteAt(3) == (byte)0xe1 &&
        value.byteAt(6) == 'E' && value.byteAt(7) == 'x' &&
        value.byteAt(8) == 'i' && value.byteAt(9) == 'f')
        return true;

    // No JFIF or Exif header found
    return false;
  }


  /** {@inheritDoc} */
  public boolean isConfigurationChangeAcceptable(
                      JPEGAttributeSyntaxCfg configuration,
                      List<LocalizableMessage> unacceptableReasons)
  {
    // The configuration will always be acceptable.
    return true;
  }

  /** {@inheritDoc} */
  public ConfigChangeResult applyConfigurationChange(
              JPEGAttributeSyntaxCfg configuration)
  {
    this.config = configuration;
    return new ConfigChangeResult();
  }


  /** {@inheritDoc} */
  public boolean isBEREncodingRequired()
  {
    return false;
  }

  /** {@inheritDoc} */
  public boolean isHumanReadable()
  {
    return false;
  }
}

