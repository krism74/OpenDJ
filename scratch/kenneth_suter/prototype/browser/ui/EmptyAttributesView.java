package org.opends.guitools.statuspanel.browser.ui;

import java.util.Set;
import java.util.Map;
import java.util.Collections;
import java.awt.*;

/**
 */
public class EmptyAttributesView extends AttributesView {

  private static final long serialVersionUID = -8775723189094483696L;

  protected void setAttributes(Map<String, Set<String>> attributes) {
    // do nothing;
  }

  Map<String, Set<String>> getAttributes() {
    return Collections.emptyMap();
  }

}
