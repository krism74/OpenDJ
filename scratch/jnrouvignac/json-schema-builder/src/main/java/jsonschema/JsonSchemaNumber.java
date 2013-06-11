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

import java.util.LinkedHashMap;
import java.util.Map;

public class JsonSchemaNumber extends JsonSchemaAdapter
{

  private Integer minimum;
  private Integer maximum;
  private Boolean exclusiveMinimum;
  private Boolean exclusiveMaximum;

  /**
   * @return this object
   */
  public JsonSchemaNumber minimum(int minimum)
  {
    this.minimum = minimum;
    return this;
  }

  /**
   * @return this object
   */
  public JsonSchemaNumber maximum(int maximum)
  {
    this.maximum = maximum;
    return this;
  }

  /**
   * @return this object
   */
  public JsonSchemaNumber exclusiveMinimum(boolean exclusiveMinimum)
  {
    this.exclusiveMinimum = exclusiveMinimum;
    return this;
  }

  /**
   * @return this object
   */
  public JsonSchemaNumber exclusiveMaximum(boolean exclusiveMaximum)
  {
    this.exclusiveMaximum = exclusiveMaximum;
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> toJsonSchema()
  {
    final Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put(TYPE, TYPE_NUMBER);
    putIfNotNull(result, MINIMUM, minimum);
    putIfNotNull(result, MAXIMUM, maximum);
    putIfNotNull(result, EXCLUSIVEMINIMUM, exclusiveMinimum);
    putIfNotNull(result, EXCLUSIVEMAXIMUM, exclusiveMaximum);
    return result;
  }

}
