import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

class AuthenticationWelcomeWindow extends JFrame {
    AuthenticationWelcomeWindow() {
        JPanel mainPanel = new JPanel();
        mainPanel.setBorder(new EmptyBorder(10, 10, 10 ,10));
        mainPanel.setLayout(new GridLayout(2,0));

        JTextPane label_information_about_authentication = new JTextPane();
        label_information_about_authentication.setText("Please grant permissions in web browser if you run the application for the first time.\n\nPress the button to continue");
        label_information_about_authentication.setOpaque(false);
        label_information_about_authentication.setBorder(new EmptyBorder(0, 0, 10, 0));
        label_information_about_authentication.setEditable(false);
        mainPanel.add(label_information_about_authentication);

        JButton authenticateButton = new JButton();
        authenticateButton.setText("Authenticate");
        authenticateButton.addActionListener(e -> BatchEmailSender.setupAuth().thenRun(() -> {
            this.setVisible(false);
            SearchQueryWindow searchQueryWindow = new SearchQueryWindow();
            searchQueryWindow.setVisible(true);
        }));
        mainPanel.add(authenticateButton);

        setContentPane(mainPanel);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setResizable(false);
        setTitle("Authentication");
        pack();
    }
}

