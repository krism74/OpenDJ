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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JsonSchemaObject extends JsonSchemaAdapter
{

  private final List<JsonSchemaProperty> properties =
      new ArrayList<JsonSchemaProperty>();
  private final List<JsonSchemaPatternProperty> patternProperties =
      new ArrayList<JsonSchemaPatternProperty>();
  private final List<JsonSchemaDefinition> definitions =
      new ArrayList<JsonSchemaDefinition>();
  private final List<JsonSchema> oneOf = new ArrayList<JsonSchema>();

  public JsonSchemaObject(JsonSchemaPart... parts)
  {
    for (JsonSchemaPart part : parts)
    {
      if (part instanceof JsonSchemaProperty)
      {
        this.properties.add((JsonSchemaProperty) part);
      }
      else if (part instanceof JsonSchemaPatternProperty)
      {
        this.patternProperties.add((JsonSchemaPatternProperty) part);
      }
      else if (part instanceof JsonSchemaDefinition)
      {
        this.definitions.add((JsonSchemaDefinition) part);
      }
      else
      {
        throw new RuntimeException("Not implemented for: "
            + (part != null ? part.getClass() : null));
      }
    }
  }

  /**
   * @return this object
   */
  public JsonSchemaObject oneOf(JsonSchema... oneOfs)
  {
    this.oneOf.addAll(Arrays.asList(oneOfs));
    return this;
  }

  private Set<String> getRequiredProperties()
  {
    return getProperties(true);
  }

  private Set<String> getProperties(boolean onlyRequired)
  {
    final Set<String> keys = new HashSet<String>();
    for (JsonSchemaProperty prop : this.properties)
    {
      if (!onlyRequired || prop.isRequired())
      {
        keys.add(prop.getName());
      }
    }
    return keys;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> toJsonSchema()
  {
    final Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put(TYPE, TYPE_OBJECT);
    put(result, PROPERTIES, this.properties);
    put(result, PATTERNPROPERTIES, this.patternProperties);
    if (!this.oneOf.isEmpty())
    {
      final List<Object> oneOfs = new ArrayList<Object>(this.oneOf.size());
      for (JsonSchema schema : this.oneOf)
      {
        oneOfs.add(schema.toJsonSchema());
      }
      result.put(ONEOF, oneOfs);
    }
    result.put(ADDITIONALPROPERTIES, "false");
    put(result, DEFINITIONS, this.definitions);

    final Set<String> requiredProperties = getRequiredProperties();
    if (!requiredProperties.isEmpty())
    {
      result.put(REQUIRED, new ArrayList<String>(requiredProperties));
    }
    return result;
  }

  private void put(final Map<String, Object> result, String propName,
      List<? extends JsonSchemaPart> properties)
  {
    if (!properties.isEmpty())
    {
      final Map<String, Object> props =
          new LinkedHashMap<String, Object>(properties.size());
      for (JsonSchemaPart prop : properties)
      {
        props.put(prop.getPartName(), prop.toJsonSchema());
      }
      result.put(propName, props);
    }
  }

}
