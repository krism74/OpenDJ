package org.opends.guitools.statuspanel.browser.ui;

import org.opends.guitools.statuspanel.browser.ldap.Entry;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.awt.*;

/**
 */
public class TableAttributesView extends AttributesView {

  private JTable tblEntry;
  private EntryTableModel tableModel;

  public TableAttributesView() {
    init();
  }

  private void init() {
    tblEntry = new JTable();
    tblEntry.setShowGrid(false);

    setLayout(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets.top = 10;
    gbc.insets.left = 10;
    gbc.insets.right = 10;
    gbc.weightx = 1.0;
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weighty = 1.0;
    add(new JScrollPane(tblEntry), gbc);    
  }

  protected void setAttributes(Map<String, Set<String>> attributes) {
    tableModel = new EntryTableModel(attributes);
    tblEntry.setModel(tableModel);
  }

  Map<String, Set<String>> getAttributes() {
    return tableModel.getCurrentAttributes();
  }

  private class EntryTableModel extends AbstractTableModel {

    Map<Integer, String[]> rowData = new HashMap<Integer, String[]>();

    String[] columnNames = {
            "Attribute Name",
            "Value"
    };

    public EntryTableModel(Entry entry) {
      Map<String, Set<String>> attrs = entry.getAttributes();
      setAttributes(attrs);
    }

    public EntryTableModel(Map<String,Set<String>> attrs) {
      setAttributes(attrs);
    }

    private void setAttributes(Map<String, Set<String>> attrs) {
      int row = 0;
      for (String name : attrs.keySet()) {
        Set<String> values = attrs.get(name);
        for (String value : values) {
          String[] attrValue = new String[2];
          attrValue[0] = name;
          attrValue[1] = value;
          rowData.put(row++, attrValue);
        }
      }
    }

    public Map<String, Set<String>> getCurrentAttributes() {
      Map<String, Set<String>> attributes = new HashMap<String, Set<String>>();
      for (int i = 0; i < rowData.size(); i++) {
        String key = rowData.get(i)[0];
        Set<String> values = attributes.get(key);
        if (values == null) {
          values = new HashSet<String>();
          attributes.put(key, values);
        }
        values.add(rowData.get(i)[1]);
      }
      return attributes;
    }

    public int getRowCount() {
      return rowData.size();
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      return rowData.get(rowIndex)[columnIndex];
    }

    public int getColumnCount() {
      return 2;
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == 1;
    }


    public String getColumnName(int column) {
      return columnNames[column];
    }

    public void setValueAt(Object newValue, int rowIndex, int columnIndex) {
      Object oldValue = getValueAt(rowIndex, columnIndex);
      if (oldValue == null && newValue != null ||
              !oldValue.equals(newValue)) {
        rowData.get(rowIndex)[columnIndex] = newValue.toString();
        String attrName = rowData.get(rowIndex)[0];
        modified(attrName, oldValue, newValue);
      }
    }

  }


}
