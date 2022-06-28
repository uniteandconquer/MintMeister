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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import javax.swing.JOptionPane;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.json.JSONException;
import org.json.JSONObject;

public class GUI extends javax.swing.JFrame
{
    protected final BackgroundService backgroundService;
    protected final DatabaseManager dbManager;    
    private String currentCard = "mintingMonitor";   
    private boolean sponsorDialogShown;
    
    public GUI(BackgroundService bgs)
    {              
        SetLookAndFeel("Nimbus");//do this before initComponents()
        backgroundService = bgs;
        dbManager = bgs.dbManager;
        initComponents();
        initMintingMonitor();
        nodeMonitorPanel.CreateMonitorTree();
        initFrame();    
        InitTaskbar();  
        checkLoginCount();
        mintingMonitor.checkForAutoStart();
        System.gc();           
    }//end constructor
    
    /**To avoid splash frozen splash screen in case minting monitor doesn't initialize properly*/
    private void initMintingMonitor()
    {          
        if(!mintingMonitor.initialise(this))
        {
            JOptionPane.showMessageDialog(this, "Error initializing minting monitor");
            System.exit(1);
        }
    }
    
    private void initFrame()
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
    
    private void checkLoginCount()
    {
        File settingsFile = new File(System.getProperty("user.dir") + "/bin/settings.json");
        if(settingsFile.exists())
        {
            try
            {
                String jsonString = Files.readString(settingsFile.toPath());
                if(jsonString != null)
                {
                    int newLoginCount;
                    
                    JSONObject jsonObject = new JSONObject(jsonString);
                    
                    String dismissed = jsonObject.optString("donateDismissed");
                    if(dismissed.isBlank())
                        jsonObject.put("donateDismissed", "false");  
                    
                    String loginCount = jsonObject.optString("loginCount");
                    if(loginCount.isBlank())
                    {
                        jsonObject.put("loginCount", "1");
                        newLoginCount = 1;
                    }
                    else
                    {
                        newLoginCount = 1 + Integer.parseInt(loginCount);
                        jsonObject.put("loginCount", String.valueOf(newLoginCount));                      
                    }   
                    
                    //MUST write to json before opening (modal) dialog, otherwise it will overwrite
                    //the user's dismiss donate pref after clicking dismissButton
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(settingsFile)))
                    {
                        writer.write(jsonObject.toString(1));
                        writer.close();
                    }                          
                    if(dismissed.equals("false") && newLoginCount % 20 == 0)
                    {
                        donateDialog.pack();
                        int x = getX() + ((getWidth() / 2) - (donateDialog.getWidth() / 2));
                        int y = getY() + ((getHeight() / 2) - (donateDialog.getHeight() / 2));
                        donateDialog.setLocation(x, y);
                        donateDialog.setVisible(true);   
                    }  
                }                
            }
            catch (IOException | JSONException e)
            {
                BackgroundService.AppendLog(e);
            }
        }
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
        donateDialog = new javax.swing.JDialog();
        donatePanel = new javax.swing.JPanel();
        jLabel6 = new javax.swing.JLabel();
        walletsButton = new javax.swing.JButton();
        remindLaterButton = new javax.swing.JButton();
        dismissButton = new javax.swing.JButton();
        sponsorStartupDialog = new javax.swing.JDialog();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTextArea1 = new javax.swing.JTextArea();
        okButton = new javax.swing.JButton();
        mainToolbar = new javax.swing.JToolBar();
        mintingMonitorButton = new javax.swing.JButton();
        sponsorsButton = new javax.swing.JButton();
        namesButton = new javax.swing.JButton();
        nodeMonitorButton = new javax.swing.JButton();
        appearanceButton = new javax.swing.JButton();
        donateButton = new javax.swing.JButton();
        exitButton = new javax.swing.JButton();
        mainPanel = new javax.swing.JPanel();
        mintingMonitor = new mintmeister.MintingMonitor();
        sponsorsPanel = new mintmeister.SponsorsPanel();
        sponsorsPanel.initialise(this,dbManager);
        namesPanel = new mintmeister.NamesPanel();
        namesPanel.initialise(this);
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

        donateDialog.setModal(true);
        donateDialog.setUndecorated(true);
        donateDialog.setResizable(false);

        donatePanel.setBorder(javax.swing.BorderFactory.createCompoundBorder(new javax.swing.border.LineBorder(new java.awt.Color(22, 162, 22), 5, true), javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.LOWERED)));
        java.awt.GridBagLayout donatePanelLayout = new java.awt.GridBagLayout();
        donatePanelLayout.columnWidths = new int[] {0};
        donatePanelLayout.rowHeights = new int[] {0, 12, 0, 12, 0, 12, 0, 12, 0, 12, 0, 12, 0};
        donatePanel.setLayout(donatePanelLayout);

        jLabel6.setFont(new java.awt.Font("Bahnschrift", 0, 14)); // NOI18N
        jLabel6.setText("<html><div style='text-align: center;'>Enjoying MintMeister?<br/><br/>\n\nPlease consider supporting the creator of this app<br/>\nby sending a tip to one of MintMeister's Qortal wallets.<br/><br/>\n\nYou can find the wallet addresses on the wallets page.</div><html>");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        donatePanel.add(jLabel6, gridBagConstraints);

        walletsButton.setFont(new java.awt.Font("Bahnschrift", 0, 12)); // NOI18N
        walletsButton.setText("Go to wallets page");
        walletsButton.setPreferredSize(new java.awt.Dimension(150, 45));
        walletsButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                walletsButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        donatePanel.add(walletsButton, gridBagConstraints);

        remindLaterButton.setFont(new java.awt.Font("Bahnschrift", 0, 12)); // NOI18N
        remindLaterButton.setText("Remind me later");
        remindLaterButton.setMinimumSize(new java.awt.Dimension(122, 22));
        remindLaterButton.setPreferredSize(new java.awt.Dimension(150, 45));
        remindLaterButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                remindLaterButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        donatePanel.add(remindLaterButton, gridBagConstraints);

        dismissButton.setFont(new java.awt.Font("Bahnschrift", 0, 12)); // NOI18N
        dismissButton.setText("<html><div style='text-align: center;'>No thanks<br/>Don't show again</div><html>");
        dismissButton.setPreferredSize(new java.awt.Dimension(150, 45));
        dismissButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                dismissButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 12;
        donatePanel.add(dismissButton, gridBagConstraints);

        javax.swing.GroupLayout donateDialogLayout = new javax.swing.GroupLayout(donateDialog.getContentPane());
        donateDialog.getContentPane().setLayout(donateDialogLayout);
        donateDialogLayout.setHorizontalGroup(
            donateDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 454, Short.MAX_VALUE)
            .addGroup(donateDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(donatePanel, javax.swing.GroupLayout.DEFAULT_SIZE, 454, Short.MAX_VALUE))
        );
        donateDialogLayout.setVerticalGroup(
            donateDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 433, Short.MAX_VALUE)
            .addGroup(donateDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(donatePanel, javax.swing.GroupLayout.DEFAULT_SIZE, 433, Short.MAX_VALUE))
        );

        sponsorStartupDialog.setTitle("Important note");
        sponsorStartupDialog.setModal(true);
        sponsorStartupDialog.getContentPane().setLayout(new java.awt.GridBagLayout());

        jTextArea1.setEditable(false);
        jTextArea1.setColumns(20);
        jTextArea1.setLineWrap(true);
        jTextArea1.setRows(5);
        jTextArea1.setText("MintMeister has a newly added functionality: The sponsors panel.\n\nThe sponsors panel has multiple use cases: It can be used by sponsors to track their sponsoring activity and check the progress of their sponsees. It can be used by sponsees to keep track of their progress. It can be used by anyone to gain a better understanding of the larger scale metrics of the sponsoring process. \n\nAnother use case is to provide a tool for those who wish to investigate the behaviours that are indicative of self sponsoring.\n\nMintMeister extracts a lot of data from the Qortal blockchain and analyzes that data, some actions that could be construed as indicative of self sponsorship can be analyzed and flagged. This means that sponsors that exhibit a certain behaviour will be flagged when you request the app to do so.\n\nPlease take note that just because a sponsor is flagged or exhibits a certain behaviour, that does not mean that they are self sponsoring. In order for us to mitigate the threat of self sponsoring, we need to gain a better understanding of it and find out the combined set of behaviours and data points which indicate who the abusers are, that is one of the purposes of this tool. We should however be careful to not needlessly accuse or cause (collateral) damage to the progress and reputation of legitimate sponsors or sponsees.\n\nThis tool is meant as an explorative and educational endeavour, to show the sponsor/sponsee relationships in a visual and concrete manner. Hopefully we can use what we learn here to eliminate the threat of egregious self sponsoring while helping the growth of legitimate minters.\n");
        jTextArea1.setWrapStyleWord(true);
        jTextArea1.setMargin(new java.awt.Insets(10, 20, 10, 20));
        jScrollPane1.setViewportView(jTextArea1);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        sponsorStartupDialog.getContentPane().add(jScrollPane1, gridBagConstraints);

        okButton.setText("OK");
        okButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                okButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        gridBagConstraints.insets = new java.awt.Insets(15, 0, 15, 0);
        sponsorStartupDialog.getContentPane().add(okButton, gridBagConstraints);

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
        mintingMonitorButton.setText("Minting monitor");
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

        sponsorsButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/sponsors.png"))); // NOI18N
        sponsorsButton.setText("Sponsors");
        sponsorsButton.setFocusable(false);
        sponsorsButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        sponsorsButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        sponsorsButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                sponsorsButtonActionPerformed(evt);
            }
        });
        mainToolbar.add(sponsorsButton);

        namesButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/names.png"))); // NOI18N
        namesButton.setText("Names");
        namesButton.setFocusable(false);
        namesButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        namesButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        namesButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                namesButtonActionPerformed(evt);
            }
        });
        mainToolbar.add(namesButton);

        nodeMonitorButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/Images/monitor.png"))); // NOI18N
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("i18n/Language"); // NOI18N
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
            mainPanel.add(sponsorsPanel, "sponsorsPanel");
            mainPanel.add(namesPanel, "namesPanel");
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
              
        //on slow systems, the dialog may not have been triggered to invisible on leaving the chart
        mintingMonitor.chartMaker.chartDialog.setVisible(false);
    }//GEN-LAST:event_mintingMonitorButtonActionPerformed

    private void donateButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_donateButtonActionPerformed
    {//GEN-HEADEREND:event_donateButtonActionPerformed
        clipboardLabel.setText("Click on an address to copy it to your clipboard"); 
        
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

    private void namesButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_namesButtonActionPerformed
    {//GEN-HEADEREND:event_namesButtonActionPerformed
         CardLayout card = (CardLayout) mainPanel.getLayout();
        currentCard = "namesPanel";
        card.show(mainPanel, currentCard);
        if (nodeMonitorPanel.timer != null)
            nodeMonitorPanel.timer.cancel();  
        
        //on slow systems, the dialog may not have been triggered to invisible on leaving the chart
        mintingMonitor.chartMaker.chartDialog.setVisible(false);
    }//GEN-LAST:event_namesButtonActionPerformed

    private void walletsButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_walletsButtonActionPerformed
    {//GEN-HEADEREND:event_walletsButtonActionPerformed
        donateDialog.setVisible(false);
        CardLayout card = (CardLayout) mainPanel.getLayout();
        currentCard = "tipJarPanel";
        card.show(mainPanel, currentCard);
    }//GEN-LAST:event_walletsButtonActionPerformed

    private void remindLaterButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_remindLaterButtonActionPerformed
    {//GEN-HEADEREND:event_remindLaterButtonActionPerformed
        donateDialog.setVisible(false);
    }//GEN-LAST:event_remindLaterButtonActionPerformed

    private void dismissButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_dismissButtonActionPerformed
    {//GEN-HEADEREND:event_dismissButtonActionPerformed
        donateDialog.setVisible(false);
        File settingsFile = new File(System.getProperty("user.dir") + "/bin/settings.json");
        //exists check should be redundant, this function is called from a dialog that is only shown if json file exists
        if(settingsFile.exists())
        {
            try
            {
                String jsonString = Files.readString(settingsFile.toPath());
                if(jsonString != null)
                {
                    JSONObject jsonObject = new JSONObject(jsonString);
                    jsonObject.put("donateDismissed", "true");   
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(settingsFile)))
                    {
                        writer.write(jsonObject.toString(1));
                        writer.close();
                    }
                }                
            }
            catch (IOException | JSONException e)
            {
                BackgroundService.AppendLog(e);
            }
        }            
    }//GEN-LAST:event_dismissButtonActionPerformed

    private void sponsorsButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_sponsorsButtonActionPerformed
    {//GEN-HEADEREND:event_sponsorsButtonActionPerformed
        CardLayout card = (CardLayout) mainPanel.getLayout();
        currentCard = "sponsorsPanel";
        card.show(mainPanel, currentCard);
        if (nodeMonitorPanel.timer != null)
            nodeMonitorPanel.timer.cancel();  
              
        //on slow systems, the dialog may not have been triggered to invisible on leaving the chart
        mintingMonitor.chartMaker.chartDialog.setVisible(false);
        
        //always true on startup
        if(!sponsorDialogShown)
        {
            // fail safe making sure dialog will not be loaded every time the user clicks this button
            sponsorDialogShown = true;
            
            boolean shown;            
            Object dialogObject = Utilities.getSetting("sponsorDialogShown", "settings.json");            
            if(dialogObject != null)
                shown = Boolean.parseBoolean(dialogObject.toString());
            else
                shown = false;
            
            if(!shown)
            {
                Utilities.updateSetting("sponsorDialogShown", "true", "settings.json"); 
                sponsorStartupDialog.pack();
                sponsorStartupDialog.setSize(600,525);
                int x = getX() + ((getWidth() / 2) - (sponsorStartupDialog.getWidth() / 2));
                int y = getY() + ((getHeight() / 2) - (sponsorStartupDialog.getHeight() / 2));
                sponsorStartupDialog.setLocation(x, y);
                sponsorStartupDialog.setVisible(true); 
            }
        }            
    }//GEN-LAST:event_sponsorsButtonActionPerformed

    private void okButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_okButtonActionPerformed
    {//GEN-HEADEREND:event_okButtonActionPerformed
        sponsorStartupDialog.setVisible(false);
        sponsorStartupDialog.dispose();
    }//GEN-LAST:event_okButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton appearanceButton;
    private javax.swing.ButtonGroup appearanceGroup;
    private javax.swing.JPopupMenu appearanceMenu;
    private javax.swing.JTextField btcField;
    private javax.swing.JLabel clipboardLabel;
    private javax.swing.JButton dismissButton;
    private javax.swing.JTextField dogeField;
    private javax.swing.JButton donateButton;
    private javax.swing.JDialog donateDialog;
    private javax.swing.JPanel donatePanel;
    private javax.swing.JButton exitButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JTextArea jTextArea1;
    private javax.swing.JTextField ltcField;
    protected javax.swing.JPanel mainPanel;
    protected javax.swing.JToolBar mainToolbar;
    private mintmeister.MintingMonitor mintingMonitor;
    private javax.swing.JButton mintingMonitorButton;
    private javax.swing.JButton namesButton;
    private mintmeister.NamesPanel namesPanel;
    private javax.swing.JButton nodeMonitorButton;
    protected mintmeister.MonitorPanel nodeMonitorPanel;
    private javax.swing.JButton okButton;
    private javax.swing.JLabel popUpLabel;
    private javax.swing.JTextField qortField;
    private javax.swing.JButton remindLaterButton;
    private javax.swing.JDialog sponsorStartupDialog;
    private javax.swing.JButton sponsorsButton;
    private mintmeister.SponsorsPanel sponsorsPanel;
    private javax.swing.JPanel tipJarPanel;
    private javax.swing.JScrollPane tipJarScrollPane;
    public javax.swing.JDialog trayPopup;
    private javax.swing.JButton walletsButton;
    // End of variables declaration//GEN-END:variables

        
}//end class GUI




