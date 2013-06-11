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

public class JsonSchemaArray extends JsonSchemaAdapter
{

  private final JsonSchema schema;
  private Integer minItems;
  private Integer maxItems;
  private Boolean uniqueItems;

  public JsonSchemaArray(JsonSchema type)
  {
    this.schema = type;
  }

  /**
   * @return this object
   */
  public JsonSchemaArray min(int minItems)
  {
    this.minItems = minItems;
    return this;
  }

  /**
   * @return this object
   */
  public JsonSchemaArray max(int maxItems)
  {
    this.maxItems = maxItems;
    return this;
  }

  /**
   * @return this object
   */
  public JsonSchemaArray uniqueItems(boolean uniqueItems)
  {
    this.uniqueItems = uniqueItems;
    return this;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> toJsonSchema()
  {
    final Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put(TYPE, TYPE_ARRAY);
    putIfNotNull(result, MINITEMS, minItems);
    putIfNotNull(result, MAXITEMS, maxItems);
    putIfNotNull(result, UNIQUEITEMS, uniqueItems);
    if (this.schema != null)
    {
      result.put(ITEMS, this.schema.toJsonSchema());
    }
    return result;
  }

}
