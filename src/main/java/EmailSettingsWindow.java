import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;

class EmailSettingsWindow extends JFrame {
    // [1] Subject
    private JTextArea               subject;

    // [2] Text Fallback
    private JTextArea               textFallback;

    // [3] HTML File Template Path
    private File                    htmlFileTemplatePath;
    private JLabel                  htmlFileTemplatePathLabel;

    // [4] Attachments
    private File[]                  attachments;
    private JTextArea               attachmentsLabel;

    EmailSettingsWindow(java.util.List<String> recipients) {
        JPanel mainPanel = new JPanel();
        mainPanel.setBorder(new EmptyBorder(10, 10, 10 ,10));
        mainPanel.setLayout(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();
        c.weightx = 1;
        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;

        // [1] Subject
        subject = new JTextArea();
        JPanel subjectWrapper = new JPanel();
        subjectWrapper.setLayout(new GridLayout(1,1));
        subjectWrapper.setBorder(new TitledBorder("Subject"));
        subjectWrapper.add(subject);
        c.gridy = 0;
        c.weighty = 0.05;
        mainPanel.add(subjectWrapper, c);

        // [2] Text Fallback
        textFallback = new JTextArea();
        JPanel textFallbackWrapper = new JPanel();
        textFallbackWrapper.setBorder(new TitledBorder("Text Fallback"));
        textFallbackWrapper.setLayout(new GridLayout(1,1));
        textFallbackWrapper.setBorder(new TitledBorder("Text Fallback"));
        textFallbackWrapper.add(new JScrollPane(textFallback));
        c.gridy = 1;
        c.weighty = 0.5;
        mainPanel.add(textFallbackWrapper, c);

        // [3] HTML File Template Path
        JPanel htmlFileTemplatePathWrapper = new JPanel();
        htmlFileTemplatePathWrapper.setLayout(new GridBagLayout());
        htmlFileTemplatePathWrapper.setBorder(new TitledBorder("HTML File Template Path"));
        GridBagConstraints c0 = new GridBagConstraints();
        c0.weighty = 1;
        c0.fill = GridBagConstraints.HORIZONTAL;
        c0.gridy = 0;

        JButton htmlFileTemplatePathOpenButton = new JButton();
        htmlFileTemplatePathOpenButton.setText("Open");
        c0.gridx = 0;
        c0.weightx = 0.2;
        htmlFileTemplatePathWrapper.add(htmlFileTemplatePathOpenButton, c0);

        htmlFileTemplatePathLabel = new JLabel();
        c0.gridx = 1;
        c0.weightx = 0.8;
        htmlFileTemplatePathWrapper.add(htmlFileTemplatePathLabel, c0);

        htmlFileTemplatePathOpenButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setMultiSelectionEnabled(false);
            chooser.showOpenDialog(new JFrame());
            htmlFileTemplatePathLabel.setText("");
            htmlFileTemplatePath = chooser.getSelectedFile();
            updateHTMLFilePathLabel();
        });

        c.gridy = 2;
        c.weighty = 0.05;
        mainPanel.add(htmlFileTemplatePathWrapper, c);

        // [4] Attachments
        JPanel attachmentsWrapper = new JPanel();
        attachmentsWrapper.setLayout(new GridBagLayout());
        attachmentsWrapper.setBorder(new TitledBorder("Attachments"));
        GridBagConstraints c1 = new GridBagConstraints();
        c1.weighty = 1;
        c1.fill = GridBagConstraints.HORIZONTAL;
        c1.anchor = GridBagConstraints.NORTHWEST;
        c1.gridy = 0;

        JButton attachmentsOpenButton = new JButton();
        attachmentsOpenButton.setText("Open");

        c1.gridx = 0;
        c1.weightx = 0.2;
        attachmentsWrapper.add(attachmentsOpenButton, c1);

        attachmentsLabel = new JTextArea();
        attachmentsLabel.setEditable(false);
        attachmentsLabel.setOpaque(false);
        attachmentsLabel.setHighlighter(null);
        c1.gridx = 1;
        c1.weightx = 0.8;
        attachmentsWrapper.add(attachmentsLabel, c1);

        attachmentsOpenButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.addChoosableFileFilter(new FileNameExtensionFilter("HTML Template", "html"));
            chooser.setMultiSelectionEnabled(true);
            chooser.showOpenDialog(new JFrame());
            attachmentsLabel.setText("");
            attachments = chooser.getSelectedFiles();
            updateAttachmentsLabel();
        });

        c.gridy = 3;
        c.weighty = 0.35;
        mainPanel.add(attachmentsWrapper, c);

        // [5] Send Button
        JButton sendButton = new JButton();
        sendButton.setText("Send");
        sendButton.addActionListener(e -> {
            BatchEmailSender.setSettings(null, null, subject.getText(), textFallback.getText(), (htmlFileTemplatePath == null ? "" : htmlFileTemplatePath.getAbsolutePath()), parseAttachmentsIntoString(attachments));
            SendingEmailsWindow sendingEmailsWindow = new SendingEmailsWindow(recipients, subject.getText(), textFallback.getText(), htmlFileTemplatePath, attachments);
            sendingEmailsWindow.setVisible(true);
            this.setVisible(false);
        });
        JPanel sendButtonWrapper = new JPanel();
        sendButtonWrapper.setLayout(new GridBagLayout());
        sendButtonWrapper.add(sendButton);

        c.gridy = 4;
        c.weighty = 0.05;
        mainPanel.add(sendButtonWrapper, c);


        setContentPane(mainPanel);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setTitle("Fill the letter");
        setSize(600, 600);

        BatchEmailSender.getSettings().thenApply(settings -> {
            // [1] Subject
            subject.setText(settings.getOrDefault("Subject", ""));

            // [2] Text Fallback
            textFallback.setText(settings.getOrDefault("Text Fallback", ""));

            // [3] HTML File Template Path
            File tmp = new File(settings.getOrDefault("HTML File Template Path", ""));
            htmlFileTemplatePath = tmp.exists() ? tmp : null;
            updateHTMLFilePathLabel();

            // [4] Attachments
            attachments = parseAttachmentsFromString(settings.getOrDefault("Attachments", ""));
            updateAttachmentsLabel();


            return true;
        });
    }

    private File[] parseAttachmentsFromString(String valueFromSettings) {
        if (valueFromSettings == null || valueFromSettings.isEmpty()) return new File[] {};
        else {
            java.util.List<String> entries = Arrays.asList(valueFromSettings.split("\\|"));
            entries.forEach(entry -> System.out.println("-> " + entry));
            return entries.stream().map(File::new).filter(File::exists).toArray(File[]::new);
        }
    }

    private String parseAttachmentsIntoString(File[] attachmentsList) {
        if (attachmentsList == null || attachmentsList.length == 0) return "";
        else {
            java.util.List<String> filePaths = Arrays.stream(attachmentsList).map(File::getAbsolutePath).collect(Collectors.toList());
            return String.join("|", filePaths);
        }
    }

    private void updateHTMLFilePathLabel() {
        if (htmlFileTemplatePath != null) htmlFileTemplatePathLabel.setText(htmlFileTemplatePath.getName());
        else htmlFileTemplatePathLabel.setText("");
    }

    private void updateAttachmentsLabel() {
        if (attachments != null) {
            Arrays.stream(attachments).forEach(file -> {
                if (attachmentsLabel.getText().isEmpty()) attachmentsLabel.setText(file.getName());
                else attachmentsLabel.setText(attachmentsLabel.getText() + "\n" + file.getName());
            });
        } else attachmentsLabel.setText("");
    }
}
