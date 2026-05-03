package com.vigolium.extension.ui.table;

import java.util.Comparator;

public class ColumnDef<E> {

    @FunctionalInterface
    public interface ValueAccessor<E> {
        Object extract(E row);
    }

    private final String name;
    private final Class<?> type;
    private final ValueAccessor<E> accessor;
    private final int width;
    private final int preferredWidth;
    private final boolean sortable;
    private final boolean editable;
    private final Comparator<?> comparator;
    private final String tooltip;

    private ColumnDef(Builder<E> builder) {
        this.name = builder.name;
        this.type = builder.type;
        this.accessor = builder.accessor;
        this.width = builder.width;
        this.preferredWidth = builder.preferredWidth;
        this.sortable = builder.sortable;
        this.editable = builder.editable;
        this.comparator = builder.comparator;
        this.tooltip = builder.tooltip;
    }

    public String name() {
        return name;
    }

    public Class<?> type() {
        return type;
    }

    public ValueAccessor<E> accessor() {
        return accessor;
    }

    public int width() {
        return width;
    }

    public int preferredWidth() {
        return preferredWidth;
    }

    public boolean sortable() {
        return sortable;
    }

    public boolean editable() {
        return editable;
    }

    public Comparator<?> comparator() {
        return comparator;
    }

    public String tooltip() {
        return tooltip;
    }

    public static <E> Builder<E> builder() {
        return new Builder<>();
    }

    public static class Builder<E> {
        private String name;
        private Class<?> type = String.class;
        private ValueAccessor<E> accessor;
        private int width = -1;
        private int preferredWidth = -1;
        private boolean sortable = true;
        private boolean editable = false;
        private Comparator<?> comparator;
        private String tooltip;

        public Builder<E> name(String name) {
            this.name = name;
            return this;
        }

        public Builder<E> type(Class<?> type) {
            this.type = type;
            return this;
        }

        public Builder<E> accessor(ValueAccessor<E> accessor) {
            this.accessor = accessor;
            return this;
        }

        public Builder<E> width(int width) {
            this.width = width;
            return this;
        }

        public Builder<E> preferredWidth(int preferredWidth) {
            this.preferredWidth = preferredWidth;
            return this;
        }

        public Builder<E> sortable(boolean sortable) {
            this.sortable = sortable;
            return this;
        }

        public Builder<E> editable(boolean editable) {
            this.editable = editable;
            return this;
        }

        public Builder<E> comparator(Comparator<?> comparator) {
            this.comparator = comparator;
            return this;
        }

        public Builder<E> tooltip(String tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        public ColumnDef<E> build() {
            if (name == null || accessor == null) {
                throw new IllegalStateException("name and accessor are required");
            }
            return new ColumnDef<>(this);
        }
    }
}
