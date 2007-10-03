package org.opends.guitools.statuspanel.browser.ldap;

import org.opends.server.types.DN;
import org.opends.server.types.RDN;

import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 */
public class Entry {

  static public final String ROOT_ENTRY_DISPLAY_NAME = "Root";

  static public final Entry NULL_ENTRY = new Entry(DN.NULL_DN, null);

  DN dn = null;
  Object payload = null;
  Object node = null;
  Map<String,Set<String>> attributes = new HashMap<String,Set<String>>();
  long childrenSetTimestamp;
  List<Entry> children = null;
  Boolean hasChildren;
  Entry parent = null;
  private boolean childrenSizeExceeded;
  String error;
  String warning;

  /** Indicates that this entry has been deleted */
  boolean deleted;

  /** Creates a new instance of Entry */
  Entry(DN dn, Object payload) {
      this.dn = dn;
      this.payload = payload;
  }

  public DN getDn() {
      return this.dn;
  }

  public void setNode(Object node) {
      this.node = node;
  }

  public Object getNode() {
      return this.node;
  }

  public Map<String,Set<String>> getAttributes() {
      return this.attributes;
  }

  public void setAttributes(Map<String,Set<String>> attrs) {
      if (attrs != null) {
          this.attributes = attrs;
      }
  }

  public boolean canDelete() {
    return !ROOT_ENTRY_DISPLAY_NAME.equals(toString());
  }

  public boolean isLeaf() {
    while (children == null) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        // do nothing;
      }
    }
    return children.size() == 0;
  }

  public Entry getParent() {
    return parent;
  }

  public String getLocalizedWarningMessage() {
    return warning;
  }

  public void setLocalizedWarningMessage(String warning) {
    this.warning = warning;
  }

  public String getLocalizedErrorMessage() {
    return error;
  }

  public void setLocalizedErrorMessage(String error) {
    this.error = error;
  }

  void setChildrenSizeLimitExceeded(boolean exceeded) {
    this.childrenSizeExceeded = exceeded;
  }

  public boolean getChildrenSizeLimitExceeded() {
    return this.childrenSizeExceeded;          
  }

  public List<Entry> getChildren() {
    return children;
  }

  /**
   * Indicates whether or not this entry has a representation
   * in the backing directory.
   * @return boolean where tree indicates that this entry has no representation
   *         in the backing directory server.
   */
  public boolean isNew() {
    return payload == null;
  }

  public boolean childrenNeedLoading() {
    // Children never need reloading once they
    // have been loaded.  We probably want to
    // change this to support refresh or periodic
    // update of nodes.
    return childrenSetTimestamp == 0;
  }

  public String toString() {
    String s;
    if (dn != null) {
      RDN rdn = dn.getRDN();
      String rdnString = (rdn != null ? rdn.toString() : "");
      if (rdnString.length() == 0) {
        s = ROOT_ENTRY_DISPLAY_NAME;
      } else {
        s = rdnString;
      }
    } else {
      s = "";
    }
    return s;
  }

  public long getChildrenSetTimestamp() {
    return this.childrenSetTimestamp;
  }

  void setChildren(List<Entry> children) {
    childrenSetTimestamp = System.currentTimeMillis();
    this.children = children;
  }

  void setChildrenNeedReloading(boolean b) {
    if (!b && childrenSetTimestamp == 0) {
      this.childrenSetTimestamp = System.currentTimeMillis();
    } else {
      this.childrenSetTimestamp = 0;
    }
  }

  void setParent(Entry parent) {
    this.parent = parent;
  }

  void addChild(Entry child) {
    if (children == null) {
      children = new ArrayList<Entry>();
    }
    children.add(child);
  }

  public void delete() {
    this.deleted = true;
  }

  public boolean isDeleted() {
    return deleted;
  }

  /**
   * Sets whether or not this entry has children.  Can be null
   * to indicate that we don't know if this entry has children.
   *
   * @param b where true means yes there are children, false means
   *        there are no children and null means we don't know
   */
  void hasChildren(Boolean b) {
    this.hasChildren = b;
  }

  /**
   * Indicates whether or not this entry has children.
   *
   * @return Boolean where true means yes there are children, false means
   *         there are no children and null means we don't know
   */
  public Boolean hasChildren() {
    return hasChildren;
  }
}
