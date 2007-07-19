package org.opends.statuspanel.browser.ui;

import java.util.Set;
import java.util.Map;
import java.util.Collections;

/**
 */
public class EmptyAttributesView extends AttributesView {

  void setAttributes(Map<String, Set<String>> attributes) {
    // do nothing;
  }

  Map<String, Set<String>> getAttributes() {
    return Collections.emptyMap();
  }

}
