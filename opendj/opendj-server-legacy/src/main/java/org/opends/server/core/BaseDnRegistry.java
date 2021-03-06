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
 *      Copyright 2007-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.core;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.api.Backend;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;

import static org.forgerock.util.Reject.*;
import static org.opends.messages.CoreMessages.*;

/**
 * Registry for maintaining the set of registered base DN's, associated backends
 * and naming context information.
 */
public class BaseDnRegistry {

  /** The set of base DNs registered with the server. */
  private final TreeMap<DN, Backend> baseDNs = new TreeMap<DN, Backend>();

  /** The set of private naming contexts registered with the server. */
  private final TreeMap<DN, Backend> privateNamingContexts = new TreeMap<DN, Backend>();

  /** The set of public naming contexts registered with the server. */
  private final TreeMap<DN, Backend> publicNamingContexts = new TreeMap<DN, Backend>();

  /**
   * Indicates whether or not this base DN registry is in test mode.
   * A registry instance that is in test mode will not modify backend
   * objects referred to in the above maps.
   */
  private boolean testOnly;

  /**
   * Registers a base DN with this registry.
   *
   * @param  baseDN to register
   * @param  backend with which the base DN is associated
   * @param  isPrivate indicates whether or not this base DN is private
   * @return list of error messages generated by registering the base DN
   *         that should be logged if the changes to this registry are
   *         committed to the server
   * @throws DirectoryException if the base DN cannot be registered
   */
  public List<LocalizableMessage> registerBaseDN(DN baseDN, Backend<?> backend, boolean isPrivate)
      throws DirectoryException
  {
    // Check to see if the base DN is already registered with the server.
    Backend<?> existingBackend = baseDNs.get(baseDN);
    if (existingBackend != null)
    {
      LocalizableMessage message = ERR_REGISTER_BASEDN_ALREADY_EXISTS.
          get(baseDN, backend.getBackendID(), existingBackend.getBackendID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }


    // Check to see if the backend is already registered with the server for
    // any other base DN(s).  The new base DN must not have any hierarchical
    // relationship with any other base Dns for the same backend.
    LinkedList<DN> otherBaseDNs = new LinkedList<DN>();
    for (DN dn : baseDNs.keySet())
    {
      Backend<?> b = baseDNs.get(dn);
      if (b.equals(backend))
      {
        otherBaseDNs.add(dn);

        if (baseDN.isAncestorOf(dn) || baseDN.isDescendantOf(dn))
        {
          LocalizableMessage message = ERR_REGISTER_BASEDN_HIERARCHY_CONFLICT.
              get(baseDN, backend.getBackendID(), dn);
          throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
        }
      }
    }


    // Check to see if the new base DN is subordinate to any other base DN
    // already defined.  If it is, then any other base DN(s) for the same
    // backend must also be subordinate to the same base DN.
    final Backend<?> superiorBackend = getSuperiorBackend(baseDN, otherBaseDNs, backend.getBackendID());
    if (superiorBackend == null && backend.getParentBackend() != null)
    {
      LocalizableMessage message = ERR_REGISTER_BASEDN_NEW_BASE_NOT_SUBORDINATE.
          get(baseDN, backend.getBackendID(), backend.getParentBackend().getBackendID());
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }


    // Check to see if the new base DN should be the superior base DN for any
    // other base DN(s) already defined.
    LinkedList<Backend<?>> subordinateBackends = new LinkedList<Backend<?>>();
    LinkedList<DN>      subordinateBaseDNs  = new LinkedList<DN>();
    for (DN dn : baseDNs.keySet())
    {
      Backend<?> b = baseDNs.get(dn);
      DN parentDN = dn.parent();
      while (parentDN != null)
      {
        if (parentDN.equals(baseDN))
        {
          subordinateBaseDNs.add(dn);
          subordinateBackends.add(b);
          break;
        }
        else if (baseDNs.containsKey(parentDN))
        {
          break;
        }

        parentDN = parentDN.parent();
      }
    }


    // If we've gotten here, then the new base DN is acceptable.  If we should
    // actually apply the changes then do so now.
    final List<LocalizableMessage> errors = new LinkedList<LocalizableMessage>();

    // Check to see if any of the registered backends already contain an
    // entry with the DN specified as the base DN.  This could happen if
    // we're creating a new subordinate backend in an existing directory
    // (e.g., moving the "ou=People,dc=example,dc=com" branch to its own
    // backend when that data already exists under the "dc=example,dc=com"
    // backend).  This condition shouldn't prevent the new base DN from
    // being registered, but it's definitely important enough that we let
    // the administrator know about it and remind them that the existing
    // backend will need to be reinitialized.
    if (superiorBackend != null && superiorBackend.entryExists(baseDN))
    {
      errors.add(WARN_REGISTER_BASEDN_ENTRIES_IN_MULTIPLE_BACKENDS.
          get(superiorBackend.getBackendID(), baseDN, backend.getBackendID()));
    }


    baseDNs.put(baseDN, backend);

    if (superiorBackend == null)
    {
      if (!testOnly)
      {
        backend.setPrivateBackend(isPrivate);
      }

      if (isPrivate)
      {
        privateNamingContexts.put(baseDN, backend);
      }
      else
      {
        publicNamingContexts.put(baseDN, backend);
      }
    }
    else if (otherBaseDNs.isEmpty() && !testOnly)
    {
      backend.setParentBackend(superiorBackend);
      superiorBackend.addSubordinateBackend(backend);
    }

    if (!testOnly)
    {
      for (Backend<?> b : subordinateBackends)
      {
        Backend<?> oldParentBackend = b.getParentBackend();
        if (oldParentBackend != null)
        {
          oldParentBackend.removeSubordinateBackend(b);
        }

        b.setParentBackend(backend);
        backend.addSubordinateBackend(b);
      }
    }

    for (DN dn : subordinateBaseDNs)
    {
      publicNamingContexts.remove(dn);
      privateNamingContexts.remove(dn);
    }

    return errors;
  }

  private Backend<?> getSuperiorBackend(DN baseDN, LinkedList<DN> otherBaseDNs, String backendID)
      throws DirectoryException
  {
    Backend<?> superiorBackend = null;
    DN parentDN = baseDN.parent();
    while (parentDN != null)
    {
      if (baseDNs.containsKey(parentDN))
      {
        superiorBackend = baseDNs.get(parentDN);

        for (DN dn : otherBaseDNs)
        {
          if (!dn.isDescendantOf(parentDN))
          {
            LocalizableMessage msg = ERR_REGISTER_BASEDN_DIFFERENT_PARENT_BASES.get(baseDN, backendID, dn);
            throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, msg);
          }
        }
        break;
      }

      parentDN = parentDN.parent();
    }
    return superiorBackend;
  }


  /**
   * Deregisters a base DN with this registry.
   *
   * @param  baseDN to deregister
   * @return list of error messages generated by deregistering the base DN
   *         that should be logged if the changes to this registry are
   *         committed to the server
   * @throws DirectoryException if the base DN could not be deregistered
   */
  public List<LocalizableMessage> deregisterBaseDN(DN baseDN)
         throws DirectoryException
  {
    ifNull(baseDN);

    // Make sure that the Directory Server actually contains a backend with
    // the specified base DN.
    Backend<?> backend = baseDNs.get(baseDN);
    if (backend == null)
    {
      LocalizableMessage message =
          ERR_DEREGISTER_BASEDN_NOT_REGISTERED.get(baseDN);
      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
    }


    // Check to see if the backend has a parent backend, and whether it has
    // any subordinates with base DNs that are below the base DN to remove.
    Backend<?>             superiorBackend     = backend.getParentBackend();
    LinkedList<Backend<?>> subordinateBackends = new LinkedList<Backend<?>>();
    if (backend.getSubordinateBackends() != null)
    {
      for (Backend<?> b : backend.getSubordinateBackends())
      {
        for (DN dn : b.getBaseDNs())
        {
          if (dn.isDescendantOf(baseDN))
          {
            subordinateBackends.add(b);
            break;
          }
        }
      }
    }


    // See if there are any other base DNs registered within the same backend.
    LinkedList<DN> otherBaseDNs = new LinkedList<DN>();
    for (DN dn : baseDNs.keySet())
    {
      if (dn.equals(baseDN))
      {
        continue;
      }

      Backend<?> b = baseDNs.get(dn);
      if (backend.equals(b))
      {
        otherBaseDNs.add(dn);
      }
    }


    // If we've gotten here, then it's OK to make the changes.

    // Get rid of the references to this base DN in the mapping tree
    // information.
    baseDNs.remove(baseDN);
    publicNamingContexts.remove(baseDN);
    privateNamingContexts.remove(baseDN);

    final LinkedList<LocalizableMessage> errors = new LinkedList<LocalizableMessage>();
    if (superiorBackend == null)
    {
      // If there were any subordinate backends, then all of their base DNs
      // will now be promoted to naming contexts.
      for (Backend<?> b : subordinateBackends)
      {
        if (!testOnly)
        {
          b.setParentBackend(null);
          backend.removeSubordinateBackend(b);
        }

        for (DN dn : b.getBaseDNs())
        {
          if (b.isPrivateBackend())
          {
            privateNamingContexts.put(dn, b);
          }
          else
          {
            publicNamingContexts.put(dn, b);
          }
        }
      }
    }
    else
    {
      // If there are no other base DNs for the associated backend, then
      // remove this backend as a subordinate of the parent backend.
      if (otherBaseDNs.isEmpty() && !testOnly)
      {
        superiorBackend.removeSubordinateBackend(backend);
      }


      // If there are any subordinate backends, then they need to be made
      // subordinate to the parent backend.  Also, we should log a warning
      // message indicating that there may be inconsistent search results
      // because some of the structural entries will be missing.
      if (! subordinateBackends.isEmpty())
      {
        // Suppress this warning message on server shutdown.
        if (!DirectoryServer.getInstance().isShuttingDown()) {
          errors.add(WARN_DEREGISTER_BASEDN_MISSING_HIERARCHY.get(
              baseDN, backend.getBackendID()));
        }

        if (!testOnly)
        {
          for (Backend<?> b : subordinateBackends)
          {
            backend.removeSubordinateBackend(b);
            superiorBackend.addSubordinateBackend(b);
            b.setParentBackend(superiorBackend);
          }
        }
      }
    }
    return errors;
  }


  /**
   * Creates a default instance.
   */
  BaseDnRegistry()
  {
    this(false);
  }

  /**
   * Returns a copy of this registry.
   *
   * @return copy of this registry
   */
  BaseDnRegistry copy()
  {
    final BaseDnRegistry registry = new BaseDnRegistry(true);
    registry.baseDNs.putAll(baseDNs);
    registry.publicNamingContexts.putAll(publicNamingContexts);
    registry.privateNamingContexts.putAll(privateNamingContexts);
    return registry;
  }


  /**
   * Creates a parameterized instance.
   *
   * @param testOnly indicates whether this registry will be used for testing;
   *        when <code>true</code> this registry will not modify backends
   */
  private BaseDnRegistry(boolean testOnly)
  {
    this.testOnly = testOnly;
  }


  /**
   * Gets the mapping of registered base DNs to their associated backend.
   *
   * @return mapping from base DN to backend
   */
  Map<DN,Backend> getBaseDnMap() {
    return this.baseDNs;
  }


  /**
   * Gets the mapping of registered public naming contexts to their
   * associated backend.
   *
   * @return mapping from naming context to backend
   */
  Map<DN,Backend> getPublicNamingContextsMap() {
    return this.publicNamingContexts;
  }


  /**
   * Gets the mapping of registered private naming contexts to their
   * associated backend.
   *
   * @return mapping from naming context to backend
   */
  Map<DN,Backend> getPrivateNamingContextsMap() {
    return this.privateNamingContexts;
  }




  /**
   * Indicates whether the specified DN is contained in this registry as
   * a naming contexts.
   *
   * @param  dn  The DN for which to make the determination.
   *
   * @return  {@code true} if the specified DN is a naming context in this
   *          registry, or {@code false} if it is not.
   */
  boolean containsNamingContext(DN dn)
  {
    return privateNamingContexts.containsKey(dn) || publicNamingContexts.containsKey(dn);
  }


  /**
   * Clear and nullify this registry's internal state.
   */
  void clear() {
    baseDNs.clear();
    privateNamingContexts.clear();
    publicNamingContexts.clear();
  }

}
