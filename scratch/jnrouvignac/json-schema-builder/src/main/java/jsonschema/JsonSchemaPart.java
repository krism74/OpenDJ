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

import java.util.List;
import java.util.Map;

/**
 * This interface represents any element suitable to use as a part/fragment of a
 * JSON schema.
 */
public interface JsonSchemaPart
{

  /**
   * Returns the name of this schema part.
   *
   * @return the name of this schema part
   */
  String getPartName();

  /**
   * Returns an object representing this schema part suitable to use for
   * validation with json-schema. The object is made of {@link Map},
   * {@link List}, {@link String}, {@link Boolean} and {@link Integer}.
   *
   * @return the object representation of this schema part
   */
  Object toJsonSchema();

}
