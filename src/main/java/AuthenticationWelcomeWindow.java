import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

/**
 * Created by rkhunter on 1/30/17.
 */
public class AuthenticationWelcomeWindow extends JFrame {
    private JButton authenticateButton;
    private JPanel mainPanel;
    private JTextPane label_information_about_authentication;

    public AuthenticationWelcomeWindow() {
        mainPanel = new JPanel();
        mainPanel.setBorder(new EmptyBorder(10, 10, 10 ,10));
        mainPanel.setLayout(new GridLayout(2,0));

        label_information_about_authentication = new JTextPane();
        label_information_about_authentication.setText("Please grant permissions in web browser if you run the application for the first time.\n\nPress the button to continue");
        label_information_about_authentication.setOpaque(false);
        label_information_about_authentication.setBorder(new EmptyBorder(0, 0, 10, 0));
        label_information_about_authentication.setEditable(false);
        mainPanel.add(label_information_about_authentication);

        authenticateButton = new JButton();
        authenticateButton.setText("Authenticate");
        authenticateButton.addActionListener(e -> {
                BatchEmailSender.setupAuth().thenRun(() -> {
                    this.setVisible(false);
                    SearchQueryWindow searchQueryWindow = new SearchQueryWindow();
                    searchQueryWindow.setVisible(true);
                });
        });
        mainPanel.add(authenticateButton);

        setContentPane(this.mainPanel);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setTitle("Authentication");
        pack();
    }
}

