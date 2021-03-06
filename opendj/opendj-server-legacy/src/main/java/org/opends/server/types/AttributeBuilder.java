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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2012-2015 ForgeRock AS
 */
package org.opends.server.types;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.Assertion;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ConditionResult;
import org.forgerock.opendj.ldap.DecodeException;
import org.forgerock.util.Reject;
import org.forgerock.util.Utils;
import org.forgerock.opendj.ldap.schema.MatchingRule;
import org.opends.server.core.DirectoryServer;

import static org.opends.server.util.StaticUtils.*;

/**
 * This class provides an interface for creating new non-virtual
 * {@link Attribute}s, or "real" attributes.
 * <p>
 * An attribute can be created incrementally using either
 * {@link #AttributeBuilder(AttributeType)} or
 * {@link #AttributeBuilder(AttributeType, String)}. The caller is
 * then free to add new options using {@link #setOption(String)} and
 * new values using {@link #add(ByteString)} or
 * {@link #addAll(Collection)}. Once all the options and values have
 * been added, the attribute can be retrieved using the
 * {@link #toAttribute()} method.
 * <p>
 * A real attribute can also be created based on the values taken from
 * another attribute using the {@link #AttributeBuilder(Attribute)}
 * constructor. The caller is then free to modify the values within
 * the attribute before retrieving the updated attribute using the
 * {@link #toAttribute()} method.
 * <p>
 * The {@link org.opends.server.types.Attributes} class contains
 * convenience factory methods,
 * e.g. {@link org.opends.server.types.Attributes#empty(String)} for
 * creating empty attributes, and
 * {@link org.opends.server.types.Attributes#create(String, String)}
 * for  creating single-valued attributes.
 * <p>
 * <code>AttributeBuilder</code>s can be re-used. Once an
 * <code>AttributeBuilder</code> has been converted to an
 * {@link Attribute} using {@link #toAttribute()}, its state is reset
 * so that its attribute type, user-provided name, options, and values
 * are all undefined:
 *
 * <pre>
 * AttributeBuilder builder = new AttributeBuilder();
 * for (int i = 0; i &lt; 10; i++)
 * {
 *   builder.setAttributeType(&quot;myAttribute&quot; + i);
 *   builder.setOption(&quot;an-option&quot;);
 *   builder.add(&quot;a value&quot;);
 *   Attribute attribute = builder.toAttribute();
 *   // Do something with attribute...
 * }
 * </pre>
 * <p>
 * <b>Implementation Note:</b> this class is optimized for the common
 * case where there is only a single value. By doing so, we avoid
 * using unnecessary storage space and also performing any unnecessary
 * normalization. In addition, this class is optimized for the common
 * cases where there are zero or one attribute type options.
 */
@org.opends.server.types.PublicAPI(
    stability = org.opends.server.types.StabilityLevel.UNCOMMITTED,
    mayInstantiate = true,
    mayExtend = false,
    mayInvoke = true)
public final class AttributeBuilder
  implements Iterable<ByteString>
{

  /**
   * A real attribute - options handled by sub-classes.
   */
  private static abstract class RealAttribute
    extends AbstractAttribute
  {
    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

    /** The attribute type for this attribute. */
    private final AttributeType attributeType;

    /** The name of this attribute as provided by the end user. */
    private final String name;

    /**
     * The unmodifiable map of values for this attribute. The key is the
     * attribute value normalized according to the equality matching rule and
     * the value is the non normalized value.
     */
    private final Map<ByteString, ByteString> values;



    /**
     * Creates a new real attribute.
     *
     * @param attributeType
     *          The attribute type.
     * @param name
     *          The user-provided attribute name.
     * @param values
     *          The attribute values.
     */
    private RealAttribute(
        AttributeType attributeType,
        String name,
        Map<ByteString, ByteString> values)
    {
      this.attributeType = attributeType;
      this.name = name;
      this.values = values;
    }


    /** {@inheritDoc} */
    @Override
    public final ConditionResult approximatelyEqualTo(ByteString assertionValue)
    {
      MatchingRule matchingRule = attributeType.getApproximateMatchingRule();
      if (matchingRule == null)
      {
        return ConditionResult.UNDEFINED;
      }

      Assertion assertion = null;
      try
      {
        assertion = matchingRule.getAssertion(assertionValue);
      }
      catch (Exception e)
      {
        logger.traceException(e);
        return ConditionResult.UNDEFINED;
      }

      ConditionResult result = ConditionResult.FALSE;
      for (ByteString v : values.values())
      {
        try
        {
          result = assertion.matches(matchingRule.normalizeAttributeValue(v));
        }
        catch (Exception e)
        {
          logger.traceException(e);
          // We couldn't normalize one of the attribute values. If we
          // can't find a definite match, then we should return
          // "undefined".
          result = ConditionResult.UNDEFINED;
        }
      }

      return result;
    }



    /** {@inheritDoc} */
    @Override
    public final boolean contains(ByteString value)
    {
      return values.containsKey(normalize(attributeType, value));
    }

    /** {@inheritDoc} */
    @Override
    public ConditionResult matchesEqualityAssertion(ByteString assertionValue)
    {
      try
      {
        MatchingRule eqRule = getAttributeType().getEqualityMatchingRule();
        final Assertion assertion = eqRule.getAssertion(assertionValue);
        for (ByteString normalizedValue : values.keySet())
        {
          if (assertion.matches(normalizedValue).toBoolean())
          {
            return ConditionResult.TRUE;
          }
        }
        return ConditionResult.FALSE;
      }
      catch (DecodeException e)
      {
        return ConditionResult.UNDEFINED;
      }
    }

    /** {@inheritDoc} */
    @Override
    public final AttributeType getAttributeType()
    {
      return attributeType;
    }



    /** {@inheritDoc} */
    @Override
    public final String getName()
    {
      return name;
    }



    /** {@inheritDoc} */
    @Override
    public final ConditionResult greaterThanOrEqualTo(ByteString assertionValue)
    {
      MatchingRule matchingRule = attributeType.getOrderingMatchingRule();
      if (matchingRule == null)
      {
        return ConditionResult.UNDEFINED;
      }

      Assertion assertion;
      try
      {
        assertion = matchingRule.getGreaterOrEqualAssertion(assertionValue);
      }
      catch (DecodeException e)
      {
        logger.traceException(e);
        return ConditionResult.UNDEFINED;
      }

      ConditionResult result = ConditionResult.FALSE;
      for (ByteString v : values.values())
      {
        try
        {
          if (assertion.matches(matchingRule.normalizeAttributeValue(v)).toBoolean())
          {
            return ConditionResult.TRUE;
          }
        }
        catch (Exception e)
        {
          logger.traceException(e);
          // We couldn't normalize one of the attribute values. If we
          // can't find a definite match, then we should return "undefined".
          result = ConditionResult.UNDEFINED;
        }
      }

      return result;
    }



    /** {@inheritDoc} */
    @Override
    public final boolean isVirtual()
    {
      return false;
    }



    /** {@inheritDoc} */
    @Override
    public final Iterator<ByteString> iterator()
    {
      return values.values().iterator();
    }



    /** {@inheritDoc} */
    @Override
    public final ConditionResult lessThanOrEqualTo(ByteString assertionValue)
    {
      MatchingRule matchingRule = attributeType.getOrderingMatchingRule();
      if (matchingRule == null)
      {
        return ConditionResult.UNDEFINED;
      }

      Assertion assertion;
      try
      {
        assertion = matchingRule.getLessOrEqualAssertion(assertionValue);
      }
      catch (DecodeException e)
      {
        logger.traceException(e);
        return ConditionResult.UNDEFINED;
      }

      ConditionResult result = ConditionResult.FALSE;
      for (ByteString v : values.values())
      {
        try
        {
          if (assertion.matches(matchingRule.normalizeAttributeValue(v)).toBoolean())
          {
            return ConditionResult.TRUE;
          }
        }
        catch (Exception e)
        {
          logger.traceException(e);

          // We couldn't normalize one of the attribute values. If we
          // can't find a definite match, then we should return "undefined".
          result = ConditionResult.UNDEFINED;
        }
      }

      return result;
    }



    /** {@inheritDoc} */
    @Override
    public final ConditionResult matchesSubstring(
        ByteString subInitial,
        List<ByteString> subAny, ByteString subFinal)
    {
      MatchingRule matchingRule = attributeType.getSubstringMatchingRule();
      if (matchingRule == null)
      {
        return ConditionResult.UNDEFINED;
      }


      Assertion assertion;
      try
      {
        assertion = matchingRule.getSubstringAssertion(subInitial, subAny, subFinal);
      }
      catch (DecodeException e)
      {
        logger.traceException(e);
        return ConditionResult.UNDEFINED;
      }

      ConditionResult result = ConditionResult.FALSE;
      for (ByteString value : values.values())
      {
        try
        {
          if (assertion.matches(matchingRule.normalizeAttributeValue(value)).toBoolean())
          {
            return ConditionResult.TRUE;
          }
        }
        catch (Exception e)
        {
          logger.traceException(e);

          // The value couldn't be normalized. If we can't find a
          // definite match, then we should return "undefined".
          result = ConditionResult.UNDEFINED;
        }
      }

      return result;
    }



    /** {@inheritDoc} */
    @Override
    public final int size()
    {
      return values.size();
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode()
    {
      int hashCode = getAttributeType().hashCode();
      for (ByteString value : values.keySet())
      {
        hashCode += value.hashCode();
      }
      return hashCode;
    }

    /** {@inheritDoc} */
    @Override
    public final void toString(StringBuilder buffer)
    {
      buffer.append("Attribute(");
      buffer.append(getNameWithOptions());
      buffer.append(", {");
      buffer.append(Utils.joinAsString(", ", values.keySet()));
      buffer.append("})");
    }

  }



  /**
   * A real attribute with a many options.
   */
  private static final class RealAttributeManyOptions
    extends RealAttribute
  {

    /** The normalized options. */
    private final SortedSet<String> normalizedOptions;

    /** The options. */
    private final Set<String> options;



    /**
     * Creates a new real attribute that has multiple options.
     *
     * @param attributeType
     *          The attribute type.
     * @param name
     *          The user-provided attribute name.
     * @param values
     *          The attribute values.
     * @param options
     *          The attribute options.
     * @param normalizedOptions
     *          The normalized attribute options.
     */
    private RealAttributeManyOptions(
        AttributeType attributeType,
        String name,
        Map<ByteString, ByteString> values,
        Set<String> options,
        SortedSet<String> normalizedOptions)
    {
      super(attributeType, name, values);
      this.options = options;
      this.normalizedOptions = normalizedOptions;
    }



    /** {@inheritDoc} */
    @Override
    public Set<String> getOptions()
    {
      return options;
    }



    /** {@inheritDoc} */
    @Override
    public boolean hasOption(String option)
    {
      String s = toLowerCase(option);
      return normalizedOptions.contains(s);
    }



    /** {@inheritDoc} */
    @Override
    public boolean hasOptions()
    {
      return true;
    }

  }



  /**
   * A real attribute with no options.
   */
  private static final class RealAttributeNoOptions
    extends RealAttribute
  {

    /**
     * Creates a new real attribute that has no options.
     *
     * @param attributeType
     *          The attribute type.
     * @param name
     *          The user-provided attribute name.
     * @param values
     *          The attribute values.
     */
    private RealAttributeNoOptions(
        AttributeType attributeType,
        String name,
        Map<ByteString, ByteString> values)
    {
      super(attributeType, name, values);
    }



    /** {@inheritDoc} */
    @Override
    public String getNameWithOptions()
    {
      return getName();
    }



    /** {@inheritDoc} */
    @Override
    public Set<String> getOptions()
    {
      return Collections.emptySet();
    }



    /** {@inheritDoc} */
    @Override
    public boolean hasAllOptions(Collection<String> options)
    {
      return (options == null || options.isEmpty());
    }



    /** {@inheritDoc} */
    @Override
    public boolean hasOption(String option)
    {
      return false;
    }



    /** {@inheritDoc} */
    @Override
    public boolean hasOptions()
    {
      return false;
    }



    /** {@inheritDoc} */
    @Override
    public boolean optionsEqual(Set<String> options)
    {
      return (options == null || options.isEmpty());
    }

  }



  /**
   * A real attribute with a single option.
   */
  private static final class RealAttributeSingleOption
    extends RealAttribute
  {

    /** The normalized single option. */
    private final String normalizedOption;

    /** A singleton set containing the single option. */
    private final Set<String> option;



    /**
     * Creates a new real attribute that has a single option.
     *
     * @param attributeType
     *          The attribute type.
     * @param name
     *          The user-provided attribute name.
     * @param values
     *          The attribute values.
     * @param option
     *          The attribute option.
     */
    private RealAttributeSingleOption(
        AttributeType attributeType,
        String name,
        Map<ByteString, ByteString> values,
        String option)
    {
      super(attributeType, name, values);
      this.option = Collections.singleton(option);
      this.normalizedOption = toLowerCase(option);
    }



    /** {@inheritDoc} */
    @Override
    public Set<String> getOptions()
    {
      return option;
    }



    /** {@inheritDoc} */
    @Override
    public boolean hasOption(String option)
    {
      String s = toLowerCase(option);
      return normalizedOption.equals(s);
    }



    /** {@inheritDoc} */
    @Override
    public boolean hasOptions()
    {
      return true;
    }

  }



  /**
   * A small set of values. This set implementation is optimized to
   * use as little memory as possible in the case where there zero or
   * one elements. In addition, any normalization of elements is
   * delayed until the second element is added (normalization may be
   * triggered by invoking {@link Object#hashCode()} or
   * {@link Object#equals(Object)}.
   *
   * @param <T>
   *          The type of elements to be contained in this small set.
   */
  private static final class SmallSet<T>
    extends AbstractSet<T>
  {

    /** The set of elements if there are more than one. */
    private LinkedHashSet<T> elements;

    /** The first element. */
    private T firstElement;



    /**
     * Creates a new small set which is initially empty.
     */
    public SmallSet()
    {
      // No implementation required.
    }


    /** {@inheritDoc} */
    @Override
    public boolean add(T e)
    {
      // Special handling for the first value. This avoids potentially
      // expensive normalization.
      if (firstElement == null && elements == null)
      {
        firstElement = e;
        return true;
      }

      // Create the value set if necessary.
      if (elements == null)
      {
        if (firstElement.equals(e))
        {
          return false;
        }

        elements = new LinkedHashSet<T>(2);

        // Move the first value into the set.
        elements.add(firstElement);
        firstElement = null;
      }

      return elements.add(e);
    }



    /** {@inheritDoc} */
    @Override
    public boolean addAll(Collection<? extends T> c)
    {
      if (elements != null)
      {
        return elements.addAll(c);
      }

      if (firstElement != null)
      {
        elements = new LinkedHashSet<T>(1 + c.size());
        elements.add(firstElement);
        firstElement = null;
        return elements.addAll(c);
      }

      // Initially empty.
      switch (c.size())
      {
      case 0:
        // Do nothing.
        return false;
      case 1:
        firstElement = c.iterator().next();
        return true;
      default:
        elements = new LinkedHashSet<T>(c);
        return true;
      }
    }



    /** {@inheritDoc} */
    @Override
    public void clear()
    {
      firstElement = null;
      elements = null;
    }



    /** {@inheritDoc} */
    @Override
    public Iterator<T> iterator()
    {
      if (elements != null)
      {
        return elements.iterator();
      }
      else if (firstElement != null)
      {
        return new Iterator<T>()
        {

          private boolean hasNext = true;



          @Override
          public boolean hasNext()
          {
            return hasNext;
          }



          @Override
          public T next()
          {
            if (!hasNext)
            {
              throw new NoSuchElementException();
            }

            hasNext = false;
            return firstElement;
          }



          @Override
          public void remove()
          {
            if (hasNext || firstElement == null)
            {
              throw new IllegalStateException();
            }

            firstElement = null;
          }

        };
      }
      else
      {
        return Collections.<T> emptySet().iterator();
      }
    }



    /** {@inheritDoc} */
    @Override
    public boolean remove(Object o)
    {
      if (elements != null)
      {
        // Note: if there is one or zero values left we could stop
        // using the set. However, lets assume that if the set
        // was multi-valued before then it may become multi-valued
        // again.
        return elements.remove(o);
      }

      if (firstElement != null && firstElement.equals(o))
      {
        firstElement = null;
        return true;
      }

      return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean contains(Object o)
    {
      if (elements != null)
      {
        return elements.contains(o);
      }

      return (firstElement != null && firstElement.equals(o));
    }


    /**
     * Sets the initial capacity of this small set. If this small set
     * already contains elements or if its capacity has already been
     * defined then an {@link IllegalStateException} is thrown.
     *
     * @param initialCapacity
     *          The initial capacity of this small set.
     * @throws IllegalStateException
     *           If this small set already contains elements or if its
     *           capacity has already been defined.
     */
    public void setInitialCapacity(int initialCapacity)
        throws IllegalStateException
    {
      Reject.ifFalse(initialCapacity >= 0);

      if (elements != null)
      {
        throw new IllegalStateException();
      }

      if (initialCapacity > 1)
      {
        elements = new LinkedHashSet<T>(initialCapacity);
      }
    }



    /** {@inheritDoc} */
    @Override
    public int size()
    {
      if (elements != null)
      {
        return elements.size();
      }
      else if (firstElement != null)
      {
        return 1;
      }
      else
      {
        return 0;
      }
    }

  }



  /**
   * Creates an attribute that has no options.
   * <p>
   * This method is only intended for use by the {@link Attributes}
   * class.
   *
   * @param attributeType
   *          The attribute type.
   * @param name
   *          The user-provided attribute name.
   * @param values
   *          The attribute values.
   * @return The new attribute.
   */
  static Attribute create(AttributeType attributeType, String name,
      Set<ByteString> values)
  {
    final AttributeBuilder builder = new AttributeBuilder(attributeType, name);
    builder.addAll(values);
    return builder.toAttribute();
  }



  /**
   * Gets the named attribute type, creating a default attribute if
   * necessary.
   *
   * @param attributeName
   *          The name of the attribute type.
   * @return The attribute type associated with the provided attribute
   *         name.
   */
  private static AttributeType getAttributeType(String attributeName)
  {
    String lc = toLowerCase(attributeName);
    AttributeType type = DirectoryServer.getAttributeType(lc);
    if (type == null)
    {
      type = DirectoryServer.getDefaultAttributeType(attributeName);
    }
    return type;
  }

  /** The attribute type for this attribute. */
  private AttributeType attributeType;

  /** The name of this attribute as provided by the end user. */
  private String name;

  /** The normalized set of options if there are more than one. */
  private SortedSet<String> normalizedOptions;

  /** The set of options. */
  private final SmallSet<String> options = new SmallSet<String>();

  /** The map of normalized values => values for this attribute. */
  private Map<ByteString, ByteString> values =
      new SmallMap<ByteString, ByteString>();



  /**
   * Creates a new attribute builder with an undefined attribute type
   * and user-provided name. The attribute type, and optionally the
   * user-provided name, must be defined using
   * {@link #setAttributeType(AttributeType)} before the attribute
   * builder can be converted to an {@link Attribute}. Failure to do
   * so will yield an {@link IllegalStateException}.
   */
  public AttributeBuilder()
  {
    // No implementation required.
  }



  /**
   * Creates a new attribute builder from an existing attribute.
   * <p>
   * Modifications to the attribute builder will not impact the
   * provided attribute.
   *
   * @param attribute
   *          The attribute to be copied.
   */
  public AttributeBuilder(Attribute attribute)
  {
    this(attribute, false);
  }



  /**
   * Creates a new attribute builder from an existing attribute,
   * optionally omitting the values contained in the provided
   * attribute.
   * <p>
   * Modifications to the attribute builder will not impact the
   * provided attribute.
   *
   * @param attribute
   *          The attribute to be copied.
   * @param omitValues
   *          <CODE>true</CODE> if the values should be omitted.
   */
  public AttributeBuilder(Attribute attribute, boolean omitValues)
  {
    this(attribute.getAttributeType(), attribute.getName());

    for (String option : attribute.getOptions())
    {
      setOption(option);
    }

    if (!omitValues)
    {
      addAll(attribute);
    }
  }



  /**
   * Creates a new attribute builder with the specified type and no
   * options and no values.
   *
   * @param attributeType
   *          The attribute type for this attribute builder.
   */
  public AttributeBuilder(AttributeType attributeType)
  {
    this(attributeType, attributeType.getNameOrOID());
  }



  /**
   * Creates a new attribute builder with the specified type and
   * user-provided name and no options and no values.
   *
   * @param attributeType
   *          The attribute type for this attribute builder.
   * @param name
   *          The user-provided name for this attribute builder.
   */
  public AttributeBuilder(AttributeType attributeType, String name)
  {
    Reject.ifNull(attributeType, name);

    this.attributeType = attributeType;
    this.name = name;
  }



  /**
   * Creates a new attribute builder with the specified attribute name
   * and no options and no values.
   * <p>
   * If the attribute name cannot be found in the schema, a new
   * attribute type is created using the default attribute syntax.
   *
   * @param attributeName
   *          The attribute name for this attribute builder.
   */
  public AttributeBuilder(String attributeName)
  {
    this(getAttributeType(attributeName), attributeName);
  }



  /**
   * Adds the specified attribute value to this attribute builder if
   * it is not already present.
   *
   * @param valueString
   *          The string representation of the attribute value to be
   *          added to this attribute builder.
   * @return <code>true</code> if this attribute builder did not
   *         already contain the specified attribute value.
   */
  public boolean add(String valueString)
  {
    return add(ByteString.valueOf(valueString));
  }



  /**
   * Adds the specified attribute value to this attribute builder if it is not
   * already present.
   *
   * @param attributeValue
   *          The {@link ByteString} representation of the attribute value to be
   *          added to this attribute builder.
   * @return <code>true</code> if this attribute builder did not already contain
   *         the specified attribute value.
   */
  public boolean add(ByteString attributeValue)
  {
    return values.put(normalize(attributeValue), attributeValue) == null;
  }

  private ByteString normalize(ByteString attributeValue)
  {
    return normalize(attributeType, attributeValue);
  }

  private static ByteString normalize(AttributeType attributeType,
      ByteString attributeValue)
  {
    try
    {
      if (attributeType != null)
      {
        final MatchingRule eqRule = attributeType.getEqualityMatchingRule();
        return eqRule.normalizeAttributeValue(attributeValue);
      }
    }
    catch (DecodeException e)
    {
      // nothing to do here
    }
    return attributeValue;
  }

  /**
   * Adds all the values from the specified attribute to this
   * attribute builder if they are not already present.
   *
   * @param attribute
   *          The attribute containing the values to be added to this
   *          attribute builder.
   * @return <code>true</code> if this attribute builder was
   *         modified.
   */
  public boolean addAll(Attribute attribute)
  {
    boolean wasModified = false;
    for (ByteString v : attribute)
    {
      wasModified |= add(v);
    }
    return wasModified;
  }



  /**
   * Adds the specified attribute values to this attribute builder if
   * they are not already present.
   *
   * @param values
   *          The attribute values to be added to this attribute
   *          builder.
   * @return <code>true</code> if this attribute builder was
   *         modified.
   */
  public boolean addAll(Collection<ByteString> values)
  {
    boolean wasModified = false;
    for (ByteString v : values)
    {
      wasModified |= add(v);
    }
    return wasModified;
  }



  /**
   * Removes all attribute values from this attribute builder.
   */
  public void clear()
  {
    values.clear();
  }



  /**
   * Indicates whether this attribute builder contains the specified
   * value.
   *
   * @param value
   *          The value for which to make the determination.
   * @return <CODE>true</CODE> if this attribute builder has the
   *         specified value, or <CODE>false</CODE> if not.
   */
  public boolean contains(ByteString value)
  {
    return values.containsKey(normalize(value));
  }



  /**
   * Indicates whether this attribute builder contains all the values
   * in the collection.
   *
   * @param values
   *          The set of values for which to make the determination.
   * @return <CODE>true</CODE> if this attribute builder contains
   *         all the values in the provided collection, or
   *         <CODE>false</CODE> if it does not contain at least one
   *         of them.
   */
  public boolean containsAll(Collection<ByteString> values)
  {
    for (ByteString v : values)
    {
      if (!contains(v))
      {
        return false;
      }
    }
    return true;
  }



  /**
   * Retrieves the attribute type for this attribute builder.
   *
   * @return The attribute type for this attribute builder, or
   *         <code>null</code> if one has not yet been specified.
   */
  public AttributeType getAttributeType()
  {
    return attributeType;
  }



  /**
   * Returns <code>true</code> if this attribute builder contains no
   * attribute values.
   *
   * @return <CODE>true</CODE> if this attribute builder contains no
   *         attribute values.
   */
  public boolean isEmpty()
  {
    return values.isEmpty();
  }



  /**
   * Returns an iterator over the attribute values in this attribute
   * builder. The attribute values are returned in the order in which
   * they were added to this attribute builder. The returned iterator
   * supports attribute value removals via its <code>remove</code>
   * method.
   *
   * @return An iterator over the attribute values in this attribute
   *         builder.
   */
  @Override
  public Iterator<ByteString> iterator()
  {
    return values.values().iterator();
  }



  /**
   * Removes the specified attribute value from this attribute builder
   * if it is present.
   *
   * @param value
   *          The attribute value to be removed from this attribute
   *          builder.
   * @return <code>true</code> if this attribute builder contained
   *         the specified attribute value.
   */
  public boolean remove(ByteString value)
  {
    return values.remove(normalize(value)) != null;
  }



  /**
   * Removes the specified attribute value from this attribute builder
   * if it is present.
   *
   * @param valueString
   *          The string representation of the attribute value to be
   *          removed from this attribute builder.
   * @return <code>true</code> if this attribute builder contained
   *         the specified attribute value.
   */
  public boolean remove(String valueString)
  {
    return remove(ByteString.valueOf(valueString));
  }



  /**
   * Removes all the values from the specified attribute from this
   * attribute builder if they are not already present.
   *
   * @param attribute
   *          The attribute containing the values to be removed from
   *          this attribute builder.
   * @return <code>true</code> if this attribute builder was
   *         modified.
   */
  public boolean removeAll(Attribute attribute)
  {
    boolean wasModified = false;
    for (ByteString v : attribute)
    {
      wasModified |= remove(v);
    }
    return wasModified;
  }



  /**
   * Removes the specified attribute values from this attribute
   * builder if they are present.
   *
   * @param values
   *          The attribute values to be removed from this attribute
   *          builder.
   * @return <code>true</code> if this attribute builder was
   *         modified.
   */
  public boolean removeAll(Collection<ByteString> values)
  {
    boolean wasModified = false;
    for (ByteString v : values)
    {
      wasModified |= remove(v);
    }
    return wasModified;
  }



  /**
   * Replaces all the values in this attribute value with the
   * specified attribute value.
   *
   * @param value
   *          The attribute value to replace all existing values.
   */
  public void replace(ByteString value)
  {
    clear();
    add(value);
  }



  /**
   * Replaces all the values in this attribute value with the
   * specified attribute value.
   *
   * @param valueString
   *          The string representation of the attribute value to
   *          replace all existing values.
   */
  public void replace(String valueString)
  {
    replace(ByteString.valueOf(valueString));
  }



  /**
   * Replaces all the values in this attribute value with the
   * attributes from the specified attribute.
   *
   * @param attribute
   *          The attribute containing the values to replace all
   *          existing values.
   */
  public void replaceAll(Attribute attribute)
  {
    clear();
    addAll(attribute);
  }



  /**
   * Replaces all the values in this attribute value with the
   * specified attribute values.
   *
   * @param values
   *          The attribute values to replace all existing values.
   */
  public void replaceAll(Collection<ByteString> values)
  {
    clear();
    addAll(values);
  }



  /**
   * Sets the attribute type associated with this attribute builder.
   *
   * @param attributeType
   *          The attribute type for this attribute builder.
   */
  public void setAttributeType(AttributeType attributeType)
  {
    setAttributeType(attributeType, attributeType.getNameOrOID());
  }



  /**
   * Sets the attribute type and user-provided name associated with
   * this attribute builder.
   *
   * @param attributeType
   *          The attribute type for this attribute builder.
   * @param name
   *          The user-provided name for this attribute builder.
   */
  public void setAttributeType(
      AttributeType attributeType,
      String name)
  {
    Reject.ifNull(attributeType, name);

    this.attributeType = attributeType;
    this.name = name;
  }



  /**
   * Sets the attribute type associated with this attribute builder
   * using the provided attribute type name.
   * <p>
   * If the attribute name cannot be found in the schema, a new
   * attribute type is created using the default attribute syntax.
   *
   * @param attributeName
   *          The attribute name for this attribute builder.
   */
  public void setAttributeType(String attributeName)
  {
    setAttributeType(getAttributeType(attributeName), attributeName);
  }



  /**
   * Sets the initial capacity of this attribute builders internal set
   * of attribute values.
   * <p>
   * The initial capacity of an attribute builder defaults to one.
   * Applications should override this default if the attribute being
   * built is expected to contain many values.
   * <p>
   * This method should only be called before any attribute values
   * have been added to this attribute builder. If it is called
   * afterwards an {@link IllegalStateException} will be thrown.
   *
   * @param initialCapacity
   *          The initial capacity of this attribute builder.
   * @return This attribute builder.
   * @throws IllegalStateException
   *           If this attribute builder already contains attribute
   *           values.
   */
  public AttributeBuilder setInitialCapacity(int initialCapacity)
      throws IllegalStateException
  {
    // This is now a no op.
    return this;
  }



  /**
   * Adds the specified option to this attribute builder if it is not
   * already present.
   *
   * @param option
   *          The option to be added to this attribute builder.
   * @return <code>true</code> if this attribute builder did not
   *         already contain the specified option.
   */
  public boolean setOption(String option)
  {
    switch (options.size())
    {
    case 0:
      return options.add(option);
    case 1:
      // Normalize and add the first option to normalized set.
      normalizedOptions = new TreeSet<String>();
      normalizedOptions.add(toLowerCase(options.firstElement));

      if (normalizedOptions.add(toLowerCase(option)))
      {
        options.add(option);
        return true;
      }
      break;
    default:
      if (normalizedOptions.add(toLowerCase(option)))
      {
        options.add(option);
        return true;
      }
      break;
    }

    return false;
  }



  /**
   * Adds the specified options to this attribute builder if they are
   * not already present.
   *
   * @param options
   *          The options to be added to this attribute builder.
   * @return <code>true</code> if this attribute builder was
   *         modified.
   */
  public boolean setOptions(Collection<String> options)
  {
    boolean isModified = false;

    for (String option : options)
    {
      isModified |= setOption(option);
    }

    return isModified;
  }



  /**
   * Indicates whether this attribute builder has exactly the
   * specified set of options.
   *
   * This implementation returns
   * {@link java.util.AbstractCollection#isEmpty()}
   * if the provided set of options is <code>null</code>.
   * Otherwise it checks that the size of the provided
   * set of options is equal to the size of this attribute
   * builder options, returns <code>false</code> if the
   * sizes differ. If the sizes are the same then each
   * option in the provided set is checked and if all the
   * provided options are present <code>true</code> is
   * returned.
   *
   * @param  options
   *         The set of options for which to make the
   *         determination (may be <code>null</code>).
   * @return <code>true</code> if this attribute
   *         builder has exactly the specified
   *         set of options.
   */
  public boolean optionsEqual(Set<String> options)
  {
    if (options == null)
    {
      return this.options.isEmpty();
    }

    if (this.options.size() != options.size())
    {
      return false;
    }

    for (String option : options)
    {
      if (!this.options.contains(option))
      {
        return false;
      }
    }

    return true;
  }



  /**
   * Returns the number of attribute values in this attribute builder.
   *
   * @return The number of attribute values in this attribute builder.
   */
  public int size()
  {
    return values.size();
  }



  /**
   * Returns an attribute representing the content of this attribute
   * builder.
   * <p>
   * For efficiency purposes this method resets the content of this
   * attribute builder so that it no longer contains any options or
   * values and its attribute type is <code>null</code>.
   *
   * @return An attribute representing the content of this attribute
   *         builder.
   * @throws IllegalStateException
   *           If this attribute builder has an undefined attribute
   *           type or name.
   */
  public Attribute toAttribute() throws IllegalStateException
  {
    if (attributeType == null)
    {
      throw new IllegalStateException(
          "Undefined attribute type or name");
    }

    // First determine the minimum representation required for the set
    // of values.
    final int size = values.size();
    Map<ByteString, ByteString> newValues;
    if (size == 0)
    {
      newValues = Collections.emptyMap();
    }
    else if (size == 1)
    {
      Entry<ByteString, ByteString> entry = values.entrySet().iterator().next();
      newValues = Collections.singletonMap(entry.getKey(), entry.getValue());
      values.clear();
    }
    else
    {
      newValues = Collections.unmodifiableMap(values);
      values = new SmallMap<ByteString, ByteString>();
    }

    // Now create the appropriate attribute based on the options.
    Attribute attribute;
    switch (options.size())
    {
    case 0:
      attribute = new RealAttributeNoOptions(attributeType, name, newValues);
      break;
    case 1:
      attribute =
        new RealAttributeSingleOption(attributeType, name, newValues,
          options.firstElement);
      break;
    default:
      attribute =
        new RealAttributeManyOptions(attributeType, name, newValues,
          Collections.unmodifiableSet(options.elements), Collections
              .unmodifiableSortedSet(normalizedOptions));
      break;
    }

    // Reset the state of this builder.
    attributeType = null;
    name = null;
    normalizedOptions = null;
    options.clear();

    return attribute;
  }



  /** {@inheritDoc} */
  @Override
  public final String toString()
  {
    StringBuilder builder = new StringBuilder();

    builder.append("AttributeBuilder(");
    builder.append(name);

    for (String option : options)
    {
      builder.append(';');
      builder.append(option);
    }

    builder.append(", {");
    builder.append(Utils.joinAsString(", ", values.keySet()));
    builder.append("})");

    return builder.toString();
  }
}
