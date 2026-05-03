package com.vigolium.extension.ui.table;

import com.vigolium.extension.filter.FilterRule;
import java.util.List;

public class FilterRulesTableModel extends BaseTableModel<FilterRule> {

    public FilterRulesTableModel() {
        super(createColumns());
    }

    private static List<ColumnDef<FilterRule>> createColumns() {
        return List.of(
                ColumnDef.<FilterRule>builder()
                        .name("Enabled")
                        .type(Boolean.class)
                        .accessor(FilterRule::isEnabled)
                        .editable(true)
                        .width(60)
                        .sortable(false)
                        .build(),
                ColumnDef.<FilterRule>builder()
                        .name("Operator")
                        .type(String.class)
                        .accessor(r ->
                                r.getOperator() == null ? "" : r.getOperator().label())
                        .width(70)
                        .sortable(false)
                        .build(),
                ColumnDef.<FilterRule>builder()
                        .name("Match Type")
                        .type(String.class)
                        .accessor(r -> r.getMatchType().label())
                        .preferredWidth(120)
                        .sortable(false)
                        .build(),
                ColumnDef.<FilterRule>builder()
                        .name("Relationship")
                        .type(String.class)
                        .accessor(r -> r.getRelationship().label())
                        .preferredWidth(150)
                        .sortable(false)
                        .build(),
                ColumnDef.<FilterRule>builder()
                        .name("Condition")
                        .type(String.class)
                        .accessor(r -> r.getCondition() == null ? "" : r.getCondition())
                        .preferredWidth(250)
                        .sortable(false)
                        .build());
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
        if (col == 0 && value instanceof Boolean b) {
            rows.get(row).setEnabled(b);
            fireTableCellUpdated(row, col);
        }
    }
}
