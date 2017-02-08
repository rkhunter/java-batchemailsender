import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.ExecutionException;

/**
 * Created by rkhunter on 2/3/17.
 */
public class CampaignSelectOrCreateWindow extends JFrame {
    private JPanel mainPanel;
    private JComboBox availableCampaigns;
    ActionListener availableCampaignsCampaignSelected;
    private JButton renameCampaign;
    private JButton deleteCampaign;
    private JButton selectCampaign;

    public CampaignSelectOrCreateWindow() {
        mainPanel = new JPanel();
        mainPanel.setBorder(new EmptyBorder(10, 10, 10 ,10));
        mainPanel.setLayout(new GridLayout(4,0));

        availableCampaigns = new JComboBox();
        availableCampaigns.setBorder(new TitledBorder("Available Campaigns"));
        availableCampaignsCampaignSelected = (e -> {
                JComboBox comboBox = (JComboBox) e.getSource();

                Object selected = comboBox.getSelectedItem();

                if (selected.toString().equals("Create new")) {
                    String s = JOptionPane.showInputDialog(
                            new JFrame(),
                            "Input campaign name",
                            "Choose wisely. Name cannot be changed later",
                            JOptionPane.PLAIN_MESSAGE);

                    if ((s != null) && (s.length() > 0)) {
                        availableCampaigns.removeItemAt(0); // remove "Create new"
                        BatchEmailSender.createNewCampaign(s).thenRun(() -> refreshCampaignsList());
                    } else JOptionPane.showMessageDialog (null, "The campaign name cannot be empty", "Create new campaign failed", JOptionPane.WARNING_MESSAGE);
                }

                verifySelection();
        });

        refreshCampaignsList();

        mainPanel.add(availableCampaigns);

        renameCampaign = new JButton();
        renameCampaign.setText("Rename");
        renameCampaign.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // TODO: Renaming the campaign
            }
        });
        renameCampaign.setEnabled(false);
        mainPanel.add(renameCampaign);


        deleteCampaign = new JButton();
        deleteCampaign.setText("Delete");
        deleteCampaign.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                BatchEmailSender.deleteCampaign(availableCampaigns.getSelectedItem().toString());
                refreshCampaignsList();
                verifySelection();
            }
        });
        deleteCampaign.setEnabled(false);
        mainPanel.add(deleteCampaign);

        selectCampaign = new JButton();
        selectCampaign.setText("Select");
        selectCampaign.addActionListener(e -> {
            try {
                String campaignName = availableCampaigns.getSelectedItem().toString();
                if (BatchEmailSender.campaignExists(campaignName).get()) {
                    BatchEmailSender.setCampaign(campaignName);
                     AuthenticationWelcomeWindow authenticationWelcomeWindow = new AuthenticationWelcomeWindow();
                     authenticationWelcomeWindow.setVisible(true);
                     this.setVisible(false);
                }
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            } catch (ExecutionException e1) {
                e1.printStackTrace();
            }
        });
        selectCampaign.setEnabled(false);
        mainPanel.add(selectCampaign);





        setContentPane(this.mainPanel);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setTitle("Select campaign or create new");
        setSize(500, 400);
        setResizable(false);
    }

    private void refreshCampaignsList() {
        availableCampaigns.removeActionListener(availableCampaignsCampaignSelected);
        availableCampaigns.removeAllItems();
        BatchEmailSender.getCampaigns().thenApply(
                campaigns -> {
                    if (!campaigns.iterator().hasNext()) availableCampaigns.addItem("Create new");
                    else {
                        campaigns.forEach(campaign -> availableCampaigns.addItem(campaign.toString()));
                        availableCampaigns.addItem("Create new");
                    }

                    return null;
                }
        );
        availableCampaigns.addActionListener(availableCampaignsCampaignSelected);
    }

    private void verifySelection() {
        try {
            if (BatchEmailSender.campaignExists(availableCampaigns.getSelectedItem().toString()).get()) {
                selectCampaign.setEnabled(true);
                deleteCampaign.setEnabled(true);
            } else {
                selectCampaign.setEnabled(false);
                deleteCampaign.setEnabled(false);
            }
        } catch (InterruptedException e1) {
            e1.printStackTrace();
        } catch (ExecutionException e1) {
            e1.printStackTrace();
        }
    }
}
