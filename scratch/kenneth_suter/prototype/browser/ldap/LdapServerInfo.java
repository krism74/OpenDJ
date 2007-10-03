package org.opends.guitools.statuspanel.browser.ldap;

/**
 */
public class LdapServerInfo {

  static public LdapServerInfo fromString(String s) {
      LdapServerInfo info = new LdapServerInfo();
      int i = s.indexOf(":");
      if (i == -1) {
          info.hostName = s;
      } else {
          info.hostName = s.substring(0, i);
          info.port = Integer.parseInt(s.substring(i + 1));
      }
      return info;
  }

  String hostName = "localhost";
  Integer port = 389;

  /** Creates a new instance of LdapServerInfo */
  public LdapServerInfo() {
  }

  /** Creates a new instance of LdapServerInfo */
  public LdapServerInfo(String host, Integer port) {
    setHostName(host);
    setPort(port);
  }

  public String toString() {
      return getHostName() + ":" + getPort();
  }

  public String getHostName() {
      return this.hostName;
  }

  public void setHostName(String hostName) {
      this.hostName = hostName;
  }

  public Integer getPort() {
      return this.port;
  }

  public void setPort(Integer port) {
      this.port = port;
  }

}
