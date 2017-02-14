import models.NonEditableTableModel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import org.ocpsoft.prettytime.PrettyTime;

class SearchQueryWindow extends JFrame {
    private static final String[] tableHeader = new String[] { "Email", "Last sent" };

    private JTextField keywordsInput;

    private DefaultComboBoxModel<String> availableFolders;

    private PrettyTime _prettyTime;
    private NonEditableTableModel availableAddresseesModel;
    private JTable availableAddressees;
    private NonEditableTableModel selectedAddresseesModel;
    private JTable selectedAddressees;

    SearchQueryWindow() {
        _prettyTime = new PrettyTime();
        JPanel mainPanel = new JPanel();
        mainPanel.setBorder(new EmptyBorder(10, 10, 10 ,10));
        mainPanel.setLayout(new GridBagLayout());


        JPanel group0 = new JPanel();
        group0.setLayout(new GridLayout(1, 1));
        group0.setBorder(new TitledBorder("1. Input search query below"));
        keywordsInput = new JTextField();
        group0.add(keywordsInput);


        JPanel group1 = new JPanel();
        group1.setLayout(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();
        c.weighty = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridy = 0;

        availableFolders = new DefaultComboBoxModel<>();
        JComboBox folder = new JComboBox<>(availableFolders);
        folder.setBorder(new TitledBorder("2. Select Folder"));
        c.weightx = 0.45;
        c.gridx = 0;
        group1.add(folder, c);

        // Spacer
        c.weightx = 0.2;
        c.gridx = 1;
        group1.add(new JPanel(), c);

        JButton checkButton = new JButton();
        checkButton.setText("Check");
        ActionListener runQuery = e -> {
            availableAddresseesModel.setRowCount(0);
            BatchEmailSender
                    .getEmailsByQueryInRemote(
                            keywordsInput.getText(),
                            availableFolders.getSelectedItem().toString()
                    ).thenApply(results -> {
                CompletableFuture<ConcurrentMap<String, Long>> table = new CompletableFuture<>();
                try {
                    ConcurrentMap lastSentListFromLocalDB = BatchEmailSender.getLastSentEmailsFromLocalDB().get();
                    ConcurrentMap<String, Long> join = results.stream()
                            .collect(Collectors.toConcurrentMap(
                                    result -> result,
                                    result -> (Long) lastSentListFromLocalDB.getOrDefault(result, 0L)
                            ));
                    table.complete(join);
                } catch (InterruptedException | ExecutionException e1) {
                    e1.printStackTrace();
                    table.cancel(true);
                }

                return table;
            }).thenApply(previous -> {
                CompletableFuture<ConcurrentMap<String, Long>> result = new CompletableFuture<>();
                try {
                    List<String> selectedEmails = new ArrayList<>();
                    for (int i = 0; i < selectedAddressees.getRowCount(); i++) {
                        selectedEmails.add(selectedAddresseesModel.getValueAt(i, 0).toString());
                    }
                    ConcurrentMap<String, Long> reduced = previous.get();
                    selectedEmails.forEach(reduced::remove);
                    result.complete(reduced);
                } catch (InterruptedException | ExecutionException e1) {
                    e1.printStackTrace();
                    result.cancel(true);
                }
                return result;
            }).thenApply(previous -> {
                CompletableFuture result = new CompletableFuture();
                try {
                    ConcurrentMap<String, Long> table = previous.get();
                    table.forEach((key, value) -> {
                        Object[] data = new Object[2];
                        data[0] = key;
                        if (value.equals(0L)) data[1] = "Never";
                        else data[1] = _prettyTime.format(new Date(value));
                        availableAddresseesModel.addRow(data);
                    });
                } catch (InterruptedException | ExecutionException e1) {
                    e1.printStackTrace();
                    result.cancel(true);
                }

                return result;
            });

            BatchEmailSender.setSettings(keywordsInput.getText(), availableFolders.getSelectedItem().toString(), null, null, null, null);
        };
        checkButton.addActionListener(runQuery);
        JPanel checkButtonWrapper = new JPanel();
        checkButtonWrapper.setLayout(new GridLayout(1,1));
        checkButtonWrapper.setBorder(new TitledBorder("3. Press the button below"));
        checkButtonWrapper.add(checkButton);
        c.weightx = 0.35;
        c.gridx = 2;
        group1.add(checkButtonWrapper, c);


        JPanel group2 = new JPanel();
        group2.setLayout(new GridBagLayout());
        c.gridy = 0;
        c.weighty = 1;

        availableAddresseesModel = new NonEditableTableModel();
        availableAddresseesModel.setColumnIdentifiers(tableHeader);
        availableAddressees = new JTable(availableAddresseesModel);
        availableAddressees.setAutoCreateRowSorter(true);
        JPanel availableAddresseesWrapper = new JPanel();
        availableAddresseesWrapper.setBorder(new TitledBorder("4. Select the recipients"));
        availableAddresseesWrapper.setLayout(new GridLayout(1,1));
        availableAddresseesWrapper.add(new JScrollPane(availableAddressees));
        c.gridx = 0;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 0.45;
        group2.add(availableAddresseesWrapper, c);

        JButton arrowLeft = new JButton();
        arrowLeft.setText("<-");
        arrowLeft.addActionListener(e -> {
            int[] idx = { 0 };
            Arrays.stream(selectedAddressees.getSelectedRows())
                    .forEachOrdered(row -> {
                        availableAddresseesModel.addRow(new Object[] { selectedAddresseesModel.getValueAt(row - idx[0], 0), selectedAddresseesModel.getValueAt(row - idx[0], 1) });
                        selectedAddresseesModel.removeRow(row - idx[0]);
                        idx[0]++;
                    });
        });
        JButton arrowRight = new JButton();
        arrowRight.setText("->");
        arrowRight.addActionListener(e -> {
            int[] idx = { 0 };
            Arrays.stream(availableAddressees.getSelectedRows())
                    .forEachOrdered(row -> {
                        selectedAddresseesModel.addRow(new Object[] { availableAddresseesModel.getValueAt(row - idx[0], 0), availableAddresseesModel.getValueAt(row - idx[0], 1) });
                        availableAddresseesModel.removeRow(row - idx[0]);
                        idx[0]++;
                    });
        });
        JPanel group3 = new JPanel();
        group3.setLayout(new GridLayout(2, 0));
        group3.setBorder(new TitledBorder("Move"));
        group3.add(arrowLeft);
        group3.add(arrowRight);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0.1;
        group2.add(group3, c);

        selectedAddresseesModel = new NonEditableTableModel();
        selectedAddresseesModel.setColumnIdentifiers(tableHeader);
        selectedAddressees = new JTable(selectedAddresseesModel);
        selectedAddressees.setAutoCreateRowSorter(true);
        JPanel selectedAddresseesWrapper = new JPanel();
        selectedAddresseesWrapper.setBorder(new TitledBorder("Selected"));
        selectedAddresseesWrapper.setLayout(new GridLayout(1,1));
        selectedAddresseesWrapper.add(new JScrollPane(selectedAddressees));
        c.gridx = 2;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 0.45;
        group2.add(selectedAddresseesWrapper, c);

        JButton sendEmailsButton = new JButton();
        sendEmailsButton.setText("Send");
        sendEmailsButton.addActionListener(e -> {
            java.util.List<String> selectedEmails = new ArrayList<>();
            for (int i = 0; i < selectedAddressees.getRowCount(); i++) {
                selectedEmails.add(selectedAddresseesModel.getValueAt(i, 0).toString());
            }
            if (!selectedEmails.isEmpty()) {
                EmailSettingsWindow emailSettingsWindow = new EmailSettingsWindow(selectedEmails);
                emailSettingsWindow.setVisible(true);
                this.setVisible(false);
            } else {
                JOptionPane.showMessageDialog (null, "The list of selected emails cannot be empty", "Empty Selected Emails List", JOptionPane.WARNING_MESSAGE);
            }
        });
        JPanel sendEmailsButtonWrapper = new JPanel();
        sendEmailsButtonWrapper.setLayout(new GridLayout(1,1));
        sendEmailsButtonWrapper.setBorder(new TitledBorder("5. Press the button below"));
        sendEmailsButtonWrapper.add(sendEmailsButton);

        c.weightx = 1;
        c.fill = GridBagConstraints.BOTH;

        c.gridy = 0;
        c.weighty = 0.05;
        mainPanel.add(group0, c);

        c.gridy = 1;
        c.weighty = 0.05;
        mainPanel.add(group1, c);

        c.gridy = 2;
        c.weighty = 0.85;
        mainPanel.add(group2, c);

        c.gridy = 3;
        c.weighty = 0.05;
        c.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(sendEmailsButtonWrapper, c);

        setContentPane(mainPanel);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        // setResizable(false);
        setTitle("Select Recipients");
        setSize(1024, 300);

        BatchEmailSender.getLabelsList().thenApply((List<String> labels) -> {
            labels.forEach((Object label) -> availableFolders.addElement(label.toString()));
            return null;
        });
        try {
            availableFolders.setSelectedItem(BatchEmailSender.getSettings().get().getOrDefault("LRU Folder", "INBOX"));
            keywordsInput.setText(BatchEmailSender.getSettings().get().getOrDefault("Last Query", ""));
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}
