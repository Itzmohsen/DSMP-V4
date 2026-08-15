package TwitterGatherDataFollowers.userRyersonU;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

final class UciRetailImportPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private static final Color INK = StudioNightTheme.adaptiveColor(
            new Color(31, 41, 55), StudioNightTheme.NIGHT_TEXT);
    private static final Color MUTED = StudioNightTheme.adaptiveColor(
            new Color(100, 116, 139), StudioNightTheme.NIGHT_MUTED);
    private static final Color LINE = StudioNightTheme.adaptiveColor(
            new Color(203, 213, 225), StudioNightTheme.NIGHT_BORDER);
    private static final Color SURFACE = StudioNightTheme.adaptiveColor(
            new Color(248, 250, 252), StudioNightTheme.NIGHT_BACKGROUND);
    private static final Color CARD = StudioNightTheme.adaptiveColor(
            Color.WHITE, StudioNightTheme.NIGHT_SURFACE);
    private static final Color BLUE = StudioNightTheme.adaptiveColor(
            new Color(82, 113, 224), StudioNightTheme.NIGHT_BLUE);
    private static final Color BLUE_DARK = StudioNightTheme.adaptiveColor(
            new Color(58, 82, 181), new Color(130, 183, 255));
    private static final Color GREEN = StudioNightTheme.adaptiveColor(
            new Color(74, 145, 107), StudioNightTheme.NIGHT_GREEN);
    private static final Color AMBER = StudioNightTheme.adaptiveColor(
            new Color(177, 112, 37), StudioNightTheme.NIGHT_AMBER);
    private static final Color SOFT_BLUE = StudioNightTheme.adaptiveColor(
            new Color(238, 243, 255), StudioNightTheme.NIGHT_SURFACE_ALT);
    private static final Color SOFT_GREEN = StudioNightTheme.adaptiveColor(
            new Color(237, 248, 242), StudioNightTheme.NIGHT_PANEL_INK);
    private static final Color SOFT_AMBER = StudioNightTheme.adaptiveColor(
            new Color(255, 248, 232), StudioNightTheme.NIGHT_PANEL_INK);

    private final ControllerAgentGui owner;

    private UciRetailDatasetImporter.DatasetKind selectedDatasetKind =
            UciRetailDatasetImporter.DatasetKind.RETAIL;
    private final Map<UciRetailDatasetImporter.DatasetKind, DatasetKindButton> datasetKindButtons =
            new EnumMap<UciRetailDatasetImporter.DatasetKind, DatasetKindButton>(
                    UciRetailDatasetImporter.DatasetKind.class);

    private JTextField fileField;
    private JTextField outputDirField;
    private JTextField outputNameField;
    private JComboBox<UciRetailDatasetImporter.MappingProfile> profileCombo;
    private JComboBox<String> invoiceCombo;
    private JComboBox<String> stockCombo;
    private JComboBox<String> descriptionCombo;
    private JComboBox<String> quantityCombo;
    private JComboBox<String> dateCombo;
    private JComboBox<String> priceCombo;
    private JComboBox<String> customerCombo;
    private JComboBox<String> countryCombo;
    private JComboBox<String> customReferenceCombo;
    private JComboBox<String> customPostCombo;
    private JComboBox<String> customDateCombo;
    private JComboBox<String> customUserIdCombo;
    private JComboBox<String> customUserNameCombo;
    private JComboBox<String> customTextCombo;
    private JCheckBox skipCancelledCheck;
    private JCheckBox skipMissingCustomerCheck;
    private JCheckBox skipQuantityCheck;
    private JCheckBox skipPriceCheck;
    private JCheckBox stockPrefixCheck;
    private JCheckBox stableFolloweeCheck;
    private JTextField minTokensField;
    private JTextField fallbackDateField;
    private JComboBox<UciRetailDatasetImporter.DatasetBalanceMode> balanceModeCombo;
    private JComboBox<UciRetailDatasetImporter.DatasetBalanceTargetPolicy> balanceTargetPolicyCombo;
    private JTextField balanceTargetField;
    private JTextField balanceSeedField;
    private JTextArea balanceGuide;
    private boolean balancePolicyUserSelected;
    private boolean suppressBalancePolicyEvents;

    private JButton inspectButton;
    private JButton buildButton;
    private JButton useOutputButton;
    private JButton resetButton;

    private MetricCard rowsMetric;
    private MetricCard columnsMetric;
    private MetricCard recognitionMetric;
    private MetricCard usersMetric;
    private MetricCard outputMetric;
    private JLabel statusBadge;
    private JTextArea profileGuide;
    private JTextArea logArea;
    private JLabel consoleEventValue;
    private JLabel consolePhaseValue;
    private JLabel consoleRowsValue;
    private JLabel consoleOutputValue;
    private JProgressBar progressBar;
    private JPanel customMappingCard;
    private JLabel invoiceColumnLabel;
    private JLabel stockColumnLabel;
    private JLabel descriptionColumnLabel;
    private JLabel quantityColumnLabel;
    private JLabel dateColumnLabel;
    private JLabel priceColumnLabel;
    private JLabel customerColumnLabel;
    private JLabel countryColumnLabel;

    private DefaultTableModel previewModel;
    private DefaultTableModel columnModel;
    private DefaultTableModel outputPreviewModel;
    private DefaultTableModel imbalanceModel;
    private JTextArea imbalanceSummary;
    private JLabel imbalanceStatusBadge;
    private JButton refreshImbalanceButton;
    private DefaultTableModel livePreviewModel;
    private JTextArea livePreviewNote;
    private final JLabel[] liveMappingLabels = new JLabel[6];
    private boolean suppressLivePreview;
    private int consoleEventCount;
    private int inspectProgressBucket = -1;
    private int buildProgressBucket = -1;
    private int imbalanceRequestId;
    private SwingWorker<UciRetailDatasetImporter.ImportReport, Void> imbalanceWorker;

    private File selectedFile;
    private UciRetailDatasetImporter.SourceProfile sourceProfile;
    private UciRetailDatasetImporter.ImportReport lastReport;
    private UciRetailDatasetImporter.ImportReport lastBalanceDiagnosis;

    UciRetailImportPanel(ControllerAgentGui owner) {
        this.owner = owner;
        setLayout(new BorderLayout());
        setBackground(SURFACE);
        add(buildContent(), BorderLayout.CENTER);
        setIdleState();
    }

    private Component buildContent() {
        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(16, 18, 16, 18));
        root.add(createHero(), BorderLayout.NORTH);

        JPanel left = createWorkflowPanel();
        JPanel center = createInspectionPanel();
        JPanel right = createReportPanel();

        center.setMinimumSize(new Dimension(560, 520));
        right.setPreferredSize(new Dimension(430, 720));
        JSplitPane inner = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, center, right);
        inner.setResizeWeight(1.0);
        inner.setContinuousLayout(true);
        inner.setBorder(null);
        inner.setDividerSize(10);
        inner.setOpaque(false);

        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, inner);
        main.setResizeWeight(0.0);
        main.setContinuousLayout(true);
        main.setBorder(null);
        main.setDividerSize(10);
        main.setOpaque(false);
        root.add(main, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        statusBadge = badge("READY", SOFT_GREEN, GREEN);
        footer.add(statusBadge, BorderLayout.WEST);
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("Choose a source file to inspect.");
        progressBar.setOpaque(true);
        progressBar.setBackground(CARD);
        progressBar.setForeground(BLUE);
        progressBar.setFont(progressBar.getFont().deriveFont(Font.BOLD, 12f));
        progressBar.setBorder(BorderFactory.createLineBorder(LINE));
        footer.add(progressBar, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        return root;
    }

    private JPanel createHero() {
        JPanel hero = new NetworkHeroPanel();
        hero.setLayout(new BorderLayout());
        hero.setBorder(new EmptyBorder(18, 20, 18, 20));
        hero.setPreferredSize(new Dimension(1000, 126));

        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("DSMP Data Preprocessing Lab");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 28f));
        title.setForeground(INK);
        JLabel subtitle = new JLabel("Convert, augment, balance, and audit DSMP-ready six-column datasets.");
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 13f));
        subtitle.setForeground(MUTED);
        titleBlock.add(title);
        titleBlock.add(Box.createVerticalStrut(5));
        titleBlock.add(subtitle);
        titleBlock.add(createAttributionBlock());

        hero.add(titleBlock, BorderLayout.WEST);
        return hero;
    }

    private static JPanel createAttributionBlock() {
        JPanel block = new JPanel();
        block.setOpaque(false);
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.setBorder(BorderFactory.createEmptyBorder(5, 1, 0, 0));
        block.add(createAttributionLine("Faculty Advisor", "Dr. Abdolreza Abhari"));
        block.add(Box.createRigidArea(new Dimension(0, 3)));
        block.add(createAttributionLine("Developer", "Mohsen Alavi"));
        return block;
    }

    private static JPanel createAttributionLine(String role, String name) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel roleLabel = new JLabel(role.toUpperCase(Locale.US));
        roleLabel.setFont(roleLabel.getFont().deriveFont(Font.BOLD, 9f));
        roleLabel.setForeground(MUTED);
        roleLabel.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 0));

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
        nameLabel.setForeground(INK);

        row.add(roleLabel);
        row.add(nameLabel);
        return row;
    }

    private JPanel createWorkflowPanel() {
        JPanel shell = new JPanel(new BorderLayout(0, 10));
        shell.setOpaque(false);
        shell.setMinimumSize(new Dimension(430, 620));
        shell.setPreferredSize(new Dimension(470, 720));

        WorkflowPanel panel = new WorkflowPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setMinimumSize(new Dimension(430, 700));

        RoundedPanel sourceCard = card("1. Dataset Family and File");
        sourceCard.add(labeled("What kind of source are you converting?", createDatasetKindSelector()));
        sourceCard.add(Box.createVerticalStrut(8));
        fileField = textField("Choose a retail, journal, tweet, or custom source file");
        fileField.setEditable(false);
        JButton chooseButton = button("Choose Dataset", BLUE, Color.WHITE);
        chooseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                chooseFile();
            }
        });
        inspectButton = button("Inspect Dataset", GREEN, Color.WHITE);
        inspectButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                inspectSelectedFile();
            }
        });
        JTextArea sourceHelp = callout("Choose the family first. The importer uses that choice only for safe defaults, then inspects headers, row counts, samples, and column profiles before it writes any DSMP file.");
        sourceHelp.setRows(3);
        sourceCard.add(labeled("Selected file", fileField));
        sourceCard.add(row(chooseButton, inspectButton));
        sourceCard.add(sourceHelp);
        panel.add(sourceCard);
        panel.add(Box.createVerticalStrut(12));

        RoundedPanel profileCard = card("2. Mapping Profile");
        profileCombo = new JComboBox<UciRetailDatasetImporter.MappingProfile>();
        profileCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateProfileGuide();
            }
        });
        profileGuide = callout("");
        profileGuide.setRows(8);
        stableFolloweeCheck = check("Keep one stable followee label per user", true);
        profileCard.add(labeled("Meaning of followee/reference column", profileCombo));
        profileCard.add(profileGuide);
        profileCard.add(Box.createVerticalStrut(7));
        profileCard.add(stableFolloweeCheck);
        panel.add(profileCard);
        panel.add(Box.createVerticalStrut(12));

        RoundedPanel mappingCard = card("3. Detected Source Columns");
        invoiceCombo = combo();
        stockCombo = combo();
        descriptionCombo = combo();
        quantityCombo = combo();
        dateCombo = combo();
        priceCombo = combo();
        customerCombo = combo();
        countryCombo = combo();
        invoiceColumnLabel = fieldLabel("Record / post id");
        stockColumnLabel = fieldLabel("Optional category");
        descriptionColumnLabel = fieldLabel("Text / document body");
        quantityColumnLabel = fieldLabel("Optional numeric filter");
        dateColumnLabel = fieldLabel("Date / timestamp");
        priceColumnLabel = fieldLabel("Optional value filter");
        customerColumnLabel = fieldLabel("User / author id");
        countryColumnLabel = fieldLabel("Followee / reference");
        mappingCard.add(twoColumn(labeled(invoiceColumnLabel, invoiceCombo), labeled(stockColumnLabel, stockCombo)));
        mappingCard.add(twoColumn(labeled(descriptionColumnLabel, descriptionCombo), labeled(quantityColumnLabel, quantityCombo)));
        mappingCard.add(twoColumn(labeled(dateColumnLabel, dateCombo), labeled(priceColumnLabel, priceCombo)));
        mappingCard.add(twoColumn(labeled(customerColumnLabel, customerCombo), labeled(countryColumnLabel, countryCombo)));
        panel.add(mappingCard);
        panel.add(Box.createVerticalStrut(12));

        customMappingCard = card("4. Custom DSMP Column Mapping");
        customReferenceCombo = combo();
        customPostCombo = combo();
        customDateCombo = combo();
        customUserIdCombo = combo();
        customUserNameCombo = combo();
        customTextCombo = combo();
        customMappingCard.add(twoColumn(labeled("Followee / reference", customReferenceCombo), labeled("Numeric post id", customPostCombo)));
        customMappingCard.add(twoColumn(labeled("Date", customDateCombo), labeled("Numeric user id", customUserIdCombo)));
        customMappingCard.add(twoColumn(labeled("User name", customUserNameCombo), labeled("Text / document body", customTextCombo)));
        panel.add(customMappingCard);
        panel.add(Box.createVerticalStrut(12));

        RoundedPanel balanceCard = card("5. Class Balance");
        balanceModeCombo = new JComboBox<UciRetailDatasetImporter.DatasetBalanceMode>(
                UciRetailDatasetImporter.DatasetBalanceMode.values());
        balanceModeCombo.setFont(balanceModeCombo.getFont().deriveFont(Font.BOLD, 12f));
        balanceModeCombo.setBackground(CARD);
        balanceModeCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                applyRecommendedBalanceTargetPolicyIfUnpinned();
                updateBalanceGuide();
                markImbalanceStale();
            }
        });
        balanceTargetPolicyCombo =
                new JComboBox<UciRetailDatasetImporter.DatasetBalanceTargetPolicy>(
                        new UciRetailDatasetImporter.DatasetBalanceTargetPolicy[] {
                                UciRetailDatasetImporter.DatasetBalanceTargetPolicy.PRESERVE_TOTAL_SIZE,
                                UciRetailDatasetImporter.DatasetBalanceTargetPolicy.CONSERVATIVE_CAP,
                                UciRetailDatasetImporter.DatasetBalanceTargetPolicy.STRICT_MINORITY,
                                UciRetailDatasetImporter.DatasetBalanceTargetPolicy.BOUNDED_AUGMENTATION,
                                UciRetailDatasetImporter.DatasetBalanceTargetPolicy.MANUAL
                        });
        balanceTargetPolicyCombo.setFont(balanceTargetPolicyCombo.getFont().deriveFont(Font.BOLD, 12f));
        balanceTargetPolicyCombo.setBackground(CARD);
        balanceTargetPolicyCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!suppressBalancePolicyEvents) {
                    balancePolicyUserSelected = true;
                }
                updateBalanceGuide();
                markImbalanceStale();
            }
        });
        balanceTargetField = textField("0");
        balanceSeedField = textField("1");
        balanceGuide = callout("");
        balanceGuide.setRows(6);
        balanceCard.add(labeled("Balance mode", balanceModeCombo));
        balanceCard.add(labeled("Target policy", balanceTargetPolicyCombo));
        balanceCard.add(twoColumn(labeled("Manual target users/class", balanceTargetField),
                labeled("Balance seed", balanceSeedField)));
        balanceCard.add(balanceGuide);
        panel.add(balanceCard);
        panel.add(Box.createVerticalStrut(12));

        RoundedPanel validationCard = card("6. Validation and Output");
        skipCancelledCheck = check("Skip cancelled invoices", true);
        skipMissingCustomerCheck = check("Skip missing customer ids", true);
        skipQuantityCheck = check("Skip non-positive quantities", true);
        skipPriceCheck = check("Skip non-positive prices", false);
        stockPrefixCheck = check("Group stock codes by prefix", true);
        minTokensField = textField("1");
        fallbackDateField = textField("2011-01-01");
        outputDirField = textField("");
        outputNameField = textField("ImportedDataset_DSMP");
        validationCard.add(twoColumn(skipCancelledCheck, skipMissingCustomerCheck));
        validationCard.add(twoColumn(skipQuantityCheck, skipPriceCheck));
        validationCard.add(stockPrefixCheck);
        validationCard.add(twoColumn(labeled("Minimum text tokens", minTokensField), labeled("Fallback date", fallbackDateField)));
        validationCard.add(labeled("Output folder", outputDirField));
        validationCard.add(labeled("Output dataset name", outputNameField));
        panel.add(validationCard);
        installLivePreviewListeners();

        JScrollPane workflowScroll = new JScrollPane(panel);
        workflowScroll.setBorder(null);
        workflowScroll.setOpaque(false);
        workflowScroll.getViewport().setOpaque(false);
        workflowScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        workflowScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        workflowScroll.getVerticalScrollBar().setUnitIncrement(18);
        shell.add(workflowScroll, BorderLayout.CENTER);

        RoundedPanel actionCard = card("7. Build and Use");
        buildButton = button("Build DSMP Dataset", BLUE, Color.WHITE);
        buildButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                buildDataset();
            }
        });
        useOutputButton = button("Use Output in Simulator", GREEN, Color.WHITE);
        useOutputButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                useOutputInSimulator();
            }
        });
        resetButton = button("Reset Defaults", SURFACE, INK);
        resetButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                applyDefaultsFromSource();
            }
        });
        actionCard.add(row(buildButton, useOutputButton));
        actionCard.add(Box.createVerticalStrut(8));
        actionCard.add(resetButton);
        shell.add(actionCard, BorderLayout.SOUTH);
        return shell;
    }

    private JPanel createInspectionPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setOpaque(false);

        JPanel metrics = new JPanel(new java.awt.GridLayout(1, 5, 10, 0));
        metrics.setOpaque(false);
        rowsMetric = new MetricCard("Rows", "-", "Data rows detected");
        columnsMetric = new MetricCard("Columns", "-", "Detected fields");
        recognitionMetric = new MetricCard("Format", "-", "Dataset type");
        usersMetric = new MetricCard("User signal", "-", "Customer/user column");
        outputMetric = new MetricCard("Output", "-", "Rows written");
        metrics.add(rowsMetric);
        metrics.add(columnsMetric);
        metrics.add(recognitionMetric);
        metrics.add(usersMetric);
        metrics.add(outputMetric);
        panel.add(metrics, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabs.setFont(tabs.getFont().deriveFont(Font.BOLD, 12f));
        previewModel = new DefaultTableModel();
        JTable previewTable = styledTable(previewModel);
        tabs.addTab("Source Snapshot", new JScrollPane(previewTable));

        columnModel = new DefaultTableModel(new Object[] { "Column", "Non-empty", "Numeric", "Date-like", "Distinct", "Samples" }, 0);
        JTable columnTable = styledTable(columnModel);
        tabs.addTab("Column Intelligence", new JScrollPane(columnTable));

        outputPreviewModel = new DefaultTableModel(new Object[] {
                "Followee", "Post id", "Date", "User id", "User name", "Text"
        }, 0);
        JTable outputTable = styledTable(outputPreviewModel);
        tabs.addTab("DSMP Output Preview", new JScrollPane(outputTable));

        tabs.addTab("Imbalance", createImbalancePanel());

        panel.add(tabs, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createImbalancePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Class Imbalance Diagnosis");
        title.setForeground(INK);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        JLabel subtitle = new JLabel("Full-dataset followee distribution before build, then exact post-build balance reporting.");
        subtitle.setForeground(MUTED);
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 12f));
        titleBlock.add(title);
        titleBlock.add(Box.createVerticalStrut(3));
        titleBlock.add(subtitle);
        header.add(titleBlock, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        imbalanceStatusBadge = badge("WAITING", SOFT_AMBER, AMBER);
        refreshImbalanceButton = button("Refresh Diagnosis", BLUE, Color.WHITE);
        refreshImbalanceButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent event) {
                refreshImbalanceDiagnosis(true);
            }
        });
        actions.add(imbalanceStatusBadge);
        actions.add(refreshImbalanceButton);
        header.add(actions, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);

        imbalanceSummary = callout("Inspect a dataset to see class imbalance, balance impact, and warnings here.");
        imbalanceSummary.setRows(7);
        panel.add(imbalanceSummary, BorderLayout.SOUTH);

        imbalanceModel = new DefaultTableModel(new Object[] {
                "Followee", "Original users", "Original rows", "Final users",
                "User share", "Ratio", "Balance action"
        }, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = styledTable(imbalanceModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getColumnModel().getColumn(0).setPreferredWidth(150);
        table.getColumnModel().getColumn(1).setPreferredWidth(98);
        table.getColumnModel().getColumn(2).setPreferredWidth(98);
        table.getColumnModel().getColumn(3).setPreferredWidth(92);
        table.getColumnModel().getColumn(4).setPreferredWidth(80);
        table.getColumnModel().getColumn(5).setPreferredWidth(70);
        table.getColumnModel().getColumn(6).setPreferredWidth(220);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createReportPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setMinimumSize(new Dimension(420, 700));

        RoundedPanel livePreview = createLivePreviewPanel();
        livePreview.setMinimumSize(new Dimension(420, 420));

        JPanel stack = new JPanel(new BorderLayout(0, 10));
        stack.setOpaque(false);
        stack.add(livePreview, BorderLayout.CENTER);
        stack.add(createImportConsolePanel(), BorderLayout.SOUTH);
        panel.add(stack, BorderLayout.CENTER);
        return panel;
    }

    private RoundedPanel createImportConsolePanel() {
        RoundedPanel console = new RoundedPanel(new Color(8, 13, 28));
        console.setLayout(new BorderLayout(10, 10));
        console.setBorder(new EmptyBorder(14, 14, 14, 14));
        console.setPreferredSize(new Dimension(420, 245));

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Import Console");
        title.setForeground(new Color(248, 250, 252));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        JLabel subtitle = new JLabel("Timestamped conversion events, generated-output paths, and audit stats.");
        subtitle.setForeground(new Color(148, 163, 184));
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 11.5f));
        titleBlock.add(title);
        titleBlock.add(Box.createVerticalStrut(2));
        titleBlock.add(subtitle);
        header.add(titleBlock, BorderLayout.WEST);
        consoleEventValue = badge("0 EVENTS", new Color(30, 41, 59), new Color(191, 219, 254));
        consoleEventValue.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(96, 165, 250, 110)),
                new EmptyBorder(6, 9, 6, 9)));
        header.add(consoleEventValue, BorderLayout.EAST);
        console.add(header, BorderLayout.NORTH);

        JPanel middle = new JPanel(new BorderLayout(0, 9));
        middle.setOpaque(false);
        JPanel stats = new JPanel(new java.awt.GridLayout(1, 3, 7, 0));
        stats.setOpaque(false);
        consolePhaseValue = new JLabel();
        consoleRowsValue = new JLabel();
        consoleOutputValue = new JLabel();
        stats.add(consoleTile("Phase", consolePhaseValue, "Ready"));
        stats.add(consoleTile("Rows", consoleRowsValue, "-"));
        stats.add(consoleTile("Output", consoleOutputValue, "Waiting"));
        middle.add(stats, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setRows(7);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        logArea.setBackground(new Color(2, 6, 23));
        logArea.setForeground(new Color(226, 232, 240));
        logArea.setCaretColor(new Color(147, 197, 253));
        logArea.setBorder(new EmptyBorder(10, 12, 10, 12));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(51, 65, 85)));
        scroll.getViewport().setBackground(new Color(2, 6, 23));
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        middle.add(scroll, BorderLayout.CENTER);
        console.add(middle, BorderLayout.CENTER);

        updateConsoleStats("READY", "-", "Waiting");
        return console;
    }

    private static JPanel consoleTile(String label, JLabel value, String initial) {
        JPanel tile = new JPanel(new BorderLayout(3, 2));
        tile.setOpaque(true);
        tile.setBackground(new Color(15, 23, 42));
        tile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(51, 65, 85)),
                new EmptyBorder(7, 8, 7, 8)));
        JLabel title = new JLabel(label.toUpperCase(Locale.US));
        title.setForeground(new Color(148, 163, 184));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 9.5f));
        value.setText(initial);
        value.setForeground(new Color(241, 245, 249));
        value.setFont(value.getFont().deriveFont(Font.BOLD, 11f));
        tile.add(title, BorderLayout.NORTH);
        tile.add(value, BorderLayout.CENTER);
        return tile;
    }

    private RoundedPanel createLivePreviewPanel() {
        RoundedPanel live = new RoundedPanel(CARD);
        live.setLayout(new BorderLayout(12, 12));
        live.setBorder(new EmptyBorder(16, 16, 16, 16));

        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Live DSMP Snapshot");
        title.setForeground(INK);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 17f));
        JLabel subtitle = new JLabel("Real-time six-column output preview from the current mapping.");
        subtitle.setForeground(MUTED);
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 12f));
        titleBlock.add(title);
        titleBlock.add(Box.createVerticalStrut(3));
        titleBlock.add(subtitle);
        header.add(titleBlock, BorderLayout.WEST);

        JPanel chips = new JPanel(new java.awt.GridLayout(1, 2, 6, 0));
        chips.setOpaque(false);
        chips.add(badge("LIVE", SOFT_GREEN, GREEN));
        chips.add(badge("5 ROWS", SOFT_BLUE, BLUE_DARK));
        header.add(chips, BorderLayout.EAST);
        live.add(header, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(8, 9));
        body.setOpaque(false);
        JTextArea help = callout("Change the profile or any column selector and this table redraws immediately. It uses the same importer mapping path as the final DSMP file, before anything is written.");
        help.setRows(3);
        body.add(help, BorderLayout.NORTH);

        JPanel tableStack = new JPanel(new BorderLayout(0, 6));
        tableStack.setOpaque(false);
        JPanel topStack = new JPanel(new BorderLayout(0, 8));
        topStack.setOpaque(false);
        topStack.add(createLiveMappingSummary(), BorderLayout.NORTH);
        topStack.add(dsmpColumnStrip(), BorderLayout.SOUTH);
        tableStack.add(topStack, BorderLayout.NORTH);

        livePreviewModel = new DefaultTableModel(new Object[] {
                "Followee", "Post id", "Date", "User id", "User name", "Text"
        }, 0);
        JTable table = styledTable(livePreviewModel);
        table.setFont(table.getFont().deriveFont(Font.PLAIN, 11f));
        table.setRowHeight(30);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getColumnModel().getColumn(0).setPreferredWidth(105);
        table.getColumnModel().getColumn(1).setPreferredWidth(96);
        table.getColumnModel().getColumn(2).setPreferredWidth(88);
        table.getColumnModel().getColumn(3).setPreferredWidth(88);
        table.getColumnModel().getColumn(4).setPreferredWidth(120);
        table.getColumnModel().getColumn(5).setPreferredWidth(280);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(440, 260));
        tableStack.add(scroll, BorderLayout.CENTER);
        body.add(tableStack, BorderLayout.CENTER);
        live.add(body, BorderLayout.CENTER);

        livePreviewNote = helpText("Inspect a source file to see the live DSMP shape.");
        livePreviewNote.setFont(livePreviewNote.getFont().deriveFont(Font.BOLD, 12f));
        livePreviewNote.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, LINE),
                new EmptyBorder(9, 0, 0, 0)));
        live.add(livePreviewNote, BorderLayout.SOUTH);
        return live;
    }

    private JPanel createLiveMappingSummary() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 7));
        wrapper.setOpaque(false);
        JLabel title = fieldLabel("Current source-to-DSMP mapping");
        wrapper.add(title, BorderLayout.NORTH);

        JPanel grid = new JPanel(new java.awt.GridLayout(2, 3, 6, 6));
        grid.setOpaque(false);
        String[] fields = new String[] {
                "Followee", "Post id", "Date", "User id", "User name", "Text"
        };
        for (int i = 0; i < fields.length; i++) {
            JPanel tile = new JPanel(new BorderLayout(4, 2));
            tile.setOpaque(true);
            tile.setBackground(SURFACE);
            tile.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(LINE),
                    new EmptyBorder(7, 8, 7, 8)));
            JLabel field = new JLabel((i + 1) + " " + fields[i]);
            field.setForeground(BLUE_DARK);
            field.setFont(field.getFont().deriveFont(Font.BOLD, 10.5f));
            JLabel value = new JLabel("Inspect first");
            value.setForeground(INK);
            value.setFont(value.getFont().deriveFont(Font.PLAIN, 10.5f));
            liveMappingLabels[i] = value;
            tile.add(field, BorderLayout.NORTH);
            tile.add(value, BorderLayout.CENTER);
            grid.add(tile);
        }
        wrapper.add(grid, BorderLayout.CENTER);
        return wrapper;
    }

    private static JPanel dsmpColumnStrip() {
        JPanel strip = new JPanel(new java.awt.GridLayout(1, 6, 4, 0));
        strip.setOpaque(false);
        strip.setAlignmentX(Component.LEFT_ALIGNMENT);
        String[] names = new String[] {
                "1 Followee", "2 Post id", "3 Date", "4 User id", "5 User", "6 Text"
        };
        for (String name : names) {
            JLabel label = new JLabel(name, SwingConstants.CENTER);
            label.setOpaque(true);
            label.setBackground(SOFT_BLUE);
            label.setForeground(BLUE_DARK);
            label.setFont(label.getFont().deriveFont(Font.BOLD, 10f));
            label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(BLUE_DARK.getRed(), BLUE_DARK.getGreen(), BLUE_DARK.getBlue(), 55)),
                    new EmptyBorder(5, 4, 5, 4)));
            strip.add(label);
        }
        return strip;
    }

    private JPanel createDatasetKindSelector() {
        JPanel grid = new JPanel(new java.awt.GridLayout(2, 2, 10, 10));
        grid.setOpaque(false);
        for (UciRetailDatasetImporter.DatasetKind kind : UciRetailDatasetImporter.DatasetKind.values()) {
            DatasetKindButton button = new DatasetKindButton(kind);
            button.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    selectDatasetKind(((DatasetKindButton) e.getSource()).kind);
                }
            });
            datasetKindButtons.put(kind, button);
            grid.add(button);
        }
        return grid;
    }

    private void selectDatasetKind(UciRetailDatasetImporter.DatasetKind kind) {
        if (kind == null) {
            kind = UciRetailDatasetImporter.DatasetKind.RETAIL;
        }
        if (selectedDatasetKind == kind) {
            updateDatasetKindUi();
            return;
        }
        selectedDatasetKind = kind;
        sourceProfile = null;
        lastReport = null;
        clearTables();
        buildButton.setEnabled(false);
        useOutputButton.setEnabled(false);
        if (selectedFile != null) {
            outputNameField.setText(safeBase(selectedFile.getName()) + "_DSMP");
            setStatus("READY", kind + " mode selected. Inspect the file again so mappings are refreshed.", 0);
            log("Dataset family changed to " + kind + ". Re-inspect the selected file before building.");
        }
        updateDatasetKindUi();
    }

    private void updateDatasetKindUi() {
        for (Map.Entry<UciRetailDatasetImporter.DatasetKind, DatasetKindButton> entry : datasetKindButtons.entrySet()) {
            entry.getValue().setSelected(entry.getKey() == selectedDatasetKind);
        }
        updateProfileChoicesForKind(selectedDatasetKind, null);
        updateColumnLabelsForKind(selectedDatasetKind);
        updateValidationControlsForKind(selectedDatasetKind);
        updateProfileGuide();
    }

    private void updateProfileChoicesForKind(UciRetailDatasetImporter.DatasetKind kind,
            UciRetailDatasetImporter.MappingProfile preferred) {
        if (profileCombo == null) {
            return;
        }
        UciRetailDatasetImporter.MappingProfile current =
                preferred == null
                        ? (UciRetailDatasetImporter.MappingProfile) profileCombo.getSelectedItem()
                        : preferred;
        UciRetailDatasetImporter.MappingProfile[] profiles = profilesForKind(kind);
        DefaultComboBoxModel<UciRetailDatasetImporter.MappingProfile> model =
                new DefaultComboBoxModel<UciRetailDatasetImporter.MappingProfile>();
        boolean containsCurrent = false;
        for (UciRetailDatasetImporter.MappingProfile profile : profiles) {
            model.addElement(profile);
            if (profile == current) {
                containsCurrent = true;
            }
        }
        profileCombo.setModel(model);
        if (containsCurrent) {
            profileCombo.setSelectedItem(current);
        } else if (profiles.length > 0) {
            profileCombo.setSelectedItem(profiles[0]);
        }
        profileCombo.setEnabled(kind != UciRetailDatasetImporter.DatasetKind.OTHER);
    }

    private static UciRetailDatasetImporter.MappingProfile[] profilesForKind(
            UciRetailDatasetImporter.DatasetKind kind) {
        if (kind == UciRetailDatasetImporter.DatasetKind.JOURNAL) {
            return new UciRetailDatasetImporter.MappingProfile[] {
                    UciRetailDatasetImporter.MappingProfile.JOURNAL_AUTHORS,
                    UciRetailDatasetImporter.MappingProfile.SIMILAR_CUSTOMERS,
                    UciRetailDatasetImporter.MappingProfile.CUSTOM
            };
        }
        if (kind == UciRetailDatasetImporter.DatasetKind.TWEETS) {
            return new UciRetailDatasetImporter.MappingProfile[] {
                    UciRetailDatasetImporter.MappingProfile.TWEET_USERS,
                    UciRetailDatasetImporter.MappingProfile.SIMILAR_CUSTOMERS,
                    UciRetailDatasetImporter.MappingProfile.CUSTOM
            };
        }
        if (kind == UciRetailDatasetImporter.DatasetKind.OTHER) {
            return new UciRetailDatasetImporter.MappingProfile[] {
                    UciRetailDatasetImporter.MappingProfile.CUSTOM
            };
        }
        return new UciRetailDatasetImporter.MappingProfile[] {
                UciRetailDatasetImporter.MappingProfile.SIMILAR_CUSTOMERS,
                UciRetailDatasetImporter.MappingProfile.SMART_PRODUCT_CATEGORY,
                UciRetailDatasetImporter.MappingProfile.PRODUCT_FAMILY,
                UciRetailDatasetImporter.MappingProfile.COUNTRY_BASELINE,
                UciRetailDatasetImporter.MappingProfile.STOCK_CODE_EXPERIMENTAL,
                UciRetailDatasetImporter.MappingProfile.CUSTOM
        };
    }

    private void updateColumnLabelsForKind(UciRetailDatasetImporter.DatasetKind kind) {
        if (invoiceColumnLabel == null) {
            return;
        }
        if (kind == UciRetailDatasetImporter.DatasetKind.RETAIL) {
            invoiceColumnLabel.setText("Invoice / order id");
            stockColumnLabel.setText("Stock / product code");
            descriptionColumnLabel.setText("Description / title text");
            quantityColumnLabel.setText("Quantity");
            dateColumnLabel.setText("Invoice date");
            priceColumnLabel.setText("Unit price");
            customerColumnLabel.setText("Customer id");
            countryColumnLabel.setText("Country / region");
        } else if (kind == UciRetailDatasetImporter.DatasetKind.JOURNAL) {
            invoiceColumnLabel.setText("Paper / article id");
            stockColumnLabel.setText("Subject / category");
            descriptionColumnLabel.setText("Title / abstract text");
            quantityColumnLabel.setText("Optional numeric filter");
            dateColumnLabel.setText("Publication date / year");
            priceColumnLabel.setText("Optional value filter");
            customerColumnLabel.setText("Author id / sequence id");
            countryColumnLabel.setText("Journal / venue followee");
        } else if (kind == UciRetailDatasetImporter.DatasetKind.TWEETS) {
            invoiceColumnLabel.setText("Tweet / status id");
            stockColumnLabel.setText("Topic / hashtag");
            descriptionColumnLabel.setText("Tweet text");
            quantityColumnLabel.setText("Optional numeric filter");
            dateColumnLabel.setText("Created date");
            priceColumnLabel.setText("Optional value filter");
            customerColumnLabel.setText("User id / screen name");
            countryColumnLabel.setText("Reference / source account");
        } else {
            invoiceColumnLabel.setText("Record / post id");
            stockColumnLabel.setText("Optional category");
            descriptionColumnLabel.setText("Text / document body");
            quantityColumnLabel.setText("Optional numeric filter");
            dateColumnLabel.setText("Date / timestamp");
            priceColumnLabel.setText("Optional value filter");
            customerColumnLabel.setText("User / entity id");
            countryColumnLabel.setText("Followee / reference label");
        }
    }

    private void updateValidationControlsForKind(UciRetailDatasetImporter.DatasetKind kind) {
        if (skipCancelledCheck == null) {
            return;
        }
        boolean retail = kind == UciRetailDatasetImporter.DatasetKind.RETAIL;
        skipCancelledCheck.setText(retail ? "Skip cancelled invoices" : "Skip cancelled rows if a cancel flag exists");
        skipQuantityCheck.setText(retail ? "Skip non-positive quantities" : "Skip non-positive numeric filter");
        skipPriceCheck.setText(retail ? "Skip non-positive prices" : "Skip non-positive value filter");
        stockPrefixCheck.setText(retail ? "Group stock codes by prefix" : "Group optional category values by prefix");
        skipCancelledCheck.setEnabled(retail);
        skipQuantityCheck.setEnabled(retail);
        skipPriceCheck.setEnabled(retail);
        stockPrefixCheck.setEnabled(retail);
    }

    private void chooseFile() {
        File start = selectedFile != null ? selectedFile : new File(System.getProperty("user.dir"));
        File chosen = ModernFilePicker.chooseDataset(this, start, new File(System.getProperty("user.dir")));
        if (chosen == null) {
            return;
        }
        selectedFile = chosen;
        fileField.setText(chosen.getAbsolutePath());
        outputDirField.setText(new File(chosen.getParentFile(), "DSMP_Imported").getAbsolutePath());
        outputNameField.setText(safeBase(chosen.getName()) + "_DSMP");
        sourceProfile = null;
        lastReport = null;
        clearTables();
        setStatus("READY", "Dataset selected. Inspect it before building.", 0);
        updateConsoleStats("READY", "-", "Selected");
        inspectButton.setEnabled(true);
        buildButton.setEnabled(false);
        useOutputButton.setEnabled(false);
        log("SOURCE SELECTED - " + selectedDatasetKind
                + "\nFile: " + chosen.getAbsolutePath()
                + "\nSize: " + humanBytes(chosen.length())
                + "\nNext: Inspect Dataset");
    }

    private void inspectSelectedFile() {
        if (selectedFile == null) {
            log("Choose a dataset file first.");
            return;
        }
        inspectProgressBucket = -1;
        setBusy(true);
        setStatus("INSPECTING", "Reading headers, delimiter, rows, and column statistics.", 8);
        updateConsoleStats("INSPECTING", "-", "Scanning");
        log("INSPECTION START"
                + "\nFamily: " + selectedDatasetKind
                + "\nFile: " + selectedFile.getName());
        SwingWorker<UciRetailDatasetImporter.SourceProfile, String> worker = new SwingWorker<UciRetailDatasetImporter.SourceProfile, String>() {
            @Override
            protected UciRetailDatasetImporter.SourceProfile doInBackground() throws Exception {
                return UciRetailDatasetImporter.inspect(selectedFile, selectedDatasetKind, new UciRetailDatasetImporter.ProgressListener() {
                    public void onProgress(final String message, final int percent) {
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                setStatus("INSPECTING", message, percent);
                                logProgress("INSPECT", message, percent);
                            }
                        });
                    }
                });
            }

            @Override
            protected void done() {
                try {
                    sourceProfile = get();
                    selectedDatasetKind = sourceProfile.selectedKind;
                    applyDefaultsFromSource();
                    renderSourceProfile();
                    setStatus("READY", "Dataset inspection complete. Review mapping, then build DSMP output.", 100);
                    updateConsoleStats("INSPECTED",
                            new DecimalFormat("#,##0").format(sourceProfile.dataRows),
                            sourceProfile.sourceFormat);
                    log("INSPECTION COMPLETE"
                            + "\n" + UciRetailDatasetImporter.describeSource(sourceProfile)
                            + "\nDetected columns: " + sourceProfile.columnNames.size()
                            + "\nMalformed rows: " + new DecimalFormat("#,##0").format(sourceProfile.malformedRows)
                            + "\nSamples loaded: " + sourceProfile.sampleRows.size());
                    buildButton.setEnabled(true);
                } catch (Exception ex) {
                    setStatus("FAILED", "Inspection failed: " + rootMessage(ex), 0);
                    updateConsoleStats("FAILED", "-", "Inspect failed");
                    log("INSPECTION FAILED\n" + rootMessage(ex));
                } finally {
                    setBusy(false);
                }
            }
        };
        worker.execute();
    }

    private void buildDataset() {
        if (sourceProfile == null) {
            log("Inspect a dataset before building.");
            return;
        }
        final UciRetailDatasetImporter.Options options;
        try {
            options = collectOptions();
        } catch (Exception ex) {
            setStatus("FAILED", rootMessage(ex), 0);
            log("OPTIONS ERROR\n" + rootMessage(ex));
            return;
        }
        setBusy(true);
        useOutputButton.setEnabled(false);
        outputPreviewModel.setRowCount(0);
        setStatus("BUILDING", "Validating and writing DSMP dataset.", 4);
        buildProgressBucket = -1;
        updateConsoleStats("BUILDING", "-", "Writing");
        log("BUILD START"
                + "\nProfile: " + options.profile
                + "\nBalance mode: " + options.balanceMode
                + "\nTarget policy: " + options.balanceTargetPolicy
                + "\nManual target users/class: "
                + (options.balanceTargetPolicy
                        == UciRetailDatasetImporter.DatasetBalanceTargetPolicy.MANUAL
                                ? Integer.toString(options.balanceTargetUsersPerClass)
                                : "not used")
                + "\nBalance seed: " + options.balanceSeed
                + "\nOutput folder: " + options.outputDirectory.getAbsolutePath()
                + "\nDataset name: " + options.outputBaseName
                + "\nMinimum text tokens: " + options.minimumTextTokens);

        SwingWorker<UciRetailDatasetImporter.ImportReport, String> worker = new SwingWorker<UciRetailDatasetImporter.ImportReport, String>() {
            @Override
            protected UciRetailDatasetImporter.ImportReport doInBackground() throws Exception {
                return UciRetailDatasetImporter.importDataset(sourceProfile, options, new UciRetailDatasetImporter.ProgressListener() {
                    public void onProgress(final String message, final int percent) {
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                setStatus("BUILDING", message, percent);
                                logProgress("BUILD", message, percent);
                            }
                        });
                    }
                });
            }

            @Override
            protected void done() {
                try {
                    lastReport = get();
                    renderReport(lastReport);
                    setStatus("READY", lastReport.summaryLine(), 100);
                    updateConsoleStats("COMPLETE",
                            new DecimalFormat("#,##0").format(lastReport.writtenRows),
                            lastReport.outputFile.getName());
                    log(formatBuildReport(lastReport));
                    useOutputButton.setEnabled(true);
                } catch (Exception ex) {
                    setStatus("FAILED", "Build failed: " + rootMessage(ex), 0);
                    updateConsoleStats("FAILED", "-", "Build failed");
                    log("BUILD FAILED\n" + rootMessage(ex));
                } finally {
                    setBusy(false);
                }
            }
        };
        worker.execute();
    }

    private void useOutputInSimulator() {
        if (lastReport == null || lastReport.outputFile == null || !lastReport.outputFile.isFile()) {
            log("Build a DSMP dataset first.");
            return;
        }
        String label = safeBase(lastReport.outputFile.getName());
        owner.loadImportedDataset(lastReport.outputFile, label);
        setStatus("READY", "Imported dataset sent to the simulator. Press Get Users there to inspect targets.", 100);
        updateConsoleStats("READY",
                new DecimalFormat("#,##0").format(lastReport.writtenRows),
                "Loaded");
        log("SIMULATOR UPDATED"
                + "\nDataset label: " + label
                + "\nSource file: " + lastReport.outputFile.getAbsolutePath());
    }

    private void applyDefaultsFromSource() {
        if (sourceProfile == null) {
            return;
        }
        UciRetailDatasetImporter.Options defaults = UciRetailDatasetImporter.Options.defaultsFor(sourceProfile);
        selectedDatasetKind = defaults.datasetKind;
        updateProfileChoicesForKind(selectedDatasetKind, defaults.profile);
        outputDirField.setText(defaults.outputDirectory.getAbsolutePath());
        outputNameField.setText(defaults.outputBaseName);
        skipCancelledCheck.setSelected(defaults.skipCancelledInvoices);
        skipMissingCustomerCheck.setSelected(defaults.skipMissingCustomer);
        skipQuantityCheck.setSelected(defaults.skipNonPositiveQuantity);
        skipPriceCheck.setSelected(defaults.skipNonPositiveUnitPrice);
        stockPrefixCheck.setSelected(defaults.groupStockCodesByPrefix);
        stableFolloweeCheck.setSelected(defaults.forceSingleFolloweePerUser);
        minTokensField.setText(Integer.toString(defaults.minimumTextTokens));
        fallbackDateField.setText(defaults.defaultDate);
        balancePolicyUserSelected = false;
        UciRetailDatasetImporter.DatasetBalanceMode initialBalanceMode =
                defaults.balanceMode == UciRetailDatasetImporter.DatasetBalanceMode.OFF
                        ? UciRetailDatasetImporter.DatasetBalanceMode.DIAGNOSE_ONLY
                        : defaults.balanceMode;
        balanceModeCombo.setSelectedItem(initialBalanceMode);
        setBalanceTargetPolicy(recommendedPolicyForMode(initialBalanceMode));
        balanceTargetField.setText(Integer.toString(defaults.balanceTargetUsersPerClass));
        balanceSeedField.setText(Long.toString(defaults.balanceSeed));
        populateColumnCombos(sourceProfile, defaults);
        updateDatasetKindUi();
        updateProfileChoicesForKind(selectedDatasetKind, defaults.profile);
        updateProfileGuide();
        updateBalanceGuide();
        refreshImbalanceDiagnosis(false);
    }

    private void populateColumnCombos(UciRetailDatasetImporter.SourceProfile source, UciRetailDatasetImporter.Options defaults) {
        suppressLivePreview = true;
        JComboBox<?>[] combos = new JComboBox<?>[] {
                invoiceCombo, stockCombo, descriptionCombo, quantityCombo, dateCombo, priceCombo,
                customerCombo, countryCombo, customReferenceCombo, customPostCombo, customDateCombo,
                customUserIdCombo, customUserNameCombo, customTextCombo
        };
        try {
            DefaultComboBoxModel<String> model = columnModel(source);
            for (JComboBox<?> combo : combos) {
                @SuppressWarnings("unchecked")
                JComboBox<String> typed = (JComboBox<String>) combo;
                typed.setModel(copyModel(model));
            }
            setCombo(invoiceCombo, defaults.invoiceColumn);
            setCombo(stockCombo, defaults.stockCodeColumn);
            setCombo(descriptionCombo, defaults.descriptionColumn);
            setCombo(quantityCombo, defaults.quantityColumn);
            setCombo(dateCombo, defaults.invoiceDateColumn);
            setCombo(priceCombo, defaults.unitPriceColumn);
            setCombo(customerCombo, defaults.customerIdColumn);
            setCombo(countryCombo, defaults.countryColumn);
            setCombo(customReferenceCombo, defaults.customReferenceColumn);
            setCombo(customPostCombo, defaults.customPostIdColumn);
            setCombo(customDateCombo, defaults.customDateColumn);
            setCombo(customUserIdCombo, defaults.customUserIdColumn);
            setCombo(customUserNameCombo, defaults.customUserNameColumn);
            setCombo(customTextCombo, defaults.customTextColumn);
        } finally {
            suppressLivePreview = false;
        }
        updateLivePreview();
    }

    private UciRetailDatasetImporter.Options collectOptions() {
        UciRetailDatasetImporter.Options options = UciRetailDatasetImporter.Options.defaultsFor(sourceProfile);
        options.datasetKind = selectedDatasetKind;
        options.profile = (UciRetailDatasetImporter.MappingProfile) profileCombo.getSelectedItem();
        options.forceSingleFolloweePerUser = stableFolloweeCheck.isSelected();
        options.skipCancelledInvoices = skipCancelledCheck.isSelected();
        options.skipMissingCustomer = skipMissingCustomerCheck.isSelected();
        options.skipNonPositiveQuantity = skipQuantityCheck.isSelected();
        options.skipNonPositiveUnitPrice = skipPriceCheck.isSelected();
        options.groupStockCodesByPrefix = stockPrefixCheck.isSelected();
        options.minimumTextTokens = parsePositiveInt(minTokensField.getText(), 1);
        options.defaultDate = fallbackDateField.getText().trim().length() == 0 ? "2011-01-01" : fallbackDateField.getText().trim();
        Object balanceMode = balanceModeCombo.getSelectedItem();
        options.balanceMode = balanceMode instanceof UciRetailDatasetImporter.DatasetBalanceMode
                ? (UciRetailDatasetImporter.DatasetBalanceMode) balanceMode
                : UciRetailDatasetImporter.DatasetBalanceMode.OFF;
        Object targetPolicy = balanceTargetPolicyCombo.getSelectedItem();
        options.balanceTargetPolicy =
                targetPolicy instanceof UciRetailDatasetImporter.DatasetBalanceTargetPolicy
                        ? (UciRetailDatasetImporter.DatasetBalanceTargetPolicy) targetPolicy
                        : UciRetailDatasetImporter.recommendedTargetPolicyForMode(
                                options.balanceMode);
        options.balanceTargetUsersPerClass =
                options.balanceTargetPolicy
                        == UciRetailDatasetImporter.DatasetBalanceTargetPolicy.MANUAL
                                ? parseNonNegativeInt(balanceTargetField.getText(), 0)
                                : 0;
        options.balanceSeed = parseLong(balanceSeedField.getText(), 1L);
        options.outputDirectory = new File(outputDirField.getText().trim());
        options.outputBaseName = outputNameField.getText().trim();

        options.invoiceColumn = comboIndex(invoiceCombo);
        options.stockCodeColumn = comboIndex(stockCombo);
        options.descriptionColumn = comboIndex(descriptionCombo);
        options.quantityColumn = comboIndex(quantityCombo);
        options.invoiceDateColumn = comboIndex(dateCombo);
        options.unitPriceColumn = comboIndex(priceCombo);
        options.customerIdColumn = comboIndex(customerCombo);
        options.countryColumn = comboIndex(countryCombo);
        options.customReferenceColumn = comboIndex(customReferenceCombo);
        options.customPostIdColumn = comboIndex(customPostCombo);
        options.customDateColumn = comboIndex(customDateCombo);
        options.customUserIdColumn = comboIndex(customUserIdCombo);
        options.customUserNameColumn = comboIndex(customUserNameCombo);
        options.customTextColumn = comboIndex(customTextCombo);
        return options;
    }

    private void renderSourceProfile() {
        DecimalFormat fmt = new DecimalFormat("#,##0");
        rowsMetric.setValue(fmt.format(sourceProfile.dataRows));
        columnsMetric.setValue(Integer.toString(sourceProfile.columnNames.size()));
        recognitionMetric.setValue(sourceProfile.detectedKind.toString());
        int customerIdx = sourceProfile.columnIndex(
                "CustomerID", "Customer ID", "UserID", "User ID", "AuthorID",
                "Author ID", "SequenceID", "ScreenName", "UserName");
        if (!sourceProfile.hasHeader && sourceProfile.columnNames.size() >= 6) {
            customerIdx = 3;
        }
        long userEstimate = customerIdx >= 0 ? sourceProfile.columns.get(customerIdx).distinctCount() : -1L;
        usersMetric.setValue(userEstimate >= 0L ? fmt.format(userEstimate) : "Mapped");
        outputMetric.setValue("-");

        previewModel.setColumnCount(0);
        previewModel.setRowCount(0);
        for (String name : sourceProfile.columnNames) {
            previewModel.addColumn(name);
        }
        for (List<String> row : sourceProfile.previewRows) {
            previewModel.addRow(row.toArray(new Object[row.size()]));
        }

        columnModel.setRowCount(0);
        for (UciRetailDatasetImporter.ColumnProfile col : sourceProfile.columns) {
            String distinct = col.distinctCount() < 0 ? "10,000+" : fmt.format(col.distinctCount());
            columnModel.addRow(new Object[] {
                    col.name,
                    fmt.format(col.nonEmptyCount),
                    fmt.format(col.numericCount),
                    fmt.format(col.dateLikeCount),
                    distinct,
                    col.sampleText()
            });
        }
        updateLivePreview();
        refreshImbalanceDiagnosis(false);
    }

    private void renderReport(UciRetailDatasetImporter.ImportReport report) {
        DecimalFormat fmt = new DecimalFormat("#,##0");
        outputMetric.setValue(fmt.format(report.writtenRows));
        outputPreviewModel.setRowCount(0);
        for (String[] row : report.outputPreview) {
            outputPreviewModel.addRow(row);
        }
        renderImbalanceReport(report, true);
    }

    private void refreshImbalanceDiagnosis(final boolean userRequested) {
        if (imbalanceModel == null) {
            return;
        }
        if (sourceProfile == null) {
            resetImbalancePanel("Inspect a dataset to see class imbalance, balance impact, and warnings here.");
            return;
        }
        final UciRetailDatasetImporter.Options options;
        try {
            options = collectOptions();
        } catch (Exception ex) {
            setImbalanceStatus("WAITING", SOFT_AMBER, AMBER);
            imbalanceSummary.setText("Balance diagnosis is waiting for a valid mapping: " + rootMessage(ex));
            return;
        }
        final int requestId = ++imbalanceRequestId;
        if (imbalanceWorker != null && !imbalanceWorker.isDone()) {
            imbalanceWorker.cancel(true);
        }
        setImbalanceStatus("SCANNING", SOFT_BLUE, BLUE_DARK);
        refreshImbalanceButton.setEnabled(false);
        if (userRequested || lastBalanceDiagnosis == null) {
            imbalanceSummary.setText("Scanning the full accepted dataset with the current mapping and balance mode...");
        }
        imbalanceWorker = new SwingWorker<UciRetailDatasetImporter.ImportReport, Void>() {
            @Override
            protected UciRetailDatasetImporter.ImportReport doInBackground() throws Exception {
                return UciRetailDatasetImporter.diagnoseBalance(sourceProfile, options, null);
            }

            @Override
            protected void done() {
                if (requestId != imbalanceRequestId) {
                    return;
                }
                refreshImbalanceButton.setEnabled(true);
                try {
                    lastBalanceDiagnosis = get();
                    renderImbalanceReport(lastBalanceDiagnosis, false);
                } catch (Exception ex) {
                    setImbalanceStatus("FAILED", StudioNightTheme.adaptiveColor(
                            new Color(254, 226, 226), StudioNightTheme.NIGHT_PANEL_INK),
                            StudioNightTheme.adaptiveColor(new Color(185, 28, 28), StudioNightTheme.NIGHT_RED));
                    imbalanceSummary.setText("Balance diagnosis failed: " + rootMessage(ex));
                }
            }
        };
        imbalanceWorker.execute();
    }

    private void renderImbalanceReport(UciRetailDatasetImporter.ImportReport report, boolean postBuild) {
        if (imbalanceModel == null || report == null) {
            return;
        }
        DecimalFormat fmt = new DecimalFormat("#,##0");
        DecimalFormat ratioFmt = new DecimalFormat("#,##0.0");
        imbalanceModel.setRowCount(0);
        Set<String> labels = new LinkedHashSet<String>();
        labels.addAll(report.originalFolloweeUserCounts.keySet());
        labels.addAll(report.originalFolloweeRowCounts.keySet());
        labels.addAll(report.finalFolloweeUserCounts.keySet());
        labels.addAll(report.finalFolloweeCounts.keySet());
        List<String> sortedLabels = new ArrayList<String>(labels);
        Collections.sort(sortedLabels, new Comparator<String>() {
            public int compare(String left, String right) {
                long leftCount = mapLong(report.originalFolloweeUserCounts, left);
                long rightCount = mapLong(report.originalFolloweeUserCounts, right);
                int countCompare = Long.compare(rightCount, leftCount);
                return countCompare != 0 ? countCompare : left.compareTo(right);
            }
        });

        long originalTotal = sumLongMap(report.originalFolloweeUserCounts);
        long finalTotal = sumLongMap(report.finalFolloweeUserCounts);
        long minUsers = Long.MAX_VALUE;
        long maxUsers = 0L;
        for (String label : sortedLabels) {
            long users = mapLong(report.originalFolloweeUserCounts, label);
            if (users > 0L) {
                minUsers = Math.min(minUsers, users);
                maxUsers = Math.max(maxUsers, users);
            }
        }
        if (minUsers == Long.MAX_VALUE) {
            minUsers = 0L;
        }

        for (String label : sortedLabels) {
            long originalUsers = mapLong(report.originalFolloweeUserCounts, label);
            long originalRows = mapLong(report.originalFolloweeRowCounts, label);
            long finalUsers = mapLong(report.finalFolloweeUserCounts, label);
            String ratio = minUsers <= 0L || originalUsers <= 0L
                    ? "-" : ratioFmt.format(originalUsers / (double) minUsers) + "x";
            imbalanceModel.addRow(new Object[] {
                    label,
                    fmt.format(originalUsers),
                    fmt.format(originalRows),
                    fmt.format(finalUsers),
                    formatPercent(originalUsers, originalTotal),
                    ratio,
                    balanceAction(report, originalUsers, finalUsers)
            });
        }

        if (sortedLabels.isEmpty()) {
            imbalanceModel.addRow(new Object[] {
                    "No accepted followee labels", "-", "-", "-", "-", "-", "Check mapping and validation filters"
            });
        }

        setImbalanceStatus(postBuild ? "BUILT" : "READY",
                postBuild ? SOFT_GREEN : SOFT_BLUE,
                postBuild ? GREEN : BLUE_DARK);
        StringBuilder sb = new StringBuilder();
        sb.append(postBuild ? "POST-BUILD BALANCE REPORT" : "PRE-BUILD FULL-DATASET DIAGNOSIS");
        sb.append("\nSelected mode: ").append(report.balanceMode);
        if (report.effectiveBalanceMode != null
                && report.balanceMode != report.effectiveBalanceMode) {
            sb.append(" | Effective mode: ").append(report.effectiveBalanceMode);
        }
        sb.append(" | Target policy: ").append(report.balanceTargetPolicy);
        sb.append(" | Engine: ").append(report.balanceEngine);
        sb.append(" | Seed: ").append(report.balanceSeed);
        if (report.balanceTargetUsersPerClass > 0) {
            sb.append(" | Target/class: ").append(fmt.format(report.balanceTargetUsersPerClass));
        } else {
            sb.append(" | Target/class: automatic or unchanged");
        }
        if (report.balanceTargetDescription != null
                && report.balanceTargetDescription.length() > 0) {
            sb.append("\nTarget source: ").append(report.balanceTargetDescription);
        }
        sb.append("\nAccepted rows: ").append(fmt.format(report.acceptedRows));
        sb.append(" | Original users: ").append(fmt.format(report.balanceOriginalUsers));
        sb.append(" | Followee labels: ").append(fmt.format(report.originalFolloweeUserCounts.size()));
        if (maxUsers > 0L && minUsers > 0L) {
            sb.append("\nOriginal imbalance: largest class ").append(fmt.format(maxUsers));
            sb.append(" users, smallest class ").append(fmt.format(minUsers));
            sb.append(" users, ratio ").append(ratioFmt.format(maxUsers / (double) minUsers)).append("x.");
        } else {
            sb.append("\nOriginal imbalance: not enough accepted classes to compute a ratio.");
        }
        sb.append("\nSelected-mode result: ").append(fmt.format(finalTotal));
        sb.append(" final user instances, ").append(fmt.format(report.balanceDroppedUsers));
        sb.append(" dropped users, ").append(fmt.format(report.balanceSyntheticUsers));
        sb.append(" synthetic users, ").append(fmt.format(report.balanceDroppedRows)).append(" dropped rows.");
        sb.append("\nRecommendation: ").append(balanceRecommendation(report, maxUsers, minUsers));
        for (String warning : report.warnings) {
            sb.append("\nWarning: ").append(warning);
        }
        imbalanceSummary.setText(sb.toString());
    }

    private void markImbalanceStale() {
        if (sourceProfile == null || imbalanceSummary == null) {
            return;
        }
        setImbalanceStatus("REFRESH", SOFT_AMBER, AMBER);
        if (lastBalanceDiagnosis == null && lastReport == null) {
            imbalanceSummary.setText("Click Refresh Diagnosis to scan the full accepted dataset with the current mapping and balance mode.");
        } else {
            imbalanceSummary.setText("Mapping or balance settings changed. Click Refresh Diagnosis for updated pre-build imbalance numbers.");
        }
    }

    private void resetImbalancePanel(String message) {
        if (imbalanceModel != null) {
            imbalanceModel.setRowCount(0);
        }
        if (imbalanceSummary != null) {
            imbalanceSummary.setText(message);
        }
        if (imbalanceStatusBadge != null) {
            setImbalanceStatus("WAITING", SOFT_AMBER, AMBER);
        }
        lastBalanceDiagnosis = null;
    }

    private void setImbalanceStatus(String text, Color background, Color foreground) {
        if (imbalanceStatusBadge == null) {
            return;
        }
        imbalanceStatusBadge.setText(text);
        imbalanceStatusBadge.setBackground(background);
        imbalanceStatusBadge.setForeground(foreground);
        imbalanceStatusBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), 70)),
                new EmptyBorder(5, 9, 5, 9)));
    }

    private static long mapLong(Map<String, Long> values, String key) {
        Long value = values.get(key);
        return value == null ? 0L : value.longValue();
    }

    private static long sumLongMap(Map<String, Long> values) {
        long total = 0L;
        for (Long value : values.values()) {
            if (value != null) {
                total += value.longValue();
            }
        }
        return total;
    }

    private static String formatPercent(long value, long total) {
        if (total <= 0L) {
            return "-";
        }
        return new DecimalFormat("0.0").format(value * 100.0d / total) + "%";
    }

    private static String balanceAction(UciRetailDatasetImporter.ImportReport report,
            long originalUsers, long finalUsers) {
        long change = finalUsers - originalUsers;
        if (change > 0L) {
            return "Augmented +" + change + " synthetic user instance" + (change == 1L ? "" : "s");
        }
        if (change < 0L) {
            return "Downsampled " + (-change) + " original user" + (change == -1L ? "" : "s");
        }
        UciRetailDatasetImporter.DatasetBalanceMode effectiveMode =
                report.effectiveBalanceMode == null
                        ? report.balanceMode : report.effectiveBalanceMode;
        if (report.balanceMode != effectiveMode
                && effectiveMode == UciRetailDatasetImporter.DatasetBalanceMode.DIAGNOSE_ONLY) {
            return "Not applied - only one followee class";
        }
        if (effectiveMode == UciRetailDatasetImporter.DatasetBalanceMode.DIAGNOSE_ONLY) {
            return "Diagnosed only - unchanged";
        }
        if (effectiveMode == UciRetailDatasetImporter.DatasetBalanceMode.OFF) {
            return "Off - unchanged";
        }
        return "Kept unchanged by selected mode";
    }

    private static String balanceRecommendation(UciRetailDatasetImporter.ImportReport report,
            long maxUsers, long minUsers) {
        if (report.originalFolloweeUserCounts.size() <= 1) {
            return "Only one followee class is present, so classifier balancing is not meaningful for this mapping.";
        }
        double ratio = minUsers <= 0L ? Double.POSITIVE_INFINITY : maxUsers / (double) minUsers;
        if (ratio <= 1.5d) {
            return "The class distribution is already fairly close; Off or Diagnose only is reasonable.";
        }
        UciRetailDatasetImporter.DatasetBalanceMode effectiveMode =
                report.effectiveBalanceMode == null
                        ? report.balanceMode : report.effectiveBalanceMode;
        if (effectiveMode == UciRetailDatasetImporter.DatasetBalanceMode.OFF
                || effectiveMode == UciRetailDatasetImporter.DatasetBalanceMode.DIAGNOSE_ONLY) {
            return "Use Conservative majority cap for a no-synthetic research run, or Hybrid cap + augmentation when tiny minority classes need support.";
        }
        if (report.balanceSyntheticUsers > 0) {
            return "Hybrid balancing is compensating for minority classes; report the synthetic-user count in the audit when presenting results.";
        }
        if (report.balanceDroppedUsers > 0) {
            return "Majority classes are being capped; this is conservative and avoids synthetic data.";
        }
        return "The selected mode is valid; review the final-user column for any remaining large class gaps.";
    }

    private static String formatBuildReport(UciRetailDatasetImporter.ImportReport report) {
        DecimalFormat fmt = new DecimalFormat("#,##0");
        long skipped = report.skippedCancelledInvoices
                + report.skippedMissingCustomer
                + report.skippedMissingText
                + report.skippedNonPositiveQuantity
                + report.skippedNonPositiveUnitPrice
                + report.malformedRows;
        StringBuilder sb = new StringBuilder();
        sb.append("BUILD COMPLETE");
        sb.append("\n").append(report.summaryLine());
        sb.append("\nRaw rows: ").append(fmt.format(report.rawRows));
        sb.append(" | Accepted rows: ").append(fmt.format(report.acceptedRows));
        sb.append(" | Written rows: ").append(fmt.format(report.writtenRows));
        sb.append("\nUnique users: ").append(fmt.format(report.uniqueUsers));
        sb.append(" | Followee labels: ").append(fmt.format(report.uniqueFollowees));
        sb.append("\nBalance mode: ").append(report.balanceMode);
        if (report.effectiveBalanceMode != null
                && report.balanceMode != report.effectiveBalanceMode) {
            sb.append(" | Effective mode: ").append(report.effectiveBalanceMode);
        }
        sb.append(" | Target policy: ").append(report.balanceTargetPolicy);
        sb.append(" | Engine: ").append(report.balanceEngine);
        sb.append("\nBalance users: original ").append(fmt.format(report.balanceOriginalUsers));
        sb.append(", selected ").append(fmt.format(report.balanceSelectedOriginalUsers));
        sb.append(", dropped ").append(fmt.format(report.balanceDroppedUsers));
        sb.append(", synthetic ").append(fmt.format(report.balanceSyntheticUsers));
        if (report.balanceTargetUsersPerClass > 0) {
            sb.append(" | Target/class: ").append(fmt.format(report.balanceTargetUsersPerClass));
        }
        if (report.balanceTargetDescription != null
                && report.balanceTargetDescription.length() > 0) {
            sb.append("\nTarget source: ").append(report.balanceTargetDescription);
        }
        if (report.balanceDroppedRows > 0L) {
            sb.append("\nBalance dropped rows: ").append(fmt.format(report.balanceDroppedRows));
        }
        sb.append("\nSkipped total: ").append(fmt.format(skipped));
        sb.append(" (cancelled ").append(fmt.format(report.skippedCancelledInvoices));
        sb.append(", missing user ").append(fmt.format(report.skippedMissingCustomer));
        sb.append(", missing text ").append(fmt.format(report.skippedMissingText));
        sb.append(", quantity ").append(fmt.format(report.skippedNonPositiveQuantity));
        sb.append(", price ").append(fmt.format(report.skippedNonPositiveUnitPrice));
        sb.append(", malformed ").append(fmt.format(report.malformedRows)).append(")");
        sb.append("\nBad/fallback dates: ").append(fmt.format(report.badDateRows));
        sb.append("\nOutput: ").append(report.outputFile.getAbsolutePath());
        sb.append(" (").append(humanBytes(report.outputFile.length())).append(")");
        sb.append("\nMetadata: ").append(report.metadataFile.getAbsolutePath());
        sb.append("\nAudit: ").append(report.auditFile.getAbsolutePath());
        sb.append("\nSHA-256: ").append(report.datasetSha256);
        for (String warning : report.warnings) {
            sb.append("\nWarning: ").append(warning);
        }
        return sb.toString();
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = new String[] { "KB", "MB", "GB", "TB" };
        int unit = -1;
        do {
            value /= 1024.0;
            unit++;
        } while (value >= 1024.0 && unit < units.length - 1);
        return new DecimalFormat("#,##0.0").format(value) + " " + units[unit];
    }

    private void installLivePreviewListeners() {
        final ActionListener listener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateLivePreview();
                markImbalanceStale();
            }
        };
        JComboBox<?>[] combos = new JComboBox<?>[] {
                invoiceCombo, stockCombo, descriptionCombo, quantityCombo, dateCombo, priceCombo,
                customerCombo, countryCombo, customReferenceCombo, customPostCombo, customDateCombo,
                customUserIdCombo, customUserNameCombo, customTextCombo
        };
        for (JComboBox<?> combo : combos) {
            combo.addActionListener(listener);
        }
        JCheckBox[] boxes = new JCheckBox[] {
                skipCancelledCheck, skipMissingCustomerCheck, skipQuantityCheck, skipPriceCheck,
                stockPrefixCheck, stableFolloweeCheck
        };
        for (JCheckBox box : boxes) {
            box.addActionListener(listener);
        }
        DocumentListener documentListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                updateLivePreview();
                markImbalanceStale();
            }

            public void removeUpdate(DocumentEvent e) {
                updateLivePreview();
                markImbalanceStale();
            }

            public void changedUpdate(DocumentEvent e) {
                updateLivePreview();
                markImbalanceStale();
            }
        };
        minTokensField.getDocument().addDocumentListener(documentListener);
        fallbackDateField.getDocument().addDocumentListener(documentListener);
        DocumentListener balanceDocumentListener = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                updateBalanceGuide();
                markImbalanceStale();
            }

            public void removeUpdate(DocumentEvent e) {
                updateBalanceGuide();
                markImbalanceStale();
            }

            public void changedUpdate(DocumentEvent e) {
                updateBalanceGuide();
                markImbalanceStale();
            }
        };
        balanceTargetField.getDocument().addDocumentListener(balanceDocumentListener);
        balanceSeedField.getDocument().addDocumentListener(balanceDocumentListener);
    }

    private void updateLivePreview() {
        if (suppressLivePreview || livePreviewModel == null) {
            return;
        }
        livePreviewModel.setRowCount(0);
        if (outputPreviewModel != null && lastReport == null) {
            outputPreviewModel.setRowCount(0);
        }
        if (sourceProfile == null) {
            updateLiveMappingSummary(null);
            if (livePreviewNote != null) {
                livePreviewNote.setText("Inspect a source file to see the live DSMP shape.");
            }
            return;
        }
        try {
            UciRetailDatasetImporter.Options options = collectOptions();
            updateLiveMappingSummary(options);
            UciRetailDatasetImporter.LivePreviewReport preview =
                    UciRetailDatasetImporter.previewOutputRows(sourceProfile, options, 5);
            if (outputPreviewModel != null) {
                outputPreviewModel.setRowCount(0);
            }
            for (String[] row : preview.rows) {
                livePreviewModel.addRow(row);
                if (outputPreviewModel != null) {
                    outputPreviewModel.addRow(row);
                }
            }
            String note = preview.summaryLine();
            if (stableFolloweeCheck.isSelected()
                    && options.profile != UciRetailDatasetImporter.MappingProfile.SIMILAR_CUSTOMERS) {
                note += " Final build may use the full-dataset dominant followee per user.";
            }
            livePreviewNote.setText(note);
        } catch (Exception ex) {
            updateLiveMappingSummary(null);
            if (outputPreviewModel != null && lastReport == null) {
                outputPreviewModel.setRowCount(0);
            }
            livePreviewNote.setText("Preview waiting: " + rootMessage(ex));
        }
    }

    private void updateLiveMappingSummary(UciRetailDatasetImporter.Options options) {
        if (liveMappingLabels[0] == null) {
            return;
        }
        if (sourceProfile == null || options == null) {
            setLiveMapping(0, "Inspect first");
            setLiveMapping(1, "Inspect first");
            setLiveMapping(2, "Inspect first");
            setLiveMapping(3, "Inspect first");
            setLiveMapping(4, "Inspect first");
            setLiveMapping(5, "Inspect first");
            return;
        }

        UciRetailDatasetImporter.MappingProfile profile = options.profile;
        if (profile == UciRetailDatasetImporter.MappingProfile.SIMILAR_CUSTOMERS) {
            setLiveMapping(0, "Constant collection label");
        } else if (profile == UciRetailDatasetImporter.MappingProfile.SMART_PRODUCT_CATEGORY) {
            setLiveMapping(0, "Smart retail category");
        } else if (profile == UciRetailDatasetImporter.MappingProfile.PRODUCT_FAMILY) {
            setLiveMapping(0, "Legacy text + product");
        } else if (profile == UciRetailDatasetImporter.MappingProfile.COUNTRY_BASELINE) {
            setLiveMapping(0, columnLabel(options.countryColumn));
        } else if (profile == UciRetailDatasetImporter.MappingProfile.STOCK_CODE_EXPERIMENTAL) {
            setLiveMapping(0, columnLabel(options.stockCodeColumn));
        } else if (profile == UciRetailDatasetImporter.MappingProfile.JOURNAL_AUTHORS
                || profile == UciRetailDatasetImporter.MappingProfile.TWEET_USERS) {
            setLiveMapping(0, columnLabel(options.countryColumn));
        } else {
            setLiveMapping(0, columnLabel(options.customReferenceColumn));
        }

        if (profile == UciRetailDatasetImporter.MappingProfile.CUSTOM) {
            setLiveMapping(1, columnLabel(options.customPostIdColumn));
            setLiveMapping(2, columnLabel(options.customDateColumn));
            setLiveMapping(3, columnLabel(options.customUserIdColumn));
            setLiveMapping(4, columnLabel(options.customUserNameColumn));
            setLiveMapping(5, columnLabel(options.customTextColumn));
        } else {
            setLiveMapping(1, options.invoiceColumn >= 0 ? columnLabel(options.invoiceColumn) : "Generated row id");
            setLiveMapping(2, options.invoiceDateColumn >= 0 ? columnLabel(options.invoiceDateColumn) : "Fallback date");
            setLiveMapping(3, columnLabel(options.customerIdColumn));
            if ((profile == UciRetailDatasetImporter.MappingProfile.JOURNAL_AUTHORS
                    || profile == UciRetailDatasetImporter.MappingProfile.TWEET_USERS)
                    && options.customUserNameColumn >= 0) {
                setLiveMapping(4, columnLabel(options.customUserNameColumn));
            } else {
                setLiveMapping(4, "Customer_<id>");
            }
            setLiveMapping(5, columnLabel(options.descriptionColumn));
        }
    }

    private void setLiveMapping(int index, String text) {
        if (index < 0 || index >= liveMappingLabels.length || liveMappingLabels[index] == null) {
            return;
        }
        String value = text == null || text.trim().length() == 0 ? "Not selected" : text;
        String display = value.length() > 24 ? value.substring(0, 21) + "..." : value;
        liveMappingLabels[index].setText(display);
        liveMappingLabels[index].setToolTipText(value);
    }

    private String columnLabel(int column) {
        return sourceProfile == null ? "Not selected" : sourceProfile.displayColumn(column);
    }

    private void updateProfileGuide() {
        UciRetailDatasetImporter.MappingProfile profile = (UciRetailDatasetImporter.MappingProfile) profileCombo.getSelectedItem();
        if (profile == null) {
            profile = selectedDatasetKind == UciRetailDatasetImporter.DatasetKind.OTHER
                    ? UciRetailDatasetImporter.MappingProfile.CUSTOM
                    : UciRetailDatasetImporter.MappingProfile.SIMILAR_CUSTOMERS;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(selectedDatasetKind).append(" source\n");
        sb.append(selectedDatasetKind.description());
        sb.append("\n\nProfile\n");
        sb.append(profile.description());
        sb.append("\n\nOutput rule: DSMP stays exactly six columns; simulator algorithms and scoring logic are unchanged.");
        if (profile == UciRetailDatasetImporter.MappingProfile.SIMILAR_CUSTOMERS) {
            sb.append("\nClassifier note: all users share one followee label, so SVM/MLP are not meaningful here. Use it for user-similarity algorithms.");
        } else if (profile == UciRetailDatasetImporter.MappingProfile.SMART_PRODUCT_CATEGORY) {
            sb.append("\nClassifier note: this scans each retail description with a broad category taxonomy, then keeps each customer's dominant category as a stable SVM/MLP target.");
        } else if (profile == UciRetailDatasetImporter.MappingProfile.PRODUCT_FAMILY) {
            sb.append("\nCompatibility note: this is the older compact product-family mapper. Prefer smart product category for new UCI Online Retail work.");
        } else if (profile == UciRetailDatasetImporter.MappingProfile.STOCK_CODE_EXPERIMENTAL) {
            sb.append("\nRisk note: high-cardinality product labels can make SVM/MLP heavy. Prefix grouping is strongly recommended.");
        } else if (profile == UciRetailDatasetImporter.MappingProfile.JOURNAL_AUTHORS) {
            sb.append("\nJournal note: the venue/journal becomes the followee label, authors become users, and titles/abstracts become text.");
        } else if (profile == UciRetailDatasetImporter.MappingProfile.TWEET_USERS) {
            sb.append("\nTweet note: the reference/source account becomes the followee label, Twitter users become users, and tweet text becomes the document body.");
        } else if (profile == UciRetailDatasetImporter.MappingProfile.CUSTOM) {
            sb.append("\nCustom mode: choose every DSMP field yourself. The importer still validates six-column compatibility before writing.");
        }
        profileGuide.setText(sb.toString());
        if (customMappingCard != null) {
            customMappingCard.setVisible(profile == UciRetailDatasetImporter.MappingProfile.CUSTOM);
        }
        updateLivePreview();
        revalidate();
        repaint();
    }

    private void updateBalanceGuide() {
        if (balanceGuide == null || balanceModeCombo == null
                || balanceTargetPolicyCombo == null) {
            return;
        }
        UciRetailDatasetImporter.DatasetBalanceMode mode =
                (UciRetailDatasetImporter.DatasetBalanceMode) balanceModeCombo.getSelectedItem();
        if (mode == null) {
            mode = UciRetailDatasetImporter.DatasetBalanceMode.OFF;
        }
        UciRetailDatasetImporter.DatasetBalanceTargetPolicy policy =
                (UciRetailDatasetImporter.DatasetBalanceTargetPolicy)
                        balanceTargetPolicyCombo.getSelectedItem();
        if (policy == null) {
            policy = recommendedPolicyForMode(mode);
        }
        boolean manual = policy
                == UciRetailDatasetImporter.DatasetBalanceTargetPolicy.MANUAL;
        balanceTargetField.setEnabled(manual);
        int target = parseNonNegativeInt(balanceTargetField.getText(), 0);
        long seed = parseLong(balanceSeedField.getText(), 1L);
        String targetText = manual
                ? (target <= 0
                        ? "waiting for a positive manual target"
                        : target + " users per followee class")
                : policy.description();
        StringBuilder sb = new StringBuilder();
        sb.append(mode.description());
        sb.append("\n\nScope: after validation and stable followee selection, before writing the DSMP file.");
        sb.append("\nTarget policy: ").append(policy).append(".");
        sb.append("\nTarget meaning: ").append(targetText).append(".");
        sb.append("\nSeed: ").append(seed).append(" for repeatable sampling.");
        if (mode == UciRetailDatasetImporter.DatasetBalanceMode.DIAGNOSE_ONLY
                || mode == UciRetailDatasetImporter.DatasetBalanceMode.OFF) {
            sb.append("\nRecommended workflow: diagnose first; use Weka supervised Resample with Preserve total size when you need a balanced exported artifact.");
        } else if (mode == UciRetailDatasetImporter.DatasetBalanceMode.STRICT_USER_BALANCE
                || mode == UciRetailDatasetImporter.DatasetBalanceMode.WEKA_SPREAD_SUBSAMPLE) {
            sb.append("\nStrict downsampling modes cap any target above the smallest class because they do not invent users.");
        }
        if (mode == UciRetailDatasetImporter.DatasetBalanceMode.HYBRID_CAP_AND_AUGMENT) {
            sb.append("\nSynthetic users get deterministic DSMP user/post ids while keeping the original text.");
        } else if (mode == UciRetailDatasetImporter.DatasetBalanceMode.WEKA_SPREAD_SUBSAMPLE
                || mode == UciRetailDatasetImporter.DatasetBalanceMode.WEKA_SUPERVISED_RESAMPLE) {
            sb.append("\nWeka runs over one instance per DSMP user/class, then selected users are written as DSMP rows.");
        }
        balanceGuide.setText(sb.toString());
    }

    private void applyRecommendedBalanceTargetPolicyIfUnpinned() {
        if (!balancePolicyUserSelected && balanceModeCombo != null
                && balanceTargetPolicyCombo != null) {
            UciRetailDatasetImporter.DatasetBalanceMode mode =
                    (UciRetailDatasetImporter.DatasetBalanceMode)
                            balanceModeCombo.getSelectedItem();
            setBalanceTargetPolicy(recommendedPolicyForMode(mode));
        }
    }

    private void setBalanceTargetPolicy(
            UciRetailDatasetImporter.DatasetBalanceTargetPolicy policy) {
        if (balanceTargetPolicyCombo == null || policy == null) {
            return;
        }
        suppressBalancePolicyEvents = true;
        try {
            balanceTargetPolicyCombo.setSelectedItem(policy);
        } finally {
            suppressBalancePolicyEvents = false;
        }
    }

    private static UciRetailDatasetImporter.DatasetBalanceTargetPolicy recommendedPolicyForMode(
            UciRetailDatasetImporter.DatasetBalanceMode mode) {
        return UciRetailDatasetImporter.recommendedTargetPolicyForMode(mode);
    }

    private void setIdleState() {
        inspectButton.setEnabled(false);
        buildButton.setEnabled(false);
        useOutputButton.setEnabled(false);
        resetButton.setEnabled(true);
        updateDatasetKindUi();
        updateBalanceGuide();
        log("Ready.\nChoose Retail, Journal, Tweets, or Other, then inspect a source file before building DSMP output.");
    }

    private void setBusy(boolean busy) {
        inspectButton.setEnabled(!busy && selectedFile != null);
        buildButton.setEnabled(!busy && sourceProfile != null);
        resetButton.setEnabled(!busy);
        useOutputButton.setEnabled(!busy && lastReport != null && lastReport.outputFile != null && lastReport.outputFile.isFile());
        setCursor(Cursor.getPredefinedCursor(busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    private void setStatus(String state, String message, int progress) {
        statusBadge.setText(state);
        if ("FAILED".equals(state)) {
            statusBadge.setBackground(StudioNightTheme.adaptiveColor(
                    new Color(254, 226, 226), StudioNightTheme.NIGHT_PANEL_INK));
            statusBadge.setForeground(StudioNightTheme.adaptiveColor(
                    new Color(185, 28, 28), StudioNightTheme.NIGHT_RED));
        } else if ("BUILDING".equals(state) || "INSPECTING".equals(state)) {
            statusBadge.setBackground(SOFT_BLUE);
            statusBadge.setForeground(BLUE_DARK);
        } else {
            statusBadge.setBackground(SOFT_GREEN);
            statusBadge.setForeground(GREEN);
        }
        progressBar.setValue(Math.max(0, Math.min(100, progress)));
        progressBar.setString(message);
        updateConsoleStats(state, null, null);
    }

    private void log(String message) {
        if (logArea == null) {
            return;
        }
        consoleEventCount++;
        if (consoleEventValue != null) {
            consoleEventValue.setText(consoleEventCount + (consoleEventCount == 1 ? " EVENT" : " EVENTS"));
        }
        String stamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String normalized = message == null ? "" : message.trim();
        normalized = normalized.replace("\r\n", "\n").replace('\r', '\n');
        normalized = normalized.replace("\n", "\n           ");
        if (logArea.getText().length() > 0) {
            logArea.append("\n");
        }
        logArea.append("[" + stamp + "] " + normalized);
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void logProgress(String phase, String message, int percent) {
        int bucket = Math.max(0, Math.min(5, percent / 20));
        if ("INSPECT".equals(phase)) {
            if (bucket == inspectProgressBucket && percent < 100) {
                return;
            }
            inspectProgressBucket = bucket;
        } else {
            if (bucket == buildProgressBucket && percent < 100) {
                return;
            }
            buildProgressBucket = bucket;
        }
        log(phase + " " + Math.max(0, Math.min(100, percent)) + "% - " + message);
    }

    private void updateConsoleStats(String phase, String rows, String output) {
        if (consolePhaseValue != null && phase != null) {
            consolePhaseValue.setText(phase);
        }
        if (consoleRowsValue != null && rows != null) {
            consoleRowsValue.setText(rows);
        }
        if (consoleOutputValue != null && output != null) {
            String value = output.trim().length() == 0 ? "-" : output;
            consoleOutputValue.setText(value.length() > 24 ? value.substring(0, 21) + "..." : value);
            consoleOutputValue.setToolTipText(value);
        }
    }

    private void clearTables() {
        if (previewModel != null) {
            previewModel.setRowCount(0);
            previewModel.setColumnCount(0);
        }
        if (columnModel != null) {
            columnModel.setRowCount(0);
        }
        if (outputPreviewModel != null) {
            outputPreviewModel.setRowCount(0);
        }
        if (livePreviewModel != null) {
            livePreviewModel.setRowCount(0);
        }
        if (livePreviewNote != null) {
            livePreviewNote.setText("Inspect a source file to see the live DSMP shape.");
        }
        resetImbalancePanel("Inspect a dataset to see class imbalance, balance impact, and warnings here.");
        updateLiveMappingSummary(null);
        rowsMetric.setValue("-");
        columnsMetric.setValue("-");
        recognitionMetric.setValue("-");
        usersMetric.setValue("-");
        outputMetric.setValue("-");
    }

    private static DefaultComboBoxModel<String> columnModel(UciRetailDatasetImporter.SourceProfile source) {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<String>();
        model.addElement("Not selected");
        for (int i = 0; i < source.columnNames.size(); i++) {
            model.addElement((i + 1) + " - " + source.columnNames.get(i));
        }
        return model;
    }

    private static DefaultComboBoxModel<String> copyModel(DefaultComboBoxModel<String> source) {
        DefaultComboBoxModel<String> copy = new DefaultComboBoxModel<String>();
        for (int i = 0; i < source.getSize(); i++) {
            copy.addElement(source.getElementAt(i));
        }
        return copy;
    }

    private static void setCombo(JComboBox<String> combo, int column) {
        combo.setSelectedIndex(column < 0 ? 0 : column + 1);
    }

    private static int comboIndex(JComboBox<String> combo) {
        return combo.getSelectedIndex() <= 0 ? -1 : combo.getSelectedIndex() - 1;
    }

    private static int parsePositiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static int parseNonNegativeInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= 0 ? parsed : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static String safeBase(String filename) {
        String raw = filename == null ? "ImportedDataset" : filename;
        int dot = raw.lastIndexOf('.');
        if (dot > 0) {
            raw = raw.substring(0, dot);
        }
        return raw.replaceAll("[^A-Za-z0-9_-]+", "_");
    }

    private static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage() == null ? t.toString() : t.getMessage();
    }

    private static JTextField textField(String text) {
        JTextField field = new JTextField(text);
        field.setFont(field.getFont().deriveFont(Font.BOLD, 12f));
        field.setForeground(INK);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                new EmptyBorder(8, 10, 8, 10)));
        return field;
    }

    private static JComboBox<String> combo() {
        JComboBox<String> combo = new JComboBox<String>();
        combo.setFont(combo.getFont().deriveFont(Font.BOLD, 12f));
        combo.setBackground(CARD);
        return combo;
    }

    private static JCheckBox check(String label, boolean selected) {
        JCheckBox box = new JCheckBox(label, selected);
        box.setOpaque(false);
        box.setForeground(INK);
        box.setFont(box.getFont().deriveFont(Font.BOLD, 12f));
        return box;
    }

    private static JButton button(String label, Color bg, Color fg) {
        JButton button = new JButton(label);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBackground(bg);
        button.setForeground(fg);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 0, 0, 20)),
                new EmptyBorder(11, 14, 11, 14)));
        button.setFont(button.getFont().deriveFont(Font.BOLD, 12f));
        return button;
    }

    private static JLabel badge(String text, Color bg, Color fg) {
        JLabel label = new JLabel(text);
        label.setOpaque(true);
        label.setBackground(bg);
        label.setForeground(fg);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 70)),
                new EmptyBorder(5, 9, 5, 9)));
        return label;
    }

    private static JTextArea helpText(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setForeground(MUTED);
        area.setFont(area.getFont().deriveFont(Font.PLAIN, 12f));
        area.setBorder(new EmptyBorder(8, 0, 2, 0));
        return area;
    }

    private static JTextArea callout(String text) {
        JTextArea area = helpText(text);
        area.setOpaque(true);
        area.setBackground(SURFACE);
        area.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE),
                new EmptyBorder(9, 11, 9, 11)));
        return area;
    }

    private static JPanel labeled(String label, Component component) {
        return labeled(fieldLabel(label), component);
    }

    private static JPanel labeled(JLabel label, Component component) {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setOpaque(false);
        panel.add(label, BorderLayout.NORTH);
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private static JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        return label;
    }

    private static JPanel row(Component left, Component right) {
        JPanel row = new JPanel(new java.awt.GridLayout(1, 2, 8, 0));
        row.setOpaque(false);
        row.add(left);
        row.add(right);
        return row;
    }

    private static JPanel twoColumn(Component left, Component right) {
        JPanel row = new JPanel(new java.awt.GridLayout(1, 2, 10, 0));
        row.setOpaque(false);
        row.add(left);
        row.add(right);
        row.setBorder(new EmptyBorder(0, 0, 8, 0));
        return row;
    }

    private static RoundedPanel card(String title) {
        RoundedPanel panel = new RoundedPanel(CARD);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(13, 14, 14, 14));

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        String headerText = title;
        int dot = title.indexOf(". ");
        if (dot > 0 && dot <= 2) {
            JLabel step = badge(title.substring(0, dot), SOFT_BLUE, BLUE_DARK);
            step.setHorizontalAlignment(SwingConstants.CENTER);
            step.setPreferredSize(new Dimension(28, 24));
            header.add(step, BorderLayout.WEST);
            headerText = title.substring(dot + 2);
        }
        JLabel label = new JLabel(headerText);
        label.setForeground(INK);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 14f));
        header.add(label, BorderLayout.CENTER);
        panel.add(header);
        panel.add(Box.createVerticalStrut(10));
        return panel;
    }

    private static final class WorkflowPanel extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;

        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(470, 700);
        }

        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 18;
        }

        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(80, visibleRect.height - 80);
        }

        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static JTable styledTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(28);
        table.setShowGrid(true);
        table.setGridColor(LINE);
        table.setForeground(INK);
        table.setSelectionBackground(new Color(219, 234, 254));
        table.setSelectionForeground(INK);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        JTableHeader header = table.getTableHeader();
        header.setBackground(SURFACE);
        header.setForeground(INK);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
        return table;
    }

    private static class RoundedPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private final Color background;

        RoundedPanel(Color background) {
            this.background = background;
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(background);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.setColor(LINE);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class DatasetKindButton extends JButton {
        private static final long serialVersionUID = 1L;
        private final UciRetailDatasetImporter.DatasetKind kind;
        private boolean selected;

        DatasetKindButton(UciRetailDatasetImporter.DatasetKind kind) {
            super("");
            this.kind = kind;
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(0, 0, 0, 0));
            setPreferredSize(new Dimension(200, 96));
            setMinimumSize(new Dimension(160, 92));
            setToolTipText(kind + ": " + kind.description());
            setOpaque(false);
        }

        @Override
        public void setSelected(boolean selected) {
            this.selected = selected;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean hover = getModel().isRollover();
            Color fill = selected ? SOFT_BLUE : hover ? SURFACE : CARD;
            Color stroke = selected ? BLUE : LINE;
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.setColor(stroke);
            g2.setStroke(new BasicStroke(selected ? 1.8f : 1.0f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);

            Color iconFill = selected ? BLUE : SURFACE;
            Color iconText = selected ? Color.WHITE : BLUE_DARK;
            g2.setColor(iconFill);
            g2.fillRoundRect(13, 14, 34, 34, 10, 10);
            g2.setColor(selected ? new Color(255, 255, 255, 90) : new Color(BLUE_DARK.getRed(), BLUE_DARK.getGreen(), BLUE_DARK.getBlue(), 60));
            g2.drawRoundRect(13, 14, 34, 34, 10, 10);

            g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
            g2.setColor(iconText);
            FontMetrics iconFm = g2.getFontMetrics();
            String code = shortCode(kind);
            g2.drawString(code, 30 - iconFm.stringWidth(code) / 2, 36);

            int textX = 58;
            int textWidth = Math.max(40, getWidth() - textX - 18);
            g2.setFont(getFont().deriveFont(Font.BOLD, 13f));
            g2.setColor(INK);
            g2.drawString(kind.toString(), textX, 28);
            g2.setFont(getFont().deriveFont(Font.PLAIN, 10.5f));
            g2.setColor(MUTED);
            drawWrapped(g2, kind.description(), textX, 44, textWidth, 3);

            if (selected) {
                g2.setColor(BLUE);
                g2.fillOval(getWidth() - 18, 12, 8, 8);
            }
            g2.dispose();
        }

        private static String shortCode(UciRetailDatasetImporter.DatasetKind kind) {
            if (kind == UciRetailDatasetImporter.DatasetKind.JOURNAL) {
                return "JR";
            }
            if (kind == UciRetailDatasetImporter.DatasetKind.TWEETS) {
                return "TW";
            }
            if (kind == UciRetailDatasetImporter.DatasetKind.OTHER) {
                return "OT";
            }
            return "RT";
        }

        private static void drawWrapped(Graphics2D g2, String text, int x, int y, int maxWidth, int maxLines) {
            FontMetrics fm = g2.getFontMetrics();
            String[] words = text.split("\\s+");
            StringBuilder line = new StringBuilder();
            int lineCount = 0;
            for (int i = 0; i < words.length; i++) {
                String next = line.length() == 0 ? words[i] : line + " " + words[i];
                if (fm.stringWidth(next) > maxWidth && line.length() > 0) {
                    boolean lastAllowed = lineCount == maxLines - 1;
                    String rendered = lastAllowed ? ellipsize(line.toString(), fm, maxWidth) : line.toString();
                    g2.drawString(rendered, x, y + lineCount * 14);
                    lineCount++;
                    line.setLength(0);
                    line.append(words[i]);
                    if (lineCount >= maxLines) {
                        return;
                    }
                } else {
                    line.setLength(0);
                    line.append(next);
                }
            }
            if (line.length() > 0 && lineCount < maxLines) {
                g2.drawString(ellipsize(line.toString(), fm, maxWidth), x, y + lineCount * 14);
            }
        }

        private static String ellipsize(String text, FontMetrics fm, int maxWidth) {
            if (fm.stringWidth(text) <= maxWidth) {
                return text;
            }
            String suffix = "...";
            String value = text;
            while (value.length() > 0 && fm.stringWidth(value + suffix) > maxWidth) {
                value = value.substring(0, value.length() - 1);
            }
            return value.length() == 0 ? suffix : value + suffix;
        }
    }

    private static final class NetworkHeroPanel extends RoundedPanel {
        private static final long serialVersionUID = 1L;
        private final javax.swing.Timer timer;
        private float phase;

        NetworkHeroPanel() {
            super(CARD);
            timer = new javax.swing.Timer(16, new ActionListener() {
                public void actionPerformed(ActionEvent event) {
                    phase += 0.018f;
                    if (phase > 1.0f) {
                        phase -= 1.0f;
                    }
                    repaint();
                }
            });
            timer.setCoalesce(true);
            addHierarchyListener(new HierarchyListener() {
                public void hierarchyChanged(HierarchyEvent event) {
                    if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                        updateAnimationState();
                    }
                }
            });
        }

        @Override
        public void addNotify() {
            super.addNotify();
            updateAnimationState();
        }

        @Override
        public void removeNotify() {
            timer.stop();
            super.removeNotify();
        }

        private void updateAnimationState() {
            if (isShowing()) {
                if (!timer.isRunning()) {
                    timer.start();
                }
            } else {
                timer.stop();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth();
            int height = getHeight();
            int left = Math.max(width / 3, 360);
            int right = Math.max(left + 280, width - 250);
            int span = Math.max(280, right - left);
            int[] xs = new int[] {
                    left,
                    left + span / 5,
                    left + span * 2 / 5,
                    left + span * 3 / 5,
                    left + span * 4 / 5,
                    right
            };
            int[] ys = new int[xs.length];
            for (int i = 0; i < xs.length; i++) {
                ys[i] = Math.round(height / 2.0f
                        + (float) Math.sin((phase + i * 0.17f) * Math.PI * 2.0f) * 14.0f);
            }

            g2.setComposite(AlphaComposite.SrcOver.derive(0.55f));
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(BLUE_DARK.getRed(), BLUE_DARK.getGreen(), BLUE_DARK.getBlue(), 86));
            for (int i = 0; i < xs.length - 1; i++) {
                g2.drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1]);
            }
            g2.setColor(new Color(GREEN.getRed(), GREEN.getGreen(), GREEN.getBlue(), 70));
            g2.drawLine(xs[1], ys[1], xs[3], ys[3]);
            g2.drawLine(xs[2], ys[2], xs[4], ys[4]);

            g2.setComposite(AlphaComposite.SrcOver);
            for (int i = 0; i < xs.length; i++) {
                Color fill = i % 2 == 0 ? BLUE_DARK : GREEN;
                g2.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 42));
                g2.fillOval(xs[i] - 7, ys[i] - 7, 14, 14);
                g2.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 160));
                g2.drawOval(xs[i] - 7, ys[i] - 7, 14, 14);
                g2.setColor(new Color(255, 255, 255, 225));
                g2.fillOval(xs[i] - 3, ys[i] - 3, 6, 6);
            }
            g2.dispose();
        }
    }

    private static final class MetricCard extends RoundedPanel {
        private static final long serialVersionUID = 1L;
        private final JLabel valueLabel;

        MetricCard(String title, String value, String caption) {
            super(CARD);
            setLayout(new BorderLayout(4, 4));
            setBorder(new EmptyBorder(12, 12, 12, 12));
            JLabel titleLabel = new JLabel(title.toUpperCase(Locale.ENGLISH));
            titleLabel.setForeground(MUTED);
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 11f));
            valueLabel = new JLabel(value);
            valueLabel.setForeground(INK);
            valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 20f));
            JLabel captionLabel = new JLabel(caption);
            captionLabel.setForeground(MUTED);
            captionLabel.setFont(captionLabel.getFont().deriveFont(Font.PLAIN, 11f));
            add(titleLabel, BorderLayout.NORTH);
            add(valueLabel, BorderLayout.CENTER);
            add(captionLabel, BorderLayout.SOUTH);
        }

        void setValue(String value) {
            valueLabel.setText(value);
        }
    }
}
