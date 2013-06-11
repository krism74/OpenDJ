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

/**
 * A JSON schema part used to define a JSON object property whose name must
 * match a regular expression pattern.
 */
public class JsonSchemaPatternProperty implements JsonSchemaPart
{

  private final String pattern;
  private final boolean required;
  private final JsonSchema schema;

  /**
   * Default ctor.
   *
   * @param name
   *          the property name
   * @param required
   *          whether the property is required
   * @param schema
   *          the schema of the property
   */
  public JsonSchemaPatternProperty(String name, boolean required,
      JsonSchema schema)
  {
    this.pattern = name;
    this.required = required;
    this.schema = schema;
  }

  /** {@inheritDoc} */
  @Override
  public String getPartName()
  {
    return getPattern();
  }

  /**
   * @return the name
   */
  public String getPattern()
  {
    return pattern;
  }

  /**
   * @return the required
   */
  public boolean isRequired()
  {
    return required;
  }

  /** {@inheritDoc} */
  @Override
  public Object toJsonSchema()
  {
    return this.schema.toJsonSchema();
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    return "\"" + pattern.replaceAll("\\\\", "\\\\\\\\") + "\" : "
        + this.schema;
  }

}
