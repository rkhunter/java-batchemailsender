import com.sun.xml.internal.org.jvnet.mimepull.MIMEMessage;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;

/**
 * Created by rkhunter on 2/6/17.
 */
public class SendingEmailsWindow extends JFrame {
    private JPanel  mainPanel;
    private JLabel  sendingLabel;
    private JLabel  progressLabel;
    int             TOTAL_EMAILS;
    // Populate with more arguments
    SendingEmailsWindow(java.util.List<String> emails, String subject, String textFallBack, File htmlFileTemplatePath, File[] attachments) {
        TOTAL_EMAILS = emails.size();
        mainPanel = new JPanel();
        mainPanel.setBorder(new EmptyBorder(10, 10, 10 ,10));
        mainPanel.setLayout(new GridLayout(2,0));
        sendingLabel = new JLabel("Sending");
        mainPanel.add(sendingLabel);
        progressLabel = new JLabel();
        progressLabel.setText("XX / " + TOTAL_EMAILS);
        mainPanel.add(progressLabel);
        setContentPane(this.mainPanel);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setTitle("Sending emails");
        setSize(300, 200);
        setResizable(false);

        System.out.println(" >>>>> FINISHED");
        // BatchEmailSender.updateLastSentEmailsDateInLocalDB(emails);
    }
}
