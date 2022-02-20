package mintmeister;

import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.io.File;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;

public class QortfolioPanel extends javax.swing.JPanel
{
    public QortfolioPanel()
    {
        initComponents();
        initAccounts();
    }
    
    private void initAccounts()
    {
        File accountsDir = new File(System.getProperty("user.dir") + "/accounts");
        if(!accountsDir.isDirectory())
            accountsDir.mkdir();
        
        File[] listOfFiles = accountsDir.listFiles();
        for(File file : listOfFiles)
        {
            if(file.getName().endsWith("mv.db"))
                accountsDropBox.addItem(file.getName().split("\\.",2)[0]);
        }
    }
    
    protected void CheckForCapsLock()
    {
        capsLockLabel.setVisible(Toolkit.getDefaultToolkit().getLockingKeyState(KeyEvent.VK_CAPS_LOCK));
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {
        java.awt.GridBagConstraints gridBagConstraints;

        passwordTipDialog = new javax.swing.JDialog();
        jLabel4 = new javax.swing.JLabel();
        loginScrollpane = new javax.swing.JScrollPane();
        loginScrollpane.getVerticalScrollBar().setUnitIncrement(10);
        loginPanel = new javax.swing.JPanel();
        nameInputField = new javax.swing.JTextField();
        loginButton = new javax.swing.JButton();
        createAccountButton = new javax.swing.JButton();
        jSeparator1 = new javax.swing.JSeparator();
        accountsDropBox = new javax.swing.JComboBox<>();
        ((JLabel)accountsDropBox.getRenderer()).setHorizontalAlignment(SwingConstants.CENTER);
        passwordField2 = new javax.swing.JPasswordField();
        passwordField1 = new javax.swing.JPasswordField();
        jPasswordField3 = new javax.swing.JPasswordField();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        passwordTipLabel = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        capsLockLabel = new javax.swing.JLabel();
        capsLockLabel.setVisible(false);
        passwordStatusLabel = new javax.swing.JLabel();
        qortfolioPanel = new javax.swing.JPanel();

        passwordTipDialog.setMinimumSize(new java.awt.Dimension(350, 200));
        passwordTipDialog.setPreferredSize(new java.awt.Dimension(350, 200));
        passwordTipDialog.setResizable(false);
        passwordTipDialog.setType(java.awt.Window.Type.POPUP);

        jLabel4.setFont(new java.awt.Font("Corbel", 1, 14)); // NOI18N
        jLabel4.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel4.setText("<html><div style='text-align: center;'>If you prefer to keep the addresses that<br/>you're tracking private, locking your<br/>account with a password is recommended</div><html>");
        passwordTipDialog.getContentPane().add(jLabel4, java.awt.BorderLayout.CENTER);

        setLayout(new java.awt.CardLayout());

        java.awt.GridBagLayout loginPanelLayout = new java.awt.GridBagLayout();
        loginPanelLayout.columnWidths = new int[] {0, 17, 0, 17, 0, 17, 0, 17, 0, 17, 0, 17, 0};
        loginPanelLayout.rowHeights = new int[] {0, 12, 0, 12, 0, 12, 0, 12, 0, 12, 0, 12, 0, 12, 0, 12, 0, 12, 0, 12, 0, 12, 0, 12, 0};
        loginPanel.setLayout(loginPanelLayout);

        nameInputField.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        nameInputField.setMinimumSize(new java.awt.Dimension(250, 30));
        nameInputField.setPreferredSize(new java.awt.Dimension(250, 30));
        nameInputField.addFocusListener(new java.awt.event.FocusAdapter()
        {
            public void focusGained(java.awt.event.FocusEvent evt)
            {
                nameInputFieldFocusGained(evt);
            }
        });
        nameInputField.addKeyListener(new java.awt.event.KeyAdapter()
        {
            public void keyReleased(java.awt.event.KeyEvent evt)
            {
                nameInputFieldKeyReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        loginPanel.add(nameInputField, gridBagConstraints);

        loginButton.setText("Login");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        loginPanel.add(loginButton, gridBagConstraints);

        createAccountButton.setText("Create account");
        createAccountButton.setEnabled(false);
        createAccountButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                createAccountButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 24;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 10, 0);
        loginPanel.add(createAccountButton, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        loginPanel.add(jSeparator1, gridBagConstraints);

        accountsDropBox.setMinimumSize(new java.awt.Dimension(250, 30));
        accountsDropBox.setPreferredSize(new java.awt.Dimension(250, 30));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        loginPanel.add(accountsDropBox, gridBagConstraints);

        passwordField2.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        passwordField2.setMinimumSize(new java.awt.Dimension(250, 30));
        passwordField2.setPreferredSize(new java.awt.Dimension(250, 30));
        passwordField2.addFocusListener(new java.awt.event.FocusAdapter()
        {
            public void focusGained(java.awt.event.FocusEvent evt)
            {
                passwordField2FocusGained(evt);
            }
        });
        passwordField2.addKeyListener(new java.awt.event.KeyAdapter()
        {
            public void keyReleased(java.awt.event.KeyEvent evt)
            {
                passwordField1KeyReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 16;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        loginPanel.add(passwordField2, gridBagConstraints);

        passwordField1.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        passwordField1.setMinimumSize(new java.awt.Dimension(250, 30));
        passwordField1.setPreferredSize(new java.awt.Dimension(250, 30));
        passwordField1.addFocusListener(new java.awt.event.FocusAdapter()
        {
            public void focusGained(java.awt.event.FocusEvent evt)
            {
                passwordField1FocusGained(evt);
            }
        });
        passwordField1.addKeyListener(new java.awt.event.KeyAdapter()
        {
            public void keyReleased(java.awt.event.KeyEvent evt)
            {
                passwordField1KeyReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 14;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        loginPanel.add(passwordField1, gridBagConstraints);

        jPasswordField3.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        jPasswordField3.setMinimumSize(new java.awt.Dimension(250, 30));
        jPasswordField3.setPreferredSize(new java.awt.Dimension(250, 30));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        loginPanel.add(jPasswordField3, gridBagConstraints);

        jLabel1.setText("Enter password:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        loginPanel.add(jLabel1, gridBagConstraints);

        jLabel2.setText("User name:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        loginPanel.add(jLabel2, gridBagConstraints);

        jLabel3.setText("Password:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 14;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        loginPanel.add(jLabel3, gridBagConstraints);

        passwordTipLabel.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        passwordTipLabel.setForeground(new java.awt.Color(0, 153, 255));
        passwordTipLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        passwordTipLabel.setText("Should I use a password?");
        passwordTipLabel.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseClicked(java.awt.event.MouseEvent evt)
            {
                passwordTipLabelMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 22;
        loginPanel.add(passwordTipLabel, gridBagConstraints);

        jLabel5.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
        jLabel5.setText("Existing account");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        loginPanel.add(jLabel5, gridBagConstraints);

        jLabel6.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
        jLabel6.setText("New account");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        loginPanel.add(jLabel6, gridBagConstraints);

        jLabel7.setText("Login as:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        loginPanel.add(jLabel7, gridBagConstraints);

        jLabel8.setText("Confirm password:");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 16;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        loginPanel.add(jLabel8, gridBagConstraints);

        capsLockLabel.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        capsLockLabel.setForeground(new java.awt.Color(161, 0, 40));
        capsLockLabel.setText("CAPSLOCK IS ON");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 20;
        loginPanel.add(capsLockLabel, gridBagConstraints);

        passwordStatusLabel.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        passwordStatusLabel.setText("Waiting for input...");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 18;
        loginPanel.add(passwordStatusLabel, gridBagConstraints);

        loginScrollpane.setViewportView(loginPanel);

        add(loginScrollpane, "loginPanel");

        javax.swing.GroupLayout qortfolioPanelLayout = new javax.swing.GroupLayout(qortfolioPanel);
        qortfolioPanel.setLayout(qortfolioPanelLayout);
        qortfolioPanelLayout.setHorizontalGroup(
            qortfolioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 563, Short.MAX_VALUE)
        );
        qortfolioPanelLayout.setVerticalGroup(
            qortfolioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 520, Short.MAX_VALUE)
        );

        add(qortfolioPanel, "qortfolioPanel");
    }// </editor-fold>//GEN-END:initComponents

    private void passwordTipLabelMouseClicked(java.awt.event.MouseEvent evt)//GEN-FIRST:event_passwordTipLabelMouseClicked
    {//GEN-HEADEREND:event_passwordTipLabelMouseClicked
        passwordTipDialog.setModal(true);
        int x = (int)evt.getLocationOnScreen().getX();
        int y = (int)evt.getLocationOnScreen().getY();
        passwordTipDialog.setLocation(x - (passwordTipDialog.getWidth()/ 2), y - (passwordTipDialog.getHeight() + 15));
        passwordTipDialog.pack();
        passwordTipDialog.setVisible(true);
    }//GEN-LAST:event_passwordTipLabelMouseClicked

    private void passwordField1KeyReleased(java.awt.event.KeyEvent evt)//GEN-FIRST:event_passwordField1KeyReleased
    {//GEN-HEADEREND:event_passwordField1KeyReleased
        CheckForCapsLock();
        
        if(passwordField1.getPassword().length == 0 && passwordField2.getPassword().length == 0)
        {
            passwordStatusLabel.setText("Waiting for input (leave blank for no password)");
            return;
        }

        if(String.copyValueOf(passwordField1.getPassword()).equals(String.copyValueOf(passwordField2.getPassword())))
        {
            createAccountButton.setEnabled(true);
            passwordStatusLabel.setText("Passwords are identical");
            if(evt.getKeyCode() == KeyEvent.VK_ENTER)
                createAccountButtonActionPerformed(null);
        }
        else
        {
            createAccountButton.setEnabled(false);
            passwordStatusLabel.setText("Passwords are NOT identical");
        }
    }//GEN-LAST:event_passwordField1KeyReleased

    private void createAccountButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_createAccountButtonActionPerformed
    {//GEN-HEADEREND:event_createAccountButtonActionPerformed
        File accountsDir = new File(System.getProperty("user.dir") + "/accounts");
        if(!accountsDir.isDirectory())
            accountsDir.mkdir();
        
        File[] listOfFiles = accountsDir.listFiles();
        for(File file : listOfFiles)
        {
            if(file.getName().endsWith("mv.db"))
            {
                if(file.getName().split("\\.",2)[0].equalsIgnoreCase(nameInputField.getText()))
                {
                    JOptionPane.showMessageDialog(this, "Account " + nameInputField.getText() + " already exists");
                    return;
                }
            }
        }
    }//GEN-LAST:event_createAccountButtonActionPerformed

    private void nameInputFieldKeyReleased(java.awt.event.KeyEvent evt)//GEN-FIRST:event_nameInputFieldKeyReleased
    {//GEN-HEADEREND:event_nameInputFieldKeyReleased
        createAccountButton.setEnabled(!nameInputField.getText().isBlank());
    }//GEN-LAST:event_nameInputFieldKeyReleased

    private void nameInputFieldFocusGained(java.awt.event.FocusEvent evt)//GEN-FIRST:event_nameInputFieldFocusGained
    {//GEN-HEADEREND:event_nameInputFieldFocusGained
        passwordStatusLabel.setText("Enter a name for this account");
    }//GEN-LAST:event_nameInputFieldFocusGained

    private void passwordField1FocusGained(java.awt.event.FocusEvent evt)//GEN-FIRST:event_passwordField1FocusGained
    {//GEN-HEADEREND:event_passwordField1FocusGained
        passwordStatusLabel.setText("Enter a password, leave blank for no password");
    }//GEN-LAST:event_passwordField1FocusGained

    private void passwordField2FocusGained(java.awt.event.FocusEvent evt)//GEN-FIRST:event_passwordField2FocusGained
    {//GEN-HEADEREND:event_passwordField2FocusGained
        passwordStatusLabel.setText("Confirm password, leave blank for no password");
    }//GEN-LAST:event_passwordField2FocusGained


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JComboBox<String> accountsDropBox;
    protected javax.swing.JLabel capsLockLabel;
    private javax.swing.JButton createAccountButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JPasswordField jPasswordField3;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JButton loginButton;
    private javax.swing.JPanel loginPanel;
    private javax.swing.JScrollPane loginScrollpane;
    private javax.swing.JTextField nameInputField;
    private javax.swing.JPasswordField passwordField1;
    private javax.swing.JPasswordField passwordField2;
    protected javax.swing.JLabel passwordStatusLabel;
    private javax.swing.JDialog passwordTipDialog;
    private javax.swing.JLabel passwordTipLabel;
    private javax.swing.JPanel qortfolioPanel;
    // End of variables declaration//GEN-END:variables
}
