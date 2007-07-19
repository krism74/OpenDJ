package org.opends.statuspanel.browser.ldap;

import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.statuspanel.browser.ldap.Entry;
import org.opends.statuspanel.browser.ldap.EntryManager;
import org.opends.statuspanel.browser.ldap.LdapServerInfo;

import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.directory.Attributes;
import javax.naming.directory.Attribute;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.NamingException;
import javax.naming.NamingEnumeration;
import javax.naming.Context;
import javax.naming.SizeLimitExceededException;
import javax.naming.NoPermissionException;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;

/**
 */
public class JndiDirectoryFacade extends DirectoryFacade {

  static private final long MAX_ENTRIES = 10000;

  private LdapServerInfo info;

  private String userName;

  private String password;

  private DirContext ctx = null;

  public SearchOperation getRoot() {
    return getEntry("");
  }

  public ModifyOperation modifyEntry(Entry e, Map<String, Set<String>> newAttrs) {
    if (ctx == null) throw new IllegalStateException("context uninitialized");
    List<ModificationItem> modList = new ArrayList<ModificationItem>();
    Map<String, Set<String>> eAttrs = e.getAttributes();
    for (String eAttrName : eAttrs.keySet()) {
      Set<String> eAttrValues = eAttrs.get(eAttrName);
      Attribute eAttr = createAttribute(eAttrName, eAttrValues);
      if (!newAttrs.containsKey(eAttrName)) {
        modList.add(
                new ModificationItem(DirContext.REMOVE_ATTRIBUTE,
                        eAttr));
      } else if (!eAttrValues.equals(newAttrs.get(eAttrName))) {
        Attribute newAttr =
                createAttribute(eAttrName, newAttrs.get(eAttrName));
        modList.add(
                new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                        newAttr));
      }
    }
    for (String newAttrName : newAttrs.keySet()) {
      if (!e.getAttributes().containsKey(newAttrName)) {
        Attribute newAttr =
                createAttribute(newAttrName, newAttrs.get(newAttrName));
        modList.add(
                new ModificationItem(DirContext.ADD_ATTRIBUTE,
                        newAttr));
      }
    }
    return new JndiModifyOperation(ctx, e.getDn().toString(),
            modList.toArray(new ModificationItem[modList.size()]));
  }

  public AddOperation addEntry(Entry e) {
    if (ctx == null) throw new IllegalStateException("context uninitialized");
    Attributes attrs = new BasicAttributes();
    Map<String, Set<String>> eAttrs = e.getAttributes();
    for (String eAttrName : eAttrs.keySet()) {
      Attribute attr = new BasicAttribute(eAttrName, false);
      for (String eAttrValue : eAttrs.get(eAttrName)) {
        attr.add(eAttrValue);
      }
    }
    return new JndiAddOperation(ctx, e.getDn().toString(), attrs);
  }

  public DeleteOperation deleteEntry(Entry e) {
    if (ctx == null) throw new IllegalStateException("context uninitialized");
    return new JndiDeleteOperation(ctx, e.getDn().toString());
  }

  public SearchOperation getEntry(String name) {
    if (ctx == null) throw new IllegalStateException("context uninitialized");
    SearchControls constraints = new SearchControls();
    constraints.setSearchScope(SearchControls.OBJECT_SCOPE);
    return new JndiSearchOperation(ctx, name, getFilter(), constraints);
  }

  public SearchOperation getChildren(Entry parent) {
    if (ctx == null) throw new IllegalStateException("context uninitialized");
    String base = parent.getDn().toString();
    String filter = getFilter();
    SearchControls controls = new SearchControls();
    controls.setCountLimit(MAX_ENTRIES);
    return new JndiSearchOperation(ctx, base, filter, controls);
  }

  public void setLdapServerInfo(LdapServerInfo info) throws NamingException {
    this.info = info;
    initContext();
  }

  public void setCredentials(String name, String pw) throws Exception {
    this.userName = name;
    this.password = pw;
    initContext();
  }

  public Long getMaxEntries() {
    return MAX_ENTRIES;
  }

  private void initContext() throws NamingException {
    Hashtable<String, String> env = new Hashtable<String, String>();
    env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
    env.put(Context.PROVIDER_URL, "ldap://" + info.getHostName() + ":" + info.getPort());
    env.put ("java.naming.ldap.version", "3");
    env.put (Context.REFERRAL, "follow");
    env.put (Context.SECURITY_AUTHENTICATION, "simple");
    if (userName != null) {
      env.put (Context.SECURITY_PRINCIPAL, userName);
      env.put (Context.SECURITY_CREDENTIALS, password);
    }
    ctx = new InitialDirContext(env);
  }

  private Entry createEntryFromSearchResult(SearchResult sr)
          throws NamingException, DirectoryException {
    Entry entry;
    String nameInNamespace = sr.getNameInNamespace();
    DN dn = DN.decode(nameInNamespace);
    entry = EntryManager.createEntry(dn, sr);
    Attributes attrs = sr.getAttributes();
    NamingEnumeration ae = attrs.getAll();
    Map<String, Set<String>> attrMap = new HashMap<String, Set<String>>();
    while (ae.hasMore()) {
      Attribute attr = (Attribute) ae.next();
      NamingEnumeration ve = attr.getAll();
      Set<String> valueSet = new HashSet<String>();
      while (ve.hasMore()) {
        Object v = ve.next();
        valueSet.add(v.toString());
      }
      attrMap.put(attr.getID(), valueSet);
    }
    entry.setAttributes(attrMap);
    return entry;
  }

  private Attribute createAttribute(String id, Set<String> values) {
    Attribute attr = new BasicAttribute(id);
    for (String eAttrValue : values) {
      attr.add(eAttrValue);
    }
    return attr;
  }

  abstract private class JndiOperation {

    DirContext ctx;
    String name;
    Throwable t;
    String error;
    String warning;


    JndiOperation(DirContext ctx, String name) {
      this.ctx = ctx;
      this.name = name;
    }

    public boolean perform() {
      boolean success = false;
      try {
        doOperation();
        success = true;
      } catch (NoPermissionException npe) {
        error = "Permission denied accessing " + name;
        this.t = npe;
      } catch (SizeLimitExceededException slee) {
        warning = new StringBuilder("Maximum ").
              append(getMaxEntries()).
              append(" Displayed.  Try applying a filter").
              toString();
        this.t = slee;
      } catch (Throwable t) {
        error = "Error accessing " + name + ": " + t.getLocalizedMessage();
        this.t = t;
      }
      return success;
    }

    abstract protected void doOperation() throws Exception;

    public Throwable getThrowable() {
      return t;
    }

    public String getLocalizedErrorMessage() {
      return error;
    }

    public String getLocalizedWarningMessage() {
      return warning;
    }
  }

  private class JndiSearchOperation extends JndiOperation implements SearchOperation {

    String filter;
    SearchControls controls;
    List<Entry> result;
    Throwable throwable;
    String warning;
    String error;
    boolean sizeLimitExceeded;

    public JndiSearchOperation(DirContext ctx, String base, String filter,
                           SearchControls controls) {
      super(ctx, base);
      this.filter = filter;
      this.controls = controls;
    }

    protected void doOperation() throws Exception {
      result = new ArrayList<Entry>();
      try {
        NamingEnumeration<SearchResult> ne =
                ctx.search(name, filter, controls);
        while (ne.hasMore()) {
          SearchResult sr = ne.next();
          Entry entry = createEntryFromSearchResult(sr);
          result.add(entry);
        }
      } catch (SizeLimitExceededException slee) {
        sizeLimitExceeded = true;
        throw slee;
      }
    }

    public List<Entry> getResult() {
      perform();
      return this.result;
    }

    public boolean sizeLimitExceeded() {
      return sizeLimitExceeded;
    }

  }

  private class JndiDeleteOperation extends JndiOperation implements DeleteOperation {

    public JndiDeleteOperation(DirContext ctx, String name) {
      super(ctx, name);
    }

    public void doOperation() throws Exception {
      ctx.unbind(name);
    }

  }

  private class JndiModifyOperation extends JndiOperation implements ModifyOperation {

    ModificationItem[] mods;

    public JndiModifyOperation(DirContext ctx, String name, ModificationItem[] mods) {
      super(ctx, name);
      this.mods = mods;
    }

    public void doOperation() throws Exception {
      ctx.modifyAttributes(name, mods);
    }

  }

  private class JndiAddOperation extends JndiOperation implements AddOperation {

    Attributes attrs;

    public JndiAddOperation(DirContext ctx, String name, Attributes attrs) {
      super(ctx, name);
      this.attrs = attrs;
    }

    public void doOperation() throws Exception {
      ctx.bind(name, ctx, attrs);
    }

  }


}
