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

import org.forgerock.i18n.slf4j.LocalizedLogger;

import org.opends.server.admin.std.server.AttributeSyntaxCfg;
import org.opends.server.api.ApproximateMatchingRule;
import org.opends.server.api.AttributeSyntax;
import org.opends.server.api.AttributeValueDecoder;
import org.opends.server.api.EqualityMatchingRule;
import org.opends.server.api.OrderingMatchingRule;
import org.opends.server.api.SubstringMatchingRule;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;

import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteSequence;
import static org.opends.messages.SchemaMessages.*;
import org.forgerock.i18n.LocalizableMessageBuilder;
import static org.opends.server.schema.SchemaConstants.*;
import org.opends.server.util.ServerConstants;


/**
 * This class defines the Boolean attribute syntax, which only allows values of
 * "TRUE" or "FALSE" (although this implementation is more flexible and will
 * also allow "YES", "ON", or "1" instead of "TRUE", or "NO", "OFF", or "0"
 * instead of "FALSE").  Only equality matching is allowed by default for this
 * syntax.
 */
public class BooleanSyntax
       extends AttributeSyntax<AttributeSyntaxCfg>
{

  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  // The default equality matching rule for this syntax.
  private EqualityMatchingRule defaultEqualityMatchingRule;



  /**
   * A {@link Boolean} attribute value decoder for this syntax.
   */
  public static final AttributeValueDecoder<Boolean> DECODER =
    new AttributeValueDecoder<Boolean>()
  {
    /**
     * {@inheritDoc}
     */
    public Boolean decode(AttributeValue value) throws DirectoryException
    {
      ByteString normalizedValue = value.getNormalizedValue();
      if (normalizedValue.equals(ServerConstants.TRUE_VALUE))
      {
        return true;
      }
      else if (normalizedValue.equals(ServerConstants.FALSE_VALUE))
      {
        return false;
      }
      else
      {
        throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
            WARN_ATTR_SYNTAX_ILLEGAL_BOOLEAN.get(normalizedValue));
      }
    }
  };



  /**
   * Creates a new instance of this syntax.  Note that the only thing that
   * should be done here is to invoke the default constructor for the
   * superclass.  All initialization should be performed in the
   * <CODE>initializeSyntax</CODE> method.
   */
  public BooleanSyntax()
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
         DirectoryServer.getEqualityMatchingRule(EMR_BOOLEAN_OID);
    if (defaultEqualityMatchingRule == null)
    {
      logger.error(ERR_ATTR_SYNTAX_UNKNOWN_EQUALITY_MATCHING_RULE, EMR_BOOLEAN_OID, SYNTAX_BOOLEAN_NAME);
    }
  }



  /**
   * Retrieves the common name for this attribute syntax.
   *
   * @return  The common name for this attribute syntax.
   */
  public String getSyntaxName()
  {
    return SYNTAX_BOOLEAN_NAME;
  }



  /**
   * Retrieves the OID for this attribute syntax.
   *
   * @return  The OID for this attribute syntax.
   */
  public String getOID()
  {
    return SYNTAX_BOOLEAN_OID;
  }



  /**
   * Retrieves a description for this attribute syntax.
   *
   * @return  A description for this attribute syntax.
   */
  public String getDescription()
  {
    return SYNTAX_BOOLEAN_DESCRIPTION;
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
    // Ordering matches are not allowed by default.
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
    // Substring matches are not allowed by default.
    return null;
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
    // Approximate matches are not allowed by default.
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
    String valueString = value.toString().toUpperCase();

    boolean returnValue = (valueString.equals("TRUE") ||
                           valueString.equals("YES") ||
                           valueString.equals("ON") ||
                           valueString.equals("1") ||
                           valueString.equals("FALSE") ||
                           valueString.equals("NO") ||
                           valueString.equals("OFF") ||
                           valueString.equals("0"));

    if (! returnValue)
    {
      invalidReason.append(WARN_ATTR_SYNTAX_ILLEGAL_BOOLEAN.get(value));
    }

    return returnValue;
  }



  /**
   * Retrieves an attribute value containing a representation of the provided
   * boolean value.
   *
   * @param  b  The boolean value for which to retrieve the attribute value.
   *
   * @return  The attribute value created from the provided boolean value.
   */
  public static AttributeValue createBooleanValue(boolean b)
  {
    ByteString value = b
        ? ServerConstants.TRUE_VALUE
        : ServerConstants.FALSE_VALUE;
    return AttributeValues.create(value, value);
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

