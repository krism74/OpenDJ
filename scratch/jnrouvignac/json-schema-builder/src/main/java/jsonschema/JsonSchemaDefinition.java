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

import java.util.Collections;

/**
 * A JSON schema part used to define another embedded schema.
 */
public class JsonSchemaDefinition implements JsonSchemaPart
{

  private final String name;
  private final JsonSchema schema;

  /**
   * Default ctor.
   *
   * @param name
   *          the property name
   * @param schema
   *          the schema of the property
   */
  public JsonSchemaDefinition(String name, JsonSchema schema)
  {
    this.name = name;
    this.schema = schema;
  }

  /** {@inheritDoc} */
  @Override
  public String getPartName()
  {
    return getName();
  }

  /**
   * @return the name
   */
  public String getName()
  {
    return name;
  }

  /**
   * Returns a reference to this schema definition.
   *
   * @return a reference to this schema definition
   */
  public JsonSchemaDefinitionReference reference()
  {
    return new JsonSchemaDefinitionReference(this);
  }

  /** {@inheritDoc} */
  @Override
  public Object toJsonSchema()
  {
    if (this.schema != null)
    {
      return this.schema.toJsonSchema();
    }
    return Collections.emptyMap();
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return "\"" + name + "\" : " + this.schema;
  }

}
