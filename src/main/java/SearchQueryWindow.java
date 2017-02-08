import models.NonEditableTableModel;
import models.NonEditableTableModel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import org.ocpsoft.prettytime.PrettyTime;

/**
 * Created by rkhunter on 1/31/17.
 */
public class SearchQueryWindow extends JFrame {
    private static final String[] tableHeader = new String[] { "Email", "Last sent" };
    private JPanel mainPanel;

    private JPanel group0;
    private JTextField keywordsInput;

    private JPanel group1;
    private DefaultComboBoxModel availableFolders;
    private JComboBox folder;
    private ActionListener runQuery;
    private JButton checkButton;
    private JPanel checkButtonWrapper;

    PrettyTime _prettyTime;
    private JPanel group2;
    private NonEditableTableModel availableAddressantsModel;
    private JTable availableAddressants;
    private JPanel availableAddressantsWrapper;
    private JPanel group3;
    private JButton arrowRight;
    private JButton arrowLeft;
    private NonEditableTableModel selectedAddressantsModel;
    private JTable selectedAddressants;
    private JPanel selectedAddressantsWrapper;

    private JPanel sendEmailsButtonWrapper;
    private JButton sendEmailsButton;

    SearchQueryWindow() {
        _prettyTime = new PrettyTime();
        mainPanel = new JPanel();
        mainPanel.setBorder(new EmptyBorder(10, 10, 10 ,10));
        mainPanel.setLayout(new GridBagLayout());


        group0 = new JPanel();
        group0.setLayout(new GridLayout(1, 1));
        group0.setBorder(new TitledBorder("1. Input search query below"));
        keywordsInput = new JTextField();
        group0.add(keywordsInput);


        group1 = new JPanel();
        group1.setLayout(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();
        c.weighty = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridy = 0;

        availableFolders = new DefaultComboBoxModel();
        folder = new JComboBox(availableFolders);
        folder.setBorder(new TitledBorder("2. Select Folder"));
        c.weightx = 0.45;
        c.gridx = 0;
        group1.add(folder, c);

        // Spacer
        c.weightx = 0.2;
        c.gridx = 1;
        group1.add(new JPanel(), c);

        checkButton = new JButton();
        checkButton.setText("Check");
        runQuery = (e -> {
            availableAddressantsModel.setRowCount(0);
            BatchEmailSender
                    .getEmailsByQueryInRemote(
                            keywordsInput.getText().toString(),
                            availableFolders.getSelectedItem().toString()
                    ).thenApply(results -> {
                        CompletableFuture<ConcurrentMap<String, Long>> table = new CompletableFuture<>();
                        try {
                            ConcurrentMap lastSentListFromLocalDB = BatchEmailSender.getLastSentEmailsFromLocalDB().get();
                            ConcurrentMap<String, Long> join = results.stream()
                                                                .collect(Collectors.toConcurrentMap(
                                                                        result -> result,
                                                                        result -> ((Long) lastSentListFromLocalDB.getOrDefault(result, 0L)).longValue()
                                                                ));
                            table.complete(join);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                            table.cancel(true);
                        } catch (ExecutionException e1) {
                            e1.printStackTrace();
                            table.cancel(true);
                        }

                        return table;
                    }).thenApply(previous -> {
                        CompletableFuture<ConcurrentMap<String, Long>> result = new CompletableFuture<>();
                        try {
                            java.util.List<String> selectedEmails = new ArrayList<>();
                            for (int i = 0; i < selectedAddressants.getRowCount(); i++) {
                                selectedEmails.add(selectedAddressantsModel.getValueAt(i, 0).toString());
                            }
                            ConcurrentMap<String, Long> reduced = previous.get();
                            selectedEmails.forEach(entry -> reduced.remove(entry));
                            result.complete(reduced);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                            result.cancel(true);
                        } catch (ExecutionException e1) {
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
                                availableAddressantsModel.addRow(data);
                            });
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                            result.cancel(true);
                        } catch (ExecutionException e1) {
                            e1.printStackTrace();
                            result.cancel(true);
                        }

                        return result;
                    });

            BatchEmailSender.setSettings(keywordsInput.getText(), availableFolders.getSelectedItem().toString(), null, null, null, null);
        });
        checkButton.addActionListener(runQuery);
        checkButtonWrapper = new JPanel();
        checkButtonWrapper.setLayout(new GridLayout(1,1));
        checkButtonWrapper.setBorder(new TitledBorder("3. Press the button below"));
        checkButtonWrapper.add(checkButton);
        c.weightx = 0.35;
        c.gridx = 2;
        group1.add(checkButtonWrapper, c);




        group2 = new JPanel();
        group2.setLayout(new GridBagLayout());
        c.gridy = 0;
        c.weighty = 1;

        availableAddressantsModel = new NonEditableTableModel();
        availableAddressantsModel.setColumnIdentifiers(tableHeader);
        availableAddressants = new JTable(availableAddressantsModel);
        availableAddressants.setAutoCreateRowSorter(true);
        availableAddressantsWrapper = new JPanel();
        availableAddressantsWrapper.setBorder(new TitledBorder("4. Select the recipients"));
        availableAddressantsWrapper.setLayout(new GridLayout(1,1));
        availableAddressantsWrapper.add(new JScrollPane(availableAddressants));
        c.gridx = 0;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 0.45;
        group2.add(availableAddressantsWrapper, c);

        arrowLeft = new JButton();
        arrowLeft.setText("<-");
        arrowLeft.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int[] idx = { 0 };
                Arrays.stream(selectedAddressants.getSelectedRows())
                        .forEachOrdered(row -> {
                            availableAddressantsModel.addRow(new Object[] { selectedAddressantsModel.getValueAt(row - idx[0], 0), selectedAddressantsModel.getValueAt(row - idx[0], 1) });
                            selectedAddressantsModel.removeRow(row - idx[0]);
                            idx[0]++;
                        });
            }
        });
        arrowRight = new JButton();
        arrowRight.setText("->");
        arrowRight.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int[] idx = { 0 };
                Arrays.stream(availableAddressants.getSelectedRows())
                        .forEachOrdered(row -> {
                            selectedAddressantsModel.addRow(new Object[] { availableAddressantsModel.getValueAt(row - idx[0], 0), availableAddressantsModel.getValueAt(row - idx[0], 1) });
                            availableAddressantsModel.removeRow(row - idx[0]);
                            idx[0]++;
                        });
            }
        });
        group3 = new JPanel();
        group3.setLayout(new GridLayout(2, 0));
        group3.setBorder(new TitledBorder("Move"));
        group3.add(arrowLeft);
        group3.add(arrowRight);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0.1;
        group2.add(group3, c);

        selectedAddressantsModel = new NonEditableTableModel();
        selectedAddressantsModel.setColumnIdentifiers(tableHeader);
        selectedAddressants = new JTable(selectedAddressantsModel);
        selectedAddressants.setAutoCreateRowSorter(true);
        selectedAddressantsWrapper = new JPanel();
        selectedAddressantsWrapper.setBorder(new TitledBorder("Selected"));
        selectedAddressantsWrapper.setLayout(new GridLayout(1,1));
        selectedAddressantsWrapper.add(new JScrollPane(selectedAddressants));
        c.gridx = 2;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 0.45;
        group2.add(selectedAddressantsWrapper, c);

        sendEmailsButton = new JButton();
        sendEmailsButton.setText("Send");
        sendEmailsButton.addActionListener(e -> {
            java.util.List<String> selectedEmails = new ArrayList<>();
            for (int i = 0; i < selectedAddressants.getRowCount(); i++) {
                selectedEmails.add(selectedAddressantsModel.getValueAt(i, 0).toString());
            }
            if (!selectedEmails.isEmpty()) {
                EmailSettingsWindow emailSettingsWindow = new EmailSettingsWindow(selectedEmails);
                emailSettingsWindow.setVisible(true);
                this.setVisible(false);
            } else {
                JOptionPane.showMessageDialog (null, "The list of selected emails cannot be empty", "Empty Selected Emails List", JOptionPane.WARNING_MESSAGE);
            }
        });
        sendEmailsButtonWrapper = new JPanel();
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

        setContentPane(this.mainPanel);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // setResizable(false);
        setTitle("Select Recipients");
        setSize(1024, 300);

        BatchEmailSender.getLabelsList().thenApply(labels -> {
            labels.forEach(label -> availableFolders.addElement(label.toString()));
            return null;
        });
        try {
            availableFolders.setSelectedItem(BatchEmailSender.getSettings().get().getOrDefault("LRU Folder", "INBOX"));
            keywordsInput.setText(BatchEmailSender.getSettings().get().getOrDefault("Last Query", ""));
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }
}
