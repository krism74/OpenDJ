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
 *      Copyright 2008 Sun Microsystems, Inc.
 *      Portions copyright 2013 ForgeRock AS.
 */

package org.opends.server.admin;



import static org.opends.messages.AdminMessages.*;

import org.opends.messages.Message;



/**
 * Thrown to indicate that a property value string was invalid
 * according to its associated property definition.
 */
public class IllegalPropertyValueStringException extends PropertyException {

  /**
   * Serialization ID.
   */
  private static final long serialVersionUID = -3145632074909281823L;

  // The illegal property value string.
  private final String value;



  /**
   * Create a new illegal property value string exception.
   *
   * @param pd
   *          The property definition.
   * @param value
   *          The illegal property value string.
   */
  public IllegalPropertyValueStringException(PropertyDefinition<?> pd,
      String value) {
    super(pd, createMessage(pd, value));
    this.value = value;
  }



  /**
   * Create a new illegal property value string exception.
   *
   * @param pd
   *          The property definition.
   * @param value
   *          The illegal property value string.
   * @param cause
   *          The cause.
   */
  public IllegalPropertyValueStringException(PropertyDefinition<?> pd,
      String value, Throwable cause) {
    super(pd, createMessage(pd, value), cause);
    this.value = value;
  }



  /**
   * Get the illegal property value string that caused the exception.
   *
   * @return Returns the illegal property value string.
   */
  public final String getIllegalValueString() {
    return value;
  }



  // Create the message.
  private static Message createMessage(PropertyDefinition<?> pd, String value) {
    PropertyDefinitionUsageBuilder builder = new PropertyDefinitionUsageBuilder(
        true);
    return ERR_ILLEGAL_PROPERTY_VALUE_STRING_EXCEPTION.get(value, pd.getName(),
        builder.getUsage(pd));
  }

}
