import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * CurrencyConverter.java
 *
 * A single-file Java Swing currency converter with a clean, modern UI and
 * editable exchange rates. Designed to run with a standard JDK (no external
 * libraries required).
 *
 * How it works:
 * - Rates are stored as value relative to USD (1 USD = rate). Conversion is
 *   done by converting "from" -> USD -> "to".
 * - A "Manage Rates" dialog lets you add/edit/remove currency entries and
 *   save/load them to disk (rates.properties).
 *
 * Build & run:
 *   javac CurrencyConverter.java
 *   java CurrencyConverter
 */
public class CurrencyConverter extends JFrame {
    private final JComboBox<String> cbFrom;
    private final JComboBox<String> cbTo;
    private final JTextField tfAmount;
    private final JLabel lblResult;
    private final Map<String, Double> rates; // relative to USD
    private final DecimalFormat df = new DecimalFormat("#,##0.00");
    private final File ratesFile = new File(System.getProperty("user.home"), ".currency_rates.properties");

    public CurrencyConverter() {
        super("Currency Converter");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(450, 220));

        // friendly default rates (1 USD = rate)
        rates = new LinkedHashMap<>();
        setDefaultRates();
        loadRatesIfExists();

        cbFrom = new JComboBox<>(rates.keySet().toArray(new String[0]));
        cbTo = new JComboBox<>(rates.keySet().toArray(new String[0]));
        tfAmount = new JTextField();
        lblResult = new JLabel(" ");

        initUI();
        pack();
        setLocationRelativeTo(null);
    }

    private void setDefaultRates() {
        rates.clear();
        rates.put("USD", 1.00);
        rates.put("EUR", 0.92);
        rates.put("GBP", 0.79);
        rates.put("JPY", 156.20);
        rates.put("INR", 83.15);
        rates.put("AUD", 1.54);
        rates.put("CAD", 1.36);
        rates.put("CHF", 0.91);
        rates.put("CNY", 6.38);
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Header
        JLabel header = new JLabel("Currency Converter");
        header.setFont(header.getFont().deriveFont(Font.BOLD, 18f));
        header.setHorizontalAlignment(SwingConstants.CENTER);
        root.add(header, BorderLayout.NORTH);

        // Center form
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.fill = GridBagConstraints.HORIZONTAL;

        g.gridx = 0; g.gridy = 0; g.weightx = 0.0;
        form.add(new JLabel("Amount:"), g);
        g.gridx = 1; g.gridy = 0; g.gridwidth = 2; g.weightx = 1.0;
        form.add(tfAmount, g);

        g.gridx = 0; g.gridy = 1; g.gridwidth = 1; g.weightx = 0.0;
        form.add(new JLabel("From:"), g);
        g.gridx = 1; g.gridy = 1; g.weightx = 1.0;
        form.add(cbFrom, g);

        JButton btnSwap = new JButton("⇄");
        btnSwap.setToolTipText("Swap From/To");
        g.gridx = 2; g.gridy = 1; g.weightx = 0.0; g.fill = GridBagConstraints.NONE;
        form.add(btnSwap, g);
        g.fill = GridBagConstraints.HORIZONTAL;

        g.gridx = 0; g.gridy = 2; g.weightx = 0.0;
        form.add(new JLabel("To:"), g);
        g.gridx = 1; g.gridy = 2; g.gridwidth = 2; g.weightx = 1.0;
        form.add(cbTo, g);

        root.add(form, BorderLayout.CENTER);

        // Footer controls
        JPanel footer = new JPanel(new BorderLayout(8,8));
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton btnConvert = new JButton("Convert");
        JButton btnClear = new JButton("Clear");
        JButton btnManage = new JButton("Manage Rates");
        leftButtons.add(btnConvert);
        leftButtons.add(btnClear);
        leftButtons.add(btnManage);

        footer.add(leftButtons, BorderLayout.WEST);

        JPanel resultPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        resultPanel.add(lblResult);
        lblResult.setFont(lblResult.getFont().deriveFont(Font.BOLD, 14f));
        footer.add(resultPanel, BorderLayout.EAST);

        root.add(footer, BorderLayout.SOUTH);

        add(root);

        // Defaults & actions
        tfAmount.setToolTipText("Enter amount to convert (numbers only)");
        tfAmount.addActionListener(e -> doConversion());
        btnConvert.addActionListener(e -> doConversion());
        btnClear.addActionListener(e -> {
            tfAmount.setText("");
            lblResult.setText(" ");
        });
        btnSwap.addActionListener(e -> {
            int fi = cbFrom.getSelectedIndex();
            int ti = cbTo.getSelectedIndex();
            cbFrom.setSelectedIndex(ti);
            cbTo.setSelectedIndex(fi);
        });
        btnManage.addActionListener(e -> openManageDialog());

        // keyboard mnemonics
        btnConvert.setMnemonic(KeyEvent.VK_C);
        btnClear.setMnemonic(KeyEvent.VK_L);
        btnManage.setMnemonic(KeyEvent.VK_M);

        // handy default selection
        cbFrom.setSelectedItem("USD");
        cbTo.setSelectedItem("EUR");
    }

    private void doConversion() {
        String amtText = tfAmount.getText().trim();
        if (amtText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter an amount.", "Input required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amtText.replaceAll(",", ""));
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter a valid number (e.g. 1234.56).", "Invalid number", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String from = (String) cbFrom.getSelectedItem();
        String to = (String) cbTo.getSelectedItem();
        if (from == null || to == null) return;

        double fromRate = rates.getOrDefault(from, 1.0);
        double toRate = rates.getOrDefault(to, 1.0);

        // Convert: amount in FROM -> USD -> TO
        double inUsd = amount / fromRate; // because rates are 1 USD = rate
        double converted = inUsd * toRate;

        lblResult.setText(df.format(amount) + " " + from + " → " + df.format(converted) + " " + to);
    }

    private void openManageDialog() {
        JDialog dlg = new JDialog(this, "Manage Rates", true);
        RatesTableModel model = new RatesTableModel(rates);
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);

        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(new EmptyBorder(8,8,8,8));
        top.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnAdd = new JButton("Add");
        JButton btnRemove = new JButton("Remove");
        JButton btnReset = new JButton("Reset Defaults");
        JButton btnSave = new JButton("Save & Close");
        controls.add(btnAdd);
        controls.add(btnRemove);
        controls.add(btnReset);
        controls.add(btnSave);

        dlg.add(top, BorderLayout.CENTER);
        dlg.add(controls, BorderLayout.SOUTH);
        dlg.setSize(520, 360);
        dlg.setLocationRelativeTo(this);

        btnAdd.addActionListener(e -> {
            model.addRow("NEW", 1.0);
            int last = model.getRowCount()-1;
            table.getSelectionModel().setSelectionInterval(last, last);
        });
        btnRemove.addActionListener(e -> {
            int r = table.getSelectedRow();
            if (r >= 0) model.removeRow(r);
        });
        btnReset.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(dlg, "Reset to built-in default rates?", "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                setDefaultRates();
                model.replaceAll(rates);
            }
        });
        btnSave.addActionListener(e -> {
            model.applyTo(rates);
            persistRates();
            refreshCurrencyCombos();
            dlg.dispose();
        });

        dlg.setVisible(true);
    }

    private void refreshCurrencyCombos() {
        String selFrom = (String) cbFrom.getSelectedItem();
        String selTo = (String) cbTo.getSelectedItem();
        cbFrom.setModel(new DefaultComboBoxModel<>(rates.keySet().toArray(new String[0])));
        cbTo.setModel(new DefaultComboBoxModel<>(rates.keySet().toArray(new String[0])));
        cbFrom.setSelectedItem(selFrom != null && rates.containsKey(selFrom) ? selFrom : rates.keySet().iterator().next());
        cbTo.setSelectedItem(selTo != null && rates.containsKey(selTo) ? selTo : rates.keySet().iterator().next());
    }

    private void persistRates() {
        try (OutputStream os = new FileOutputStream(ratesFile)) {
            Properties p = new Properties();
            for (Map.Entry<String, Double> e : rates.entrySet()) {
                p.setProperty(e.getKey(), Double.toString(e.getValue()));
            }
            p.store(os, "Currency rates (1 USD = rate)");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save rates: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadRatesIfExists() {
        if (!ratesFile.exists()) return;
        try (InputStream is = new FileInputStream(ratesFile)) {
            Properties p = new Properties();
            p.load(is);
            Map<String, Double> loaded = new LinkedHashMap<>();
            for (String name : p.stringPropertyNames()) {
                try {
                    double v = Double.parseDouble(p.getProperty(name));
                    loaded.put(name, v);
                } catch (NumberFormatException ignore) {}
            }
            if (!loaded.isEmpty()) {
                rates.clear();
                rates.putAll(loaded);
            }
        } catch (IOException ignore) {}
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // use system look & feel for a more native/clean look
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignore) {}
            new CurrencyConverter().setVisible(true);
        });
    }

    // Table model to edit rates
    static class RatesTableModel extends AbstractTableModel {
        private final String[] cols = {"Currency", "Rate (1 USD = rate)"};
        private java.util.List<String> keys = new java.util.ArrayList<>();
        private java.util.List<Double> vals = new java.util.ArrayList<>();

        RatesTableModel(Map<String, Double> source) {
            replaceAll(source);
        }

        void replaceAll(Map<String, Double> source) {
            keys.clear(); vals.clear();
            for (Map.Entry<String, Double> e : source.entrySet()) {
                keys.add(e.getKey()); vals.add(e.getValue());
            }
            fireTableDataChanged();
        }

        void addRow(String code, double rate) {
            keys.add(code);
            vals.add(rate);
            fireTableRowsInserted(keys.size()-1, keys.size()-1);
        }

        void removeRow(int row) {
            if (row >= 0 && row < keys.size()) {
                keys.remove(row);
                vals.remove(row);
                fireTableRowsDeleted(row, row);
            }
        }

        void applyTo(Map<String, Double> dest) {
            dest.clear();
            for (int i = 0; i < keys.size(); i++) {
                String k = keys.get(i).trim().toUpperCase();
                if (k.isEmpty()) continue;
                double v = vals.get(i);
                dest.put(k, v);
            }
        }

        @Override
        public int getRowCount() { return keys.size(); }

        @Override
        public int getColumnCount() { return cols.length; }

        @Override
        public String getColumnName(int column) { return cols[column]; }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return columnIndex == 0 ? keys.get(rowIndex) : vals.get(rowIndex);
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) { return true; }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                keys.set(rowIndex, aValue.toString());
            } else {
                try {
                    double v = Double.parseDouble(aValue.toString());
                    vals.set(rowIndex, v);
                } catch (NumberFormatException ignore) {}
            }
            fireTableCellUpdated(rowIndex, columnIndex);
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) { return columnIndex == 0 ? String.class : Double.class; }
    }
}
