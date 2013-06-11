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

public class JsonSchemaForwardDecl extends JsonSchemaAdapter
{

  private JsonSchema delayedSchema;

  /**
   * Sets the actual schema corresponding to this forward declaration.
   */
  public void define(JsonSchemaDefinitionReference reference)
  {
    if (this.delayedSchema != null)
    {
      throw new IllegalArgumentException(
          "Cannot define twice a foward declaration. Current definition is: "
              + this.delayedSchema);
    }
    this.delayedSchema = reference;
  }

  /** {@inheritDoc} */
  @Override
  public Object toJsonSchema()
  {
    if (this.delayedSchema != null)
    {
      return this.delayedSchema.toJsonSchema();
    }
    return Collections.emptyMap();
  }

}
