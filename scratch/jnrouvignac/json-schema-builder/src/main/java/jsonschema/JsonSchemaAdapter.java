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

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.ObjectMapper;

/**
 * Adapter/helper class for the {@link JsonSchema} interface offering some
 * default functionality.
 */
public abstract class JsonSchemaAdapter implements JsonSchema
{

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  /**
   * Puts the key and value in the map if none is null.
   *
   * @param map
   *          the map where to put the entry
   * @param key
   *          the key to put in the map
   * @param value
   *          the value to put in the map
   */
  protected void putIfNotNull(Map<String, Object> map, String key,
      Object value)
  {
    if (key != null && value != null)
    {
      map.put(key, value);
    }
  }

  /** {@inheritDoc} */
  @Override
  public String toString()
  {
    try
    {
      final StringWriter writer = new StringWriter();
      // final PrintWriter writer = new PrintWriter(System.out);
      final JsonGenerator generator =
          JSON_MAPPER.getJsonFactory().createJsonGenerator(writer)
              .useDefaultPrettyPrinter();
      write(generator, null, toJsonSchema(), "");
      generator.close();
      return writer.toString();
    }
    catch (IOException e)
    {
      return e.toString();
    }
  }

  @SuppressWarnings("unchecked")
  private void write(JsonGenerator generator, String fieldName, Object pojo,
      String jsonPointer) throws IOException, JsonGenerationException
  {
    if (fieldName != null)
    {
      generator.writeFieldName(fieldName);
      jsonPointer = jsonPointer + "/" + fieldName;
    }

    if (pojo instanceof Map)
    {
      generator.writeStartObject();
      for (Entry<String, Object> entry : ((Map<String, Object>) pojo)
          .entrySet())
      {
        write(generator, entry.getKey(), entry.getValue(), jsonPointer);
      }
      generator.writeEndObject();
    }
    else if (pojo instanceof Collection)
    {
      generator.writeStartArray();
      for (Object obj : (Collection<?>) pojo)
      {
        write(generator, null, obj, jsonPointer);
      }
      generator.writeEndArray();
    }
    else if (pojo instanceof String)
    {
      generator.writeString((String) pojo);
    }
    else if (pojo instanceof Boolean)
    {
      generator.writeBoolean((Boolean) pojo);
    }
    else if (pojo instanceof Integer)
    {
      generator.writeNumber((Integer) pojo);
    }
    else
    {
      throw new RuntimeException("Not implemented for "
          + (pojo != null ? pojo.getClass() : null));
    }
  }

}
