package org.opends.statuspanel.browser;

import java.util.Properties;
import java.util.List;
import java.util.Collections;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 * Configuration properties for the entry browser.
 */
public class BrowserProperties extends Properties {

  static private final Logger LOG =
          Logger.getLogger(BrowserProperties.class.getName());

  static private BrowserProperties instance;

  /**
   * Gets the instance of browser properties.
   * @return BrowserProperties properties
   */
  static public BrowserProperties getInstance() {
    if (instance == null) {
      instance = new BrowserProperties();
    }
    return instance;
  }

  /**
   * Gets the list of DNs that will be used as roots in the browser.
   * @return list of strings representing DNs to be used as roots
   */
  public List<String> getBaseDnList() {
    List<String> dnList = new ArrayList<String>();
    String rawDns = getProperty("base.dns");
    if (rawDns != null) {
      String[] sa = rawDns.split(" ");
      for (String s : sa) {
        try {
          s = URLDecoder.decode(s, "utf8");
        } catch (UnsupportedEncodingException e) {}
        dnList.add(s);
      }
    }
    return dnList;
  }

  /**
   * Gets the list of attribute names used for querying entries in
   * the browser.
   * @return list of string representing query attributes
   */
  public List<String> getFilterAttributeList() {
    List<String> dnList = new ArrayList<String>();
    String rawDns = getProperty("filter.attrs");
    if (rawDns != null) {
      String[] sa = rawDns.split(" ");
      for (String s : sa) {
        dnList.add(s);
      }
    }
    return dnList;
  }

  private BrowserProperties() {
    InputStream is = getClass().getResourceAsStream("browser.properties");
    try {
      load(is);
    } catch (IOException e) {
      LOG.log(Level.INFO, "Error loading browser properties", e);
    }
  }

}
