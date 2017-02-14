import javax.mail.MessagingException;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

class SendingEmailsWindow extends JFrame {
    SendingEmailsWindow(java.util.List<String> emails, String subject, String textFallBack, File htmlFileTemplatePath, File[] attachments) {
        int TOTAL_EMAILS = emails.size();
        JPanel mainPanel = new JPanel();
        mainPanel.setBorder(new EmptyBorder(10, 10, 10 ,10));
        mainPanel.setLayout(new GridLayout(2,0));
        JLabel sendingLabel = new JLabel("Sending");
        mainPanel.add(sendingLabel);
        JLabel progressLabel = new JLabel();
        int[] idx = { 0 };
        progressLabel.setText(idx[0] + " / " + TOTAL_EMAILS);
        mainPanel.add(progressLabel);
        setContentPane(mainPanel);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setTitle("Sending emails");
        setSize(300, 200);
        setResizable(false);

        class SendEmails extends SwingWorker<Boolean, Integer> {
            @Override
            protected Boolean doInBackground() throws Exception {
                emails.forEach(recipient -> {
                    try {
                        BatchEmailSender.sendMessage(BatchEmailSender.createEmailWithAttachment(recipient, subject, htmlFileTemplatePath, textFallBack, attachments));
                        idx[0]++;
                        publish(idx[0]);
                    } catch (MessagingException | IOException e) {
                        e.printStackTrace();
                    }
                });
                return true;
            }

            @Override
            protected void process(List<Integer> chunks) {
                progressLabel.setText(chunks.get(chunks.size() - 1) + " / " + TOTAL_EMAILS);
            }

            @Override
            protected void done() {
                boolean result = false;
                try {
                    result = get();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                System.out.println("Finished with status " + result);
                if (result) BatchEmailSender.updateLastSentEmailsDateInLocalDB(emails).thenRun(() -> System.exit(0));
            }
        }

        SendEmails sendEmails = new SendEmails();
        sendEmails.execute();
    }
}
