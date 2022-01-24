package mintmeister;

import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URL;
import javax.swing.JOptionPane;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

public class GUI extends javax.swing.JFrame
{
    protected final BackgroundService backgroundService;
    protected final DatabaseManager dbManager;    
    private String currentCard = "mintingMonitor";   
    
    public GUI(BackgroundService bgs)
    {              
        SetLookAndFeel("Nimbus");//do this before initComponents()
        backgroundService = bgs;
        dbManager = bgs.dbManager;
        initComponents();
        nodeMonitorPanel.CreateMonitorTree();  
        InitFrame();    
        InitTaskbar();  
        System.gc();           
    }//end constructor
    
    private void InitFrame()
    {
        //put the frame at middle of the screen,add icon and set visible
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        URL imageURL = GUI.class.getClassLoader().getResource("Images/icon.png");
        Image icon = Toolkit.getDefaultToolkit().getImage(imageURL);
        setTitle(BackgroundService.BUILDVERSION);
        setIconImage(icon);        
        setLocation(dim.width / 2 - getSize().width / 2, dim.height / 2 - getSize().height / 2);
        setVisible(true);
        BackgroundService.SPLASH.setVisible(false);
        //calling this funtion after GUI is done intitialising, if an error occurs in minting monitor
        //init this will ensure that the splash screen is disabled and the GUI is enabled
        mintingMonitor.initialise(this);
    }
    
    private void InitTaskbar()
    {           
        for (LookAndFeelInfo LFI : UIManager.getInstalledLookAndFeels())
        {
            JRadioButtonMenuItem radioButtonMenuItem = new JRadioButtonMenuItem(LFI.getName());
            if(LFI.getName().equals("Nimbus"))
                radioButtonMenuItem.setSelected(true); 
                
            radioButtonMenuItem.addActionListener((ActionEvent e) ->
            {     
                appearanceMenu.setVisible(false);
                SetLookAndFeel(e.getActionCommand());
            });
            appearanceGroup.add(radioButtonMenuItem);
            appearanceMenu.add(radioButtonMenuItem);
        }
    }
     
     
    protected void ShowLoadScreen()
    {      
        //setting the label to visible will make the logo jump up. Label start text is 3 line breaks.
        statusLabel.setText(Utilities.AllignCenterHTML(Main.BUNDLE.getString("loginSuccess")));
        CardLayout card = (CardLayout) mainPanel.getLayout();
        card.show(mainPanel, "splashPanel");
    }
    
    
    
    protected void ExpandTree(JTree tree, int nodeLevel)
    {
        var currentNode = (DefaultMutableTreeNode) tree.getModel().getRoot();        
        
        do
        {    
            if (currentNode.getLevel() == nodeLevel) 
            {
                tree.expandPath(new TreePath(currentNode.getPath()));
            }
            
            currentNode = currentNode.getNextNode();
        } 
        while (currentNode != null);
    }
    
    protected void ExpandNode(JTree tree, DefaultMutableTreeNode currentNode,int nodeLevel)
    {        
        DefaultMutableTreeNode original = currentNode;
        do
        {
            if (currentNode.getLevel() == nodeLevel) 
                tree.expandPath(new TreePath(currentNode.getPath()));
            
            currentNode = currentNode.getNextNode().isNodeAncestor(original) ? currentNode.getNextNode() : null;            
        } 
        while (currentNode != null);
    }

    private void SetLookAndFeel(String styleString)
    {
        try
        {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels())
            {
                if (styleString.equals(info.getName()))
                {
                    //in case nimbus dark mode button text is not visible
//                    if(styleString.equals("Nimbus"))
//                        UIManager.getLookAndFeelDefaults().put("Button.textForeground", Color.BLACK);  
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    SwingUtilities.updateComponentTreeUI(this);
                    if(appearanceMenu != null)
                        SwingUtilities.updateComponentTreeUI(appearanceMenu);
                    break;
                }
            }
        }
        catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex)
        {
            java.util.logging.Logger.getLogger(GUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
    }
    
    protected void exitButtonActionPerformed(java.awt.event.ActionEvent evt)                                           
    {                                               
        if(BackgroundService.ISMAPPING)
        {
            if(JOptionPane.showConfirmDialog(
                    this,
                    Utilities.AllignCenterHTML(Main.BUNDLE.getString("exitConfirm")),
                    Main.BUNDLE.getString("exitConfirmTitle"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.PLAIN_MESSAGE) == JOptionPane.YES_OPTION)
            {         
                mintingMonitor.mappingHalted = true;
                mintingMonitor.exitInitiated = true;
            }
        }
        else
            System.exit(0);
    }
    
    private void pasteToLabel(String coin)
    {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Transferable t = clipboard.getContents(this);
        if (t == null)
            return;
        try
        {
            clipboardLabel.setText(coin + " address copied to clipboard: " + (String) t.getTransferData(DataFlavor.stringFlavor));
        }
        catch (UnsupportedFlavorException | IOException e)
        {
            BackgroundService.AppendLog(e);
        }
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

        appearanceGroup = new javax.swing.ButtonGroup();
        appearanceMenu = new javax.swing.JPopupMenu();
        trayPopup = new javax.swing.JDialog();
        popUpLabel = new javax.swing.JLabel();
        mainToolbar = new javax.swing.JToolBar();
        mintingMonitorButton = new javax.swing.JButton();
        nodeMonitorButton = new javax.swing.JButton();
        appearanceButton = new javax.swing.JButton();
        donateButton = new javax.swing.JButton();
        exitButton = new javax.swing.JButton();
        mainPanel = new javax.swing.JPanel();
        mintingMonitor = new mintmeister.MintingMonitor();
        splashPanel = new javax.swing.JPanel();
        statusLabel = new javax.swing.JLabel();
        nodeMonitorPanel = new mintmeister.MonitorPanel();
        nodeMonitorPanel.Initialise(this);
        tipJarScrollPane = new javax.swing.JScrollPane();
        tipJarScrollPane.getVerticalScrollBar().setUnitIncrement(10);
        tipJarPanel = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        btcField = new javax.swing.JTextField();
        jLabel3 = new javax.swing.JLabel();
        dogeField = new javax.swing.JTextField();
        jLabel4 = new javax.swing.JLabel();
        ltcField = new javax.swing.JTextField();
        jLabel5 = new javax.swing.JLabel();
        qortField = new javax.swing.JTextField();
        jSeparator1 = new javax.swing.JSeparator();
        jSeparator2 = new javax.swing.JSeparator();
        jSeparator3 = new javax.swing.JSeparator();
        clipboardLabel = new javax.swing.JLabel();

        appearanceMenu.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseExited(java.awt.event.MouseEvent evt)
            {
                appearanceMenuMouseExited(evt);
            }
        });

        trayPopup.setUndecorated(true);

        popUpLabel.setBackground(new java.awt.Color(204, 202, 202));
        popUpLabel.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        popUpLabel.setForeground(new java.awt.Color(0, 0, 0));
        popUpLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        popUpLabel.setText("<html><div style='text-align: center;'>Mapping session in progress<br/>MintMeister is running in the background<br/><br/> Double click on the system tray icon to open the UI<br/><br/> To exit the program, click 'Exit' in the menu bar<br/> You can also right click the system tray icon and click 'Exit'</div><html>");
        popUpLabel.setBorder(javax.swing.BorderFactory.createCompoundBorder(new javax.swing.border.LineBorder(new java.awt.Color(49, 0, 0), 4, true), new javax.swing.border.SoftBevelBorder(javax.swing.border.BevelBorder.LOWERED, new java.awt.Color(54, 56, 72), new java.awt.Color(84, 55, 55), new java.awt.Color(58, 77, 96), new java.awt.Color(72, 50, 50))));
        popUpLabel.setOpaque(true);
        popUpLabel.setPreferredSize(new java.awt.Dimension(380, 120));
        popUpLabel.addMouseListener(new java.awt.event.MouseAdapter()
        {
            public void mouseClicked(java.awt.event.MouseEvent evt)
            {
                popUpLabelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout trayPopupLayout = new javax.swing.GroupLayout(trayPopup.getContentPane());
        trayPopup.getContentPane().setLayout(trayPopupLayout);
        trayPopupLayout.setHorizontalGroup(
            trayPopupLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(popUpLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 417, Short.MAX_VALUE)
        );
        trayPopupLayout.setVerticalGroup(
            trayPopupLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(popUpLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 177, Short.MAX_VALUE)
        );

        setMinimumSize(new java.awt.Dimension(500, 600));
        setPreferredSize(new java.awt.Dimension(900, 650));
        addWindowListener(new java.awt.event.WindowAdapter()
        {
            public void windowClosing(java.awt.event.WindowEvent evt)
            {
                windowHandler(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        mainToolbar.setFloatable(false);
        mainToolbar.setRollover(true);

        mintingMonitorButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/minting_monitor.png"))); // NOI18N
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n/Language"); // NOI18N
        mintingMonitorButton.setText(bundle.getString("alertsButton")); // NOI18N
        mintingMonitorButton.setFocusable(false);
        mintingMonitorButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        mintingMonitorButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        mintingMonitorButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                mintingMonitorButtonActionPerformed(evt);
            }
        });
        mainToolbar.add(mintingMonitorButton);

        nodeMonitorButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/monitor.png"))); // NOI18N
        nodeMonitorButton.setText(bundle.getString("nodeMonitorButton")); // NOI18N
        nodeMonitorButton.setToolTipText("Current info on you node's status");
        nodeMonitorButton.setFocusable(false);
        nodeMonitorButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        nodeMonitorButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        nodeMonitorButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                nodeMonitorButtonActionPerformed(evt);
            }
        });
        mainToolbar.add(nodeMonitorButton);

        appearanceButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/Appearance.png"))); // NOI18N
        appearanceButton.setText(bundle.getString("appearanceButton")); // NOI18N
        appearanceButton.setFocusable(false);
        appearanceButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        appearanceButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        appearanceButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                appearanceButtonActionPerformed(evt);
            }
        });
        mainToolbar.add(appearanceButton);

        donateButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/donate.png"))); // NOI18N
        donateButton.setText(bundle.getString("donateButton")); // NOI18N
        donateButton.setFocusable(false);
        donateButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        donateButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        donateButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                donateButtonActionPerformed(evt);
            }
        });
        mainToolbar.add(donateButton);

        exitButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/exit.png"))); // NOI18N
        exitButton.setText(bundle.getString("exitButton")); // NOI18N
        exitButton.setFocusable(false);
        exitButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        exitButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);

        exitButton.addActionListener(new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent evt)
                {
                    exitButtonActionPerformed(evt);
                }
            });
            mainToolbar.add(exitButton);

            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 0;
            gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
            gridBagConstraints.ipady = 11;
            gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints.weightx = 1.0;
            getContentPane().add(mainToolbar, gridBagConstraints);

            mainPanel.setLayout(new java.awt.CardLayout());
            mainPanel.add(mintingMonitor, "mintingMonitor");

            splashPanel.setBackground(new java.awt.Color(51, 51, 51));
            splashPanel.setLayout(new java.awt.GridBagLayout());

            statusLabel.setFont(new java.awt.Font("Segoe UI", 1, 18)); // NOI18N
            statusLabel.setForeground(new java.awt.Color(166, 166, 166));
            statusLabel.setText("<html><div style='text-align: center;'<br/><br/><br/></div><html>");
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 1;
            splashPanel.add(statusLabel, gridBagConstraints);

            mainPanel.add(splashPanel, "splashPanel");
            mainPanel.add(nodeMonitorPanel, "monitorPanel");

            tipJarPanel.setLayout(new java.awt.GridBagLayout());

            jLabel1.setFont(new java.awt.Font("Bahnschrift", 1, 18)); // NOI18N
            jLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
            jLabel1.setText("Leave a tip for the developer");
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 0;
            gridBagConstraints.insets = new java.awt.Insets(0, 0, 20, 0);
            tipJarPanel.add(jLabel1, gridBagConstraints);

            jLabel2.setFont(new java.awt.Font("Bahnschrift", 0, 14)); // NOI18N
            jLabel2.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
            jLabel2.setText("Bitcoin");
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 14;
            tipJarPanel.add(jLabel2, gridBagConstraints);

            btcField.setEditable(false);
            btcField.setFont(new java.awt.Font("Bahnschrift", 0, 14)); // NOI18N
            btcField.setHorizontalAlignment(javax.swing.JTextField.CENTER);
            btcField.setText("1MCFEd5dpGLRqJxGhqLTMA2vf9GuvrpT7P");
            btcField.addMouseListener(new java.awt.event.MouseAdapter()
            {
                public void mouseReleased(java.awt.event.MouseEvent evt)
                {
                    btcFieldMouseReleased(evt);
                }
            });
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 15;
            gridBagConstraints.ipadx = 150;
            gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
            tipJarPanel.add(btcField, gridBagConstraints);

            jLabel3.setFont(new java.awt.Font("Bahnschrift", 0, 14)); // NOI18N
            jLabel3.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
            jLabel3.setText("Dogecoin");
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 10;
            tipJarPanel.add(jLabel3, gridBagConstraints);

            dogeField.setEditable(false);
            dogeField.setFont(new java.awt.Font("Bahnschrift", 0, 14)); // NOI18N
            dogeField.setHorizontalAlignment(javax.swing.JTextField.CENTER);
            dogeField.setText("DSbtx8q9hNzgzKUEKvrXRXtY8udjCnki1T");
            dogeField.addMouseListener(new java.awt.event.MouseAdapter()
            {
                public void mouseReleased(java.awt.event.MouseEvent evt)
                {
                    dogeFieldMouseReleased(evt);
                }
            });
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 11;
            gridBagConstraints.ipadx = 150;
            gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
            tipJarPanel.add(dogeField, gridBagConstraints);

            jLabel4.setFont(new java.awt.Font("Bahnschrift", 0, 14)); // NOI18N
            jLabel4.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
            jLabel4.setText("Litecoin");
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 6;
            tipJarPanel.add(jLabel4, gridBagConstraints);

            ltcField.setEditable(false);
            ltcField.setFont(new java.awt.Font("Bahnschrift", 0, 14)); // NOI18N
            ltcField.setHorizontalAlignment(javax.swing.JTextField.CENTER);
            ltcField.setText("LU5mPptunXYmqx9iDwsEeSFAfJZ9RFafyF");
            ltcField.addMouseListener(new java.awt.event.MouseAdapter()
            {
                public void mouseReleased(java.awt.event.MouseEvent evt)
                {
                    ltcFieldMouseReleased(evt);
                }
            });
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 7;
            gridBagConstraints.ipadx = 150;
            gridBagConstraints.insets = new java.awt.Insets(11, 0, 11, 0);
            tipJarPanel.add(ltcField, gridBagConstraints);

            jLabel5.setFont(new java.awt.Font("Bahnschrift", 0, 14)); // NOI18N
            jLabel5.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
            jLabel5.setText("QORT");
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 2;
            tipJarPanel.add(jLabel5, gridBagConstraints);

            qortField.setEditable(false);
            qortField.setFont(new java.awt.Font("Bahnschrift", 0, 14)); // NOI18N
            qortField.setHorizontalAlignment(javax.swing.JTextField.CENTER);
            qortField.setText("Qa672e3FktYjMRRiSZP2sJ8fyqHt3kx68Y");
            qortField.addMouseListener(new java.awt.event.MouseAdapter()
            {
                public void mouseReleased(java.awt.event.MouseEvent evt)
                {
                    qortFieldMouseReleased(evt);
                }
            });
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 3;
            gridBagConstraints.ipadx = 150;
            gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
            tipJarPanel.add(qortField, gridBagConstraints);
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 5;
            gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
            gridBagConstraints.insets = new java.awt.Insets(11, 0, 10, 0);
            tipJarPanel.add(jSeparator1, gridBagConstraints);
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 9;
            gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
            gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
            tipJarPanel.add(jSeparator2, gridBagConstraints);
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 13;
            gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
            gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
            tipJarPanel.add(jSeparator3, gridBagConstraints);

            clipboardLabel.setFont(new java.awt.Font("Bahnschrift", 0, 14)); // NOI18N
            clipboardLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
            clipboardLabel.setText("Click on an address to copy it to your clipboard");
            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 1;
            gridBagConstraints.insets = new java.awt.Insets(0, 0, 20, 0);
            tipJarPanel.add(clipboardLabel, gridBagConstraints);

            tipJarScrollPane.setViewportView(tipJarPanel);

            mainPanel.add(tipJarScrollPane, "tipJarPanel");

            gridBagConstraints = new java.awt.GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 1;
            gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
            gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints.weightx = 1.0;
            gridBagConstraints.weighty = 1.0;
            getContentPane().add(mainPanel, gridBagConstraints);

            pack();
            setLocationRelativeTo(null);
        }// </editor-fold>//GEN-END:initComponents

    private void nodeMonitorButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_nodeMonitorButtonActionPerformed
    {//GEN-HEADEREND:event_nodeMonitorButtonActionPerformed
        nodeMonitorPanel.isSynced = true; //first ping this flag must be true to activate time approximation
        CardLayout card = (CardLayout) mainPanel.getLayout();
        //We only need to run the GUI timer if monitorPanel is selected/in focus
        if (!currentCard.equals("monitorPanel"))
            nodeMonitorPanel.RestartTimer();
        
        currentCard = "monitorPanel";
        card.show(mainPanel, currentCard);
        if(nodeMonitorPanel.startTime == 0)
            nodeMonitorPanel.startTime = System.currentTimeMillis();
        
        clipboardLabel.setText("Click on an address to copy it to your clipboard");
        
        //on slow systems, the dialog may not have been triggered to invisible on leaving the chart
        mintingMonitor.chartMaker.chartDialog.setVisible(false);
    }//GEN-LAST:event_nodeMonitorButtonActionPerformed

    private void windowHandler(java.awt.event.WindowEvent evt)//GEN-FIRST:event_windowHandler
    {//GEN-HEADEREND:event_windowHandler
        backgroundService.SetGUIEnabled(false);
    }//GEN-LAST:event_windowHandler

    private void appearanceMenuMouseExited(java.awt.event.MouseEvent evt)//GEN-FIRST:event_appearanceMenuMouseExited
    {//GEN-HEADEREND:event_appearanceMenuMouseExited
        appearanceMenu.setVisible(false);
    }//GEN-LAST:event_appearanceMenuMouseExited

    private void appearanceButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_appearanceButtonActionPerformed
    {//GEN-HEADEREND:event_appearanceButtonActionPerformed
        //Menu bar did can not listen for mouse click on menu, only for menu items.This is a problem for the other buttons.
        //Using a custom pop up menu for setting look and feel. Tried many listeners (focus, mouseEntered and Exited etc.) show() works best
        //Using setVisible creates problems getting rid of the popup. Using the buttons location in show() would place the menu with an offset
        appearanceMenu.setLocation(appearanceButton.getLocationOnScreen().x,appearanceButton.getLocationOnScreen().y);
        appearanceMenu.show(appearanceButton, appearanceMenu.getX(),appearanceMenu.getY());
                
        //on slow systems, the dialog may not have been triggered to invisible on leaving the chart
        mintingMonitor.chartMaker.chartDialog.setVisible(false);
    }//GEN-LAST:event_appearanceButtonActionPerformed

    private void mintingMonitorButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_mintingMonitorButtonActionPerformed
    {//GEN-HEADEREND:event_mintingMonitorButtonActionPerformed
    
        CardLayout card = (CardLayout) mainPanel.getLayout();
        currentCard = "mintingMonitor";
        card.show(mainPanel, currentCard);
        if (nodeMonitorPanel.timer != null)
            nodeMonitorPanel.timer.cancel();  
        
        clipboardLabel.setText("Click on an address to copy it to your clipboard");         
        //on slow systems, the dialog may not have been triggered to invisible on leaving the chart
        mintingMonitor.chartMaker.chartDialog.setVisible(false);
    }//GEN-LAST:event_mintingMonitorButtonActionPerformed

    private void donateButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_donateButtonActionPerformed
    {//GEN-HEADEREND:event_donateButtonActionPerformed
         CardLayout card = (CardLayout) mainPanel.getLayout();
        currentCard = "tipJarPanel";
        card.show(mainPanel, currentCard);
        if (nodeMonitorPanel.timer != null)
            nodeMonitorPanel.timer.cancel();     
        
        //on slow systems, the dialog may not have been triggered to invisible on leaving the chart
        mintingMonitor.chartMaker.chartDialog.setVisible(false);
    }//GEN-LAST:event_donateButtonActionPerformed

    private void popUpLabelMouseClicked(java.awt.event.MouseEvent evt)//GEN-FIRST:event_popUpLabelMouseClicked
    {//GEN-HEADEREND:event_popUpLabelMouseClicked
        backgroundService.SetGUIEnabled(true);
    }//GEN-LAST:event_popUpLabelMouseClicked

    private void qortFieldMouseReleased(java.awt.event.MouseEvent evt)//GEN-FIRST:event_qortFieldMouseReleased
    {//GEN-HEADEREND:event_qortFieldMouseReleased
         Utilities.copyToClipboard(qortField.getText());
        pasteToLabel("QORT");
    }//GEN-LAST:event_qortFieldMouseReleased

    private void ltcFieldMouseReleased(java.awt.event.MouseEvent evt)//GEN-FIRST:event_ltcFieldMouseReleased
    {//GEN-HEADEREND:event_ltcFieldMouseReleased
        Utilities.copyToClipboard(ltcField.getText());
        pasteToLabel("Litecoin");
    }//GEN-LAST:event_ltcFieldMouseReleased

    private void dogeFieldMouseReleased(java.awt.event.MouseEvent evt)//GEN-FIRST:event_dogeFieldMouseReleased
    {//GEN-HEADEREND:event_dogeFieldMouseReleased
        Utilities.copyToClipboard(dogeField.getText());
        pasteToLabel("Dogecoin");
    }//GEN-LAST:event_dogeFieldMouseReleased

    private void btcFieldMouseReleased(java.awt.event.MouseEvent evt)//GEN-FIRST:event_btcFieldMouseReleased
    {//GEN-HEADEREND:event_btcFieldMouseReleased
        Utilities.copyToClipboard(btcField.getText());
        pasteToLabel("Bitcoin");
    }//GEN-LAST:event_btcFieldMouseReleased


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton appearanceButton;
    private javax.swing.ButtonGroup appearanceGroup;
    private javax.swing.JPopupMenu appearanceMenu;
    private javax.swing.JTextField btcField;
    private javax.swing.JLabel clipboardLabel;
    private javax.swing.JTextField dogeField;
    private javax.swing.JButton donateButton;
    private javax.swing.JButton exitButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JTextField ltcField;
    protected javax.swing.JPanel mainPanel;
    protected javax.swing.JToolBar mainToolbar;
    private mintmeister.MintingMonitor mintingMonitor;
    private javax.swing.JButton mintingMonitorButton;
    private javax.swing.JButton nodeMonitorButton;
    protected mintmeister.MonitorPanel nodeMonitorPanel;
    private javax.swing.JLabel popUpLabel;
    private javax.swing.JTextField qortField;
    private javax.swing.JPanel splashPanel;
    protected javax.swing.JLabel statusLabel;
    private javax.swing.JPanel tipJarPanel;
    private javax.swing.JScrollPane tipJarScrollPane;
    public javax.swing.JDialog trayPopup;
    // End of variables declaration//GEN-END:variables

        
}//end class GUI




