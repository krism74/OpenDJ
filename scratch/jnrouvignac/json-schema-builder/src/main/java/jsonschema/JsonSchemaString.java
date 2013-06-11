/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.protocols.http.jsonschema;

import static org.forgerock.json.schema.validator.Constants.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A JSON schema used to validate a JSON string.
 */
public class JsonSchemaString extends JsonSchemaAdapter
{

  private String pattern;
  private Integer minLength;
  private Integer maxLength;
  private String[] enumerationValues;

  /**
   * A string instance is considered valid if the regular expression matches the
   * instance successfully. Recall: regular expressions are not implicitly
   * anchored.
   *
   * @param pattern
   *          the regular expression pattern to use for validating this string
   * @return this object
   */
  public JsonSchemaString pattern(String pattern)
  {
    this.pattern = pattern;
    return this;
  }

  /**
   * A string instance is valid against this keyword if its length is greater
   * than, or equal to, the value of this keyword.
   *
   * @param minLength
   *          the minimum length required for this string
   * @return this object
   */
  public JsonSchemaString minLength(int minLength)
  {
    this.minLength = minLength;
    return this;
  }

  /**
   * A string instance is valid against this keyword if its length is less than,
   * or equal to, the value of this keyword.
   *
   * @param maxLength
   *          the maximum length required for this string
   * @return this object
   */
  public JsonSchemaString maxLength(int maxLength)
  {
    this.maxLength = maxLength;
    return this;
  }

  /**
   * @return this object
   */
  public JsonSchema enumeration(String... enumerationValues)
  {
    this.enumerationValues = enumerationValues;
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> toJsonSchema()
  {
    final Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put(TYPE, TYPE_STRING);
    putIfNotNull(result, PATTERN, pattern);
    putIfNotNull(result, MINLENGTH, minLength);
    putIfNotNull(result, MAXLENGTH, maxLength);
    if (this.enumerationValues != null)
    {
      result.put(ENUM, Arrays.asList(this.enumerationValues));
    }
    return result;
  }

}
