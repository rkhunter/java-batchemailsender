package models;

import javax.swing.table.DefaultTableModel;

/**
 * Created by rkhunter on 2/3/17.
 */
public class NonEditableTableModel extends DefaultTableModel {
    public NonEditableTableModel() {
        super();
    }
    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }
}
