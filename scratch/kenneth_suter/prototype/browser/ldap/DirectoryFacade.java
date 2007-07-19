package org.opends.statuspanel.browser.ldap;

import org.opends.statuspanel.browser.ldap.LdapServerInfo;
import org.opends.statuspanel.browser.ldap.Entry;

import java.util.List;
import java.util.Set;
import java.util.Map;

public abstract class DirectoryFacade {

  static private final String DEFAULT_FILTER = "objectclass=*";

  private String filter = DEFAULT_FILTER;

  public interface Operation {

    boolean perform();

    Throwable getThrowable();

    String getLocalizedErrorMessage();

    String getLocalizedWarningMessage();

  }

  public interface SearchOperation extends Operation {

    List<Entry> getResult();

    boolean sizeLimitExceeded();

  }

  public interface DeleteOperation extends Operation {

  }

  public interface ModifyOperation extends Operation {

  }

  public interface AddOperation extends Operation {

  }

  abstract public ModifyOperation modifyEntry(Entry e,
                                              Map<String,Set<String>> newAttrs);

  abstract public AddOperation addEntry(Entry e);

  abstract public DeleteOperation deleteEntry(Entry e);

  abstract public SearchOperation getEntry(String name);

  abstract public SearchOperation getRoot();

  abstract public SearchOperation getChildren(Entry e);

  abstract public void setLdapServerInfo(LdapServerInfo info) throws Exception;

  abstract public void setCredentials(String name, String pw) throws Exception;

  abstract public Long getMaxEntries();

  public String getFilter() {
    return this.filter;
  }

  public void setFilter(String filter) {
    if (filter != null) {
      this.filter = filter;
    } else {
      this.filter = DEFAULT_FILTER;
    }
  }

}
