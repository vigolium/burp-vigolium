# Swing UI Application Design Guide

Standards for building large-scale Java Swing desktop applications.

## Burp HTTP Editor Layout

- Use `JSplitPane(HORIZONTAL_SPLIT)` with `resizeWeight=0.5` for side-by-side Request | Response views
- Burp's `HttpRequestEditor` / `HttpResponseEditor` (from `api.userInterface()`) already provide Pretty/Raw/Hex tabs and search
- Wrap each editor in a `JPanel(BorderLayout)` with a bold `JLabel` header in `NORTH`
- Never use `JTabbedPane` to switch between Request/Response — show both simultaneously

## List + Action Panel

Standard layout for editable lists/tables with side action buttons.

```
┌─────────────────────────────────────────────────┐
│ ☑ Checkbox label (optional, row 0, gridwidth=3) │
│ ┌──────────┐   ┌──────────────────────────────┐│
│ │  Add     │ 5 │ Col1 │ Col2 │ Col3 │ Col4   ││
│ │  Edit    │ px│──────┼──────┼──────┼────────││
│ │  Remove  │   │ ...  │ ...  │ ...  │ ...    ││
│ │  Up      │   │      │      │      │        ││
│ │  Down    │   │ gridheight spans all btn rows││
│ └──────────┘   └──────────────────────────────┘│
│ ┌─────────┐   ┌──────────────────────────────┐ │
│ │  Add    │   │ Enter a new item (optional)  │ │
│ └─────────┘   └──────────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

Uses **GridBagLayout** on the panel itself — buttons and table are direct children (no separate button sub-panel).

```java
GridBagLayout layout = new GridBagLayout();
layout.columnWidths = new int[]{0, 5, 0};   // buttons | 5px spacer | table
layout.rowHeights = new int[]{0, 5, 0, 5, 0, 5, 0, 5, 0, 5, 0};  // alternating content/spacer
setLayout(layout);

GridBagConstraints gbc = new GridBagConstraints();

// Optional checkbox — row 0, spans full width
gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3;
gbc.fill = GridBagConstraints.HORIZONTAL; gbc.anchor = GridBagConstraints.NORTHWEST;
add(enableCheckbox, gbc);

// Buttons — col 0, even rows starting at 2, fill=HORIZONTAL (auto-equalizes width)
gbc = new GridBagConstraints();
gbc.gridx = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.anchor = GridBagConstraints.NORTHWEST;
int row = 2;
for (JButton btn : List.of(addBtn, editBtn, removeBtn, upBtn, downBtn)) {
    gbc.gridy = row;
    add(btn, gbc);
    row += 2;  // skip spacer rows
}

// Table — col 2, spans all button rows, gets all horizontal space
gbc = new GridBagConstraints();
gbc.gridx = 2; gbc.gridy = 2;
gbc.gridheight = (buttons.size() * 2) - 1;  // span all button + spacer rows
gbc.fill = GridBagConstraints.VERTICAL;
gbc.anchor = GridBagConstraints.NORTHWEST;
gbc.weightx = 1.0;
add(tableScrollPane, gbc);

// Optional input row — below buttons/table
gbc = new GridBagConstraints();
gbc.gridy = row; gbc.gridx = 0;
gbc.fill = GridBagConstraints.HORIZONTAL; gbc.anchor = GridBagConstraints.NORTHWEST;
add(addItemBtn, gbc);
gbc.gridx = 2;
add(textField, gbc);
```

**Key rules:**
- No `setPreferredSize`/`setMaximumSize` on buttons — `fill=HORIZONTAL` auto-equalizes width to widest button
- `columnWidths` spacer column (5px) replaces manual gaps
- `rowHeights` alternating `{0, 5, 0, 5, ...}` for consistent vertical spacing
- Table's `weightx=1.0` takes all extra horizontal space
- Disable Edit/Remove/Up/Down when no selection; Up on first row, Down on last

---

## Burp Button Styling (FlatLaf)

- Apply primary (orange) style: `button.putClientProperty("FlatLaf.styleClass", "primary")`
- Remove style: `button.putClientProperty("FlatLaf.styleClass", null)`
- Available styles: `"primary"`, `"destructive"`, `"ai"` (from `BurpLaf.properties` `[style]Button.*`)
- Theme chain: `LightTheme.json` swatches → palette → `BurpLaf.properties` variables → `FlatLaf.styleClass`
- Do NOT use `JToggleButton` with primary style — selected state uses darker `selectedBackground`; use `JButton` with manual boolean state instead

## Burp API Gotchas

- Use `request.toByteArray().getBytes()` / `response.toByteArray().getBytes()` for raw bytes — never `.toString().getBytes()` (loses binary data, encoding-dependent)

## Build

- `./gradlew spotlessApply build` — format then build (always run spotlessApply before build)
- Spotless handles formatting only (indentation, line breaks, import ordering) — does NOT add imports or shorten FQN

## Code Style

- Always use imports, never fully qualified class names inline (e.g. `DedupComponent.class` not `com.vigolium.extension.dedup.DedupComponent.class`)

---

## Architecture: Direct Calls + PropertyChange

```
  ┌───────────┐    ┌────────────┐
  │  Services │    │ Controllers│
  │ (concrete)│◄───┤ (concrete) │
  └───────────┘    └─────┬──────┘
                         │ direct method calls
                 ┌───────┴───────┐
                 │               │
         ┌───────▼──────┐  ┌────▼───────┐
         │PropertyChange│  │   Views    │
         │+ Callbacks   │◄─┤  (JPanel)  │
         └──────────────┘  └────┬───────┘
                                │
                       ┌────────▼────────┐
                       │  Data Models    │
                       │  Domain objects │
                       │  Factory-created│
                       └─────────────────┘
```

### Principles

- **Views are concrete classes** (extend `JPanel`), never interfaces
- **Controllers call views directly** — no intermediary layer
- **PropertyChangeListener is primary** inter-component communication
- **Constructor parameters** — pass dependencies directly, no DI framework needed
- **Factory classes** for creating complex objects with mixed deps
- **Manual model-view sync** — no auto-binding framework
- **Distributed state** via PropertyChange + direct calls, not centralized store

### Communication Patterns (by priority)

| Pattern                      | When to Use                                     |
| ---------------------------- | ----------------------------------------------- |
| **Direct method calls**      | Controller → View, within same feature          |
| **PropertyChangeListener**   | UI state sync, visibility, split panes, theme   |
| **ChangeListener callbacks** | Parent-child, model-view within same feature    |
| **Event Bus**                | Cross-feature async events only (use sparingly) |

### Project Structure

```
src/main/java/com/myapp/
├── model/              # Domain objects, POJOs
├── service/            # Business logic
├── factory/            # Object creation
├── controller/         # Coordinators
├── ui/
│   ├── panel/          # Reusable panels
│   ├── table/          # Table models, column defs, renderers
│   ├── dialog/
│   └── MainFrame.java
└── config/             # Settings interface
```

---

## Inter-Component Communication

### 1. PropertyChangeListener — Primary

```java
// Fire
firePropertyChange("hidden", oldValue, newValue);

// Listen
component.addPropertyChangeListener("hidden", e -> {
    tab.setVisible(!(Boolean) e.getNewValue());
});
```

**Common properties:** `"hidden"`, `"title"`, `"dividerLocation"`, `"detachedWindowId"`

### 2. ChangeListener Callbacks — Parent-Child

```java
class SelectionModel {
    private final List<ChangeListener> listeners = new CopyOnWriteArrayList<>();

    void addChangeListener(ChangeListener l) { listeners.add(l); }

    void setSelected(Object item) {
        this.selected = item;
        ChangeEvent e = new ChangeEvent(this);
        listeners.forEach(l -> l.stateChanged(e));
    }
}
```

Use `CopyOnWriteArrayList` for thread-safe listener storage.

### 3. Event Bus — Cross-Feature Only

```java
class EventBus {
    <T> Subscription subscribe(Class<T> eventType, Consumer<T> handler);
    void post(Object event);
    void postAsync(Object event);
}

// Programmatic registration — NO @Subscribe annotations
Subscription sub = eventBus.subscribe(TaskCompleted.class, this::onTaskDone);
sub.unsubscribe();
```

---

## Component Lifecycle

### Constructor-Only Initialization

```java
class FeaturePanel extends JPanel {
    FeaturePanel(SelectionModel model, AppSettings settings) {
        super(new BorderLayout());

        // 1. Store dependencies
        this.model = model;

        // 2. Create child components
        this.scrollPane = new JScrollPane();
        this.scrollPane.getVerticalScrollBar().setUnitIncrement(25);

        // 3. Assemble layout
        add(scrollPane, BorderLayout.CENTER);

        // 4. Register listeners (LAST)
        model.addChangeListener(this::onSelectionChanged);

        // 5. Set component name (ALWAYS)
        setName("featurePanel");
    }
}
```

### Disposal

```java
@Override
public void dispose() {
    Toolkit.getDefaultToolkit().removeAWTEventListener(this.awtListener);
    registry.unregister(this.subscription);
    super.dispose();
}
```

---

## Factory Pattern

Use factory classes for creating objects that need shared services + runtime params:

```java
class TabFactory {
    private final HttpClient httpClient;
    private final AppSettings settings;

    TabFactory(HttpClient httpClient, AppSettings settings) {
        this.httpClient = httpClient;
        this.settings = settings;
    }

    EditorTab create(HttpRequest request, String name) {
        return new EditorTab(httpClient, settings, request, name);
    }
}
```

---

## Table System

### Rich Column Definitions

```java
@FunctionalInterface
interface ValueAccessor<E> {
    Object extract(E row);
}

class ColumnDef<E> {
    String name;
    Class<?> type;
    ValueAccessor<E> accessor;      // Functional, NOT reflection
    ValueMutator<E> mutator;        // Optional
    TableCellRenderer renderer;
    TableCellEditor editor;
    int width, preferredWidth, minWidth, maxWidth;
    boolean resizable, visible, sortable, hidable, editable, searchable, filterable;
    Comparator<?> comparator;
    SortOrder defaultSortOrder;
    String tooltip;
}
```

### Column Builder

```java
ColumnDef<HttpRequest> col = ColumnDef.<HttpRequest>builder()
    .name("Method")
    .type(String.class)
    .accessor(req -> req.method())
    .width(60)
    .sortable(true)
    .comparator(String.CASE_INSENSITIVE_ORDER)
    .build();
```

### Base Table Model (EDT-Safe)

```java
abstract class BaseTableModel<E> extends AbstractTableModel {
    protected final ColumnConfig<E> columns;

    protected BaseTableModel(ColumnConfig<E> columns) {
        this.columns = columns;
        columns.addStructureListener(this::fireTableStructureChanged);
    }

    @Override public int getColumnCount() { return columns.getAll().size(); }
    @Override public String getColumnName(int col) { return columns.getAll().get(col).name(); }
    @Override public Class<?> getColumnClass(int col) { return columns.getAll().get(col).type(); }

    @Override
    public Object getValueAt(int row, int col) {
        return columns.getAll().get(col).accessor().extract(getRow(row));
    }

    protected abstract E getRow(int row);

    // EDT marshalling built into base
    @Override
    public void fireTableRowsInserted(int first, int last) {
        if (SwingUtilities.isEventDispatchThread()) {
            super.fireTableRowsInserted(first, last);
        } else {
            try { SwingUtilities.invokeAndWait(() -> super.fireTableRowsInserted(first, last)); }
            catch (Exception e) { throw new RuntimeException(e); }
        }
    }
}
```

### Type-Dispatch Renderer

```java
class TypeDispatchRenderer implements TableCellRenderer {
    private static final TableCellRenderer ICON = new IconRenderer();
    private static final TableCellRenderer NUMBER = new NumberRenderer();
    private static final TableCellRenderer BOOLEAN = new BooleanRenderer();
    private static final TableCellRenderer DATETIME = new DateTimeRenderer();
    private final TableCellRenderer fallback;

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean sel, boolean focus, int row, int col) {
        TableCellRenderer r = switch (value) {
            case Icon i -> ICON;
            case Number n -> NUMBER;
            case Boolean b -> BOOLEAN;
            case TemporalAccessor t -> DATETIME;
            default -> fallback;
        };
        return r.getTableCellRendererComponent(table, value, sel, focus, row, col);
    }
}
```

### 3-Way Sort Toggle

```java
class ThreeWayRowSorter<M extends TableModel> extends TableRowSorter<M> {
    @Override
    public void toggleSortOrder(int col) {
        List<SortKey> keys = getSortKeys();
        if (!keys.isEmpty() && keys.get(0).getSortOrder() == SortOrder.DESCENDING) {
            setSortKeys(null);  // unsorted
        } else {
            super.toggleSortOrder(col);  // unsorted→asc, asc→desc
        }
    }
}
```

### Selection Handling

```java
table.getSelectionModel().addListSelectionListener(e -> {
    if (!e.getValueIsAdjusting()) {
        int viewRow = table.getSelectedRow();
        if (viewRow >= 0) {
            int modelRow = table.convertRowIndexToModel(viewRow);  // ALWAYS convert
            controller.onSelected(model.getRow(modelRow));
        }
    }
});
```

### Empty State

```java
class AppTable extends JTable {
    private Component emptyState;

    void setEmptyState(Component comp) {
        if (emptyState != null) remove(emptyState);
        emptyState = comp;
        if (comp != null) {
            add(comp);
            comp.setVisible(getModel().getRowCount() == 0);
        }
        setFillsViewportHeight(comp != null);
    }
}
```

### Data Flow

```
Domain Object → ColumnConfig (functional accessors) → BaseTableModel (EDT-safe)
→ ThreeWayRowSorter → JTable (type-dispatch renderers, XSS protection)
→ ListSelectionListener (convertRowIndexToModel) → Controller
```

---

## Settings

### Flat Interface with Sub-Interface Grouping

```java
interface AppSettings extends ProxySettings, ScanSettings {
    void addChangeListener(Runnable listener);
}

interface ProxySettings {
    boolean isProxyEnabled();
    void setProxyEnabled(boolean value);
    int getProxyPort();
    void setProxyPort(int value);
}
```

### Preferences Storage (Flat Key-Value)

```java
interface Preferences {
    String getString(String key);
    void setString(String key, String value);
    Boolean getBoolean(String key);
    void setBoolean(String key, boolean value);
    Integer getInteger(String key);
    void setInteger(String key, int value);
    Set<String> stringKeys();
    void deleteString(String key);
}
```

---

## Threading / EDT

### EDT Check

```java
Preconditions.check(EventQueue.isDispatchThread(), "Must be on EDT");
```

### EDT-Safe Methods

```java
public void setVisible(boolean visible) {
    if (SwingUtilities.isEventDispatchThread()) {
        super.setVisible(visible);
    } else {
        SwingUtilities.invokeLater(() -> setVisible(visible));
    }
}
```

### Background Work

```java
ExecutorService pool = Executors.newFixedThreadPool(8, new NamedThreadFactory("worker"));

CompletableFuture.supplyAsync(() -> heavyWork(), pool)
    .thenAcceptAsync(result -> updateUI(result), SwingUtilities::invokeLater);
```

---

## Enum Display in JComboBox

Override `toString()` to return `label` — JComboBox renders via `toString()`. Gson serializes by `name()` so this is safe for persistence.

---

## XSS Protection

Disable HTML rendering everywhere:

```java
// Renderers
putClientProperty("html.disable", Boolean.TRUE);

// Tooltips
@Override public JToolTip createToolTip() {
    JToolTip tip = super.createToolTip();
    tip.putClientProperty("html.disable", Boolean.TRUE);
    return tip;
}

// EditorPanes
editorPane.putClientProperty("JEditorPane.honorDisplayProperties", Boolean.TRUE);
```

---

## Look & Feel

- **FlatLaf** theme with custom subclass
- Animated transitions: `FlatAnimatedLafChange.showSnapshot()` → update → `hideSnapshotWithAnimation()`
- All colors via `UIManager.getColor(key)`, all fonts via `UIManager.getFont("defaultFont")`
- Never hardcode colors or font families

---

## Color System

All from `UIManager`. Never hardcode.

| Palette | Key Prefix                 | Usage                       |
| ------- | -------------------------- | --------------------------- |
| Primary | `Colors.palette.primary.*` | Brand / accent              |
| Mono    | `Colors.palette.mono.*`    | Grayscale (1=light, 8=dark) |
| Error   | `Colors.palette.error.*`   | Destructive                 |
| Success | `Colors.palette.success.*` | Success                     |
| Warning | `Colors.palette.warning.*` | Warning                     |
| Info    | `Colors.palette.info.*`    | Informational               |

| Semantic Key             | Usage              |
| ------------------------ | ------------------ |
| `Colors.ui.background.1` | Primary background |
| `Colors.ui.background.2` | Secondary / nested |
| `Colors.ui.background.6` | Selected state     |
| `Colors.ui.text.body`    | Body text          |
| `Colors.ui.text.header`  | Header text        |

---

## Font System

| Style  | Code                                                    |
| ------ | ------------------------------------------------------- |
| Body   | `UIManager.getFont("defaultFont")`                      |
| Bold   | `base.deriveFont(Font.BOLD)`                            |
| Title  | `base.deriveFont(Font.BOLD, base.getSize() + 4f)`       |
| Header | `base.deriveFont(Font.BOLD, base.getSize() + 8f)`       |
| Small  | `base.deriveFont(base.getSize() - 2f)`                  |
| Mono   | `new Font(Font.MONOSPACED, Font.PLAIN, base.getSize())` |

---

## Spacing

| px  | Usage                               |
| --- | ----------------------------------- |
| 5   | Minimal gap                         |
| 10  | Standard gap                        |
| 15  | Dialog padding                      |
| 20  | Section padding                     |
| 25  | Scroll increment (all scroll panes) |

---

## Layout

### BorderLayout (Default)

```java
new JPanel(new BorderLayout())
new JPanel(new BorderLayout(10, 0))   // hgap
new JPanel(new BorderLayout(0, 20))   // vgap
```

### GridBagLayout (Forms)

Gaps are baked into `columnWidths`/`rowHeights` arrays — **no `Insets` on individual components**. A `Box.Filler` with `weightx=1.0` in the last column pushes content left.

```java
GridBagLayout layout = new GridBagLayout();
layout.columnWidths = new int[]{0, 5, 0, 5, 0, 5, 0};  // label | 5px | field | 5px | suffix/error | 5px | filler
layout.rowHeights = new int[]{0, 5, 0, 5, 0};           // alternating content/spacer rows
form.setLayout(layout);

GridBagConstraints gbc = new GridBagConstraints();

// Label — col 0, anchor=LINE_START
gbc.gridx = 0; gbc.gridy = 0;
gbc.anchor = GridBagConstraints.LINE_START;
form.add(new JLabel("Max connections:"), gbc);

// Small number field — col 2, setColumns(5), anchor=ABOVE_BASELINE (baseline-aligns with label)
JTextField field = new JTextField();
field.setColumns(5);
gbc = new GridBagConstraints();
gbc.gridx = 2; gbc.gridy = 0;
gbc.anchor = GridBagConstraints.ABOVE_BASELINE;
form.add(field, gbc);

// Wide text field — use setColumns(35) + fill=HORIZONTAL instead
// gbc.fill = GridBagConstraints.HORIZONTAL;

// Checkbox + inline field + suffix label (all same gridy)
JCheckBox cb = new JCheckBox("Timeout after");
cb.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
cb.setMargin(new Insets(0, 0, 0, 0));
gbc = new GridBagConstraints();
gbc.gridx = 0; gbc.gridy = 4;
gbc.anchor = GridBagConstraints.LINE_START;
form.add(cb, gbc);
// field at gridx=2, suffix JLabel("seconds") at gridx=4

// Filler — last column, pushes all content left
gbc = new GridBagConstraints();
gbc.gridx = 6; gbc.gridy = 0; gbc.weightx = 1.0;
form.add(new Box.Filler(new Dimension(0, 0), new Dimension(0, 0), new Dimension(0, Integer.MAX_VALUE)), gbc);
```

**Key rules:**
- Number inputs: `setColumns(5)`, never `setPreferredSize`
- Labels: `anchor=LINE_START` — Fields: `anchor=ABOVE_BASELINE` (baseline alignment)
- Checkbox inline rows: zero margin+border on checkbox for tight alignment

---

## Borders

| Context        | Insets           |
| -------------- | ---------------- |
| Standard panel | `10, 10, 10, 10` |
| Dialog         | `15, 15, 15, 15` |
| Main panel     | `10, 10, 10, 10` |
| Compact        | `5, 5, 5, 5`     |

**Important:** Child panels inside a scrollable container (e.g. panels added to a `BoxLayout` content pane within a `JScrollPane`) must have `0, 0, 0, 0` border — only the outer container sets padding. Never double-pad.

```java
// Separator + padding
BorderFactory.createCompoundBorder(
    BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Colors.palette.mono.5")),
    BorderFactory.createEmptyBorder(10, 0, 10, 0)
)
```

---

## Component Rules

### ScrollPanes

```java
scrollPane.getVerticalScrollBar().setUnitIncrement(25);
scrollPane.getHorizontalScrollBar().setUnitIncrement(25);
scrollPane.setBorder(BorderFactory.createEmptyBorder());
```

### Tables

```java
table.setAutoCreateRowSorter(true);
table.setShowVerticalLines(false);
table.setShowHorizontalLines(false);
table.setIntercellSpacing(new Dimension(0, 0));
table.setFillsViewportHeight(true);
table.setPreferredScrollableViewportSize(
        new Dimension(table.getPreferredScrollableViewportSize().width, table.getRowHeight() * 10));
```

**Viewport sizing:** All tables must limit visible rows to 10 with `setPreferredScrollableViewportSize`. Place the JScrollPane in `BorderLayout.NORTH` (not CENTER) so it respects preferred size instead of expanding.

**Nested scroll fix (MUST for every table inside a JScrollPane):** When a table's JScrollPane is nested inside an outer JScrollPane, the inner one consumes wheel events even when there's nothing to scroll. Always disable default wheel handling and forward to parent at boundaries:

```java
scrollPane.setWheelScrollingEnabled(false);
scrollPane.addMouseWheelListener(e -> {
    JScrollBar bar = scrollPane.getVerticalScrollBar();
    if (bar.isVisible() && bar.getValue() > 0
            && bar.getValue() + bar.getVisibleAmount() < bar.getMaximum()) {
        bar.setValue(bar.getValue() + e.getWheelRotation() * bar.getUnitIncrement() * e.getScrollAmount());
    } else {
        Container parent = scrollPane.getParent();
        if (parent != null) {
            parent.dispatchEvent(SwingUtilities.convertMouseEvent(scrollPane, e, parent));
        }
    }
});
```

### SplitPanes

```java
split.setResizeWeight(0.45);
split.setBorder(null);
split.setDividerSize(5);
```

---

## Naming Convention

All components MUST call `setName()`:

- **Format:** `contextPrefix` + `ComponentType` in camelCase
- **Examples:** `"settingsWindow"`, `"scanResultTable"`, `"proxyInterceptSplitPane"`, `"liveCaptureStopButton"`, `"commandPaletteMenuItem_" + i`

---

## Keyboard Shortcuts

### Direct

```java
inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "edit");
actionMap.put("edit", editAction);
```

### Window-Level

```java
getRootPane().getInputMap(WHEN_IN_FOCUSED_WINDOW)
    .put(KeyStroke.getKeyStroke(VK_ESCAPE, 0), "escape");
getRootPane().getActionMap().put("escape", closeAction);
```

### Visibility-Scoped (HierarchyListener)

Register when showing, unregister when hidden:

```java
component.addHierarchyListener(e -> {
    if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
        JRootPane root = SwingUtilities.getRootPane(component);
        if (root == null) return;
        InputMap im = root.getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();
        KeyStroke key = KeyStroke.getKeyStroke(VK_S, shortcutMask);
        if (component.isShowing()) { im.put(key, "save"); am.put("save", saveAction); }
        else { im.remove(key); am.remove("save"); }
    }
});
```

### Platform Mask

```java
int shortcutMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
```

---

## Timer Lifecycle (AncestorListener)

```java
record TimerLifecycle(Timer timer) implements AncestorListener {
    @Override public void ancestorAdded(AncestorEvent e)   { timer.start(); }
    @Override public void ancestorRemoved(AncestorEvent e)  { timer.stop(); }
    @Override public void ancestorMoved(AncestorEvent e)    {}
}

component.addAncestorListener(new TimerLifecycle(new Timer(25, e -> animate())));
```

---

## Event Listeners

Lambdas and method references. Avoid anonymous inner classes.

```java
button.addActionListener(e -> doSomething());
button.addActionListener(this::handleClick);
```

Multi-interface for complex listeners:

```java
class Autocomplete implements MouseListener, ListSelectionListener, CaretListener { ... }
```

---

## Context Menus

```java
JPopupMenu popup = new JPopupMenu();
popup.addPopupMenuListener(new PopupMenuListener() {
    @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        popup.removeAll();
        popup.add(new JMenuItem("Copy"));
    }
    @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}
    @Override public void popupMenuCanceled(PopupMenuEvent e) {}
});
component.setComponentPopupMenu(popup);
```

---

## Error Handling

Inline error components, NOT dialogs or toasts:

```java
void handleError(ErrorType type) {
    addErrorComponent(new ErrorPanel(type.title(), type.message(), retryAction));
    revalidate();
    repaint();
}
```

---

## Focus Delegation

```java
class SearchPanel extends JPanel {
    @Override public void requestFocus() { searchField.requestFocus(); }
    @Override public boolean requestFocusInWindow() { return searchField.requestFocusInWindow(); }
}
```

---

## Undo/Redo

```java
class SmartUndoManager extends UndoManager {
    private CompoundEdit currentEdit;
    private int lastCaretPos;

    @Override
    public void undoableEditHappened(UndoableEditEvent e) {
        int delta = Math.abs(editor.getCaretPosition() - lastCaretPos);
        if (currentEdit != null && delta <= 1) {
            currentEdit.addEdit(e.getEdit());
        } else {
            if (currentEdit != null) currentEdit.end();
            currentEdit = new CompoundEdit();
            currentEdit.addEdit(e.getEdit());
            addEdit(currentEdit);
        }
        lastCaretPos = editor.getCaretPosition();
    }
}
```

---

## Anti-Patterns

- **No `InputVerifier`** — use `DocumentFilter` or manual validation
- **No `@Subscribe` annotations** — programmatic registration only
- **No centralized state store** — distributed via PropertyChange
- **No view interfaces** — concrete JPanel subclasses
- **No reflection-based binding** — functional `ValueAccessor<E>` lambdas
- **No blocking on EDT** — background work via ExecutorService + CompletableFuture
- **No `revalidate()/repaint()` after table data changes** — `fireTable*()` handles it; only for structural UI changes (add/remove components)
