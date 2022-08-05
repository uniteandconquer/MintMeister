package mintmeister;

import java.awt.HeadlessException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class DevFundPanel extends javax.swing.JPanel
{
    private DatabaseManager dbManager;
    private String orderKey = "desc";
    
    private double qortBought = 0; 
    private double qortSold = 0;
    private double ltcBought = 0;
    private double ltcSold = 0;
    private double dogeBought = 0;
    private double dogeSold = 0;                   
    private double qortBoughtWithLtc = 0;
    private double qortBoughtWithDoge = 0;                   
    private double qortSoldForLtc = 0;
    private double qortSoldForDoge = 0;                   
    private double listedAmount = 0;
    private double balance = 0;
                   
    private double totalReceived = 0;
    private double totalPaid = 0;
    private int totalReceivedCount = 0;
    private int totalPaidCount = 0; 
    private int totalDonors = 0; 
    private int totalBeneficiaries = 0;
    
    public DevFundPanel()
    {
        initComponents();
    }
    
    protected void init(DatabaseManager dbManager)
    {
        this.dbManager = dbManager;
        
        summaryTable.getTableHeader().addMouseListener(new MouseAdapter()
        {
           @Override
           public void mouseClicked(MouseEvent e)
           {
               orderKey = orderKey.equals("asc") ? "desc" : "asc";  
               int col = summaryTable.columnAtPoint(e.getPoint());
               String headerName = summaryTable.getColumnName(col); 
               arrangeSummaryTable(headerName);
           }
       });   
    }
    
    private void arrangeSummaryTable(String headerName)
    {
        try(Connection connection = ConnectionDB.getConnection("minters"))
        {
            String key = null;
            switch(headerName)
            {
                case "Address":
                    key = "address";
                    break;
                case "Name":
                    key = "name";
                    break;
                case "Total donated":
                    key = "donated";
                    break;
                case "Total received":
                    key = "received";
                    break;
                    case "Total donations made":
                    key = "donated_count";
                    break;
                case "Total donations received":
                    key = "received_count";
                    break;
            }
            if(key == null)
                return;
            
            fillSummaryTable(key, connection);
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }
    
    private void fillSummaryTable(String key, Connection connection) throws SQLException
    {                   
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("select * from dev_fund order by " + key + " " + orderKey);
        DefaultTableModel model = (DefaultTableModel) summaryTable.getModel();

        model.setRowCount(0);
        
        while(resultSet.next())
        {
            model.addRow(new Object[]
             {
                 resultSet.getString("address"),
                 resultSet.getString("name"),
                 String.format("%,.2f", resultSet.getDouble("donated")),
                 String.format("%,.2f", resultSet.getDouble("received")),
                 resultSet.getInt("donated_count"),
                 resultSet.getInt("received_count"),
             }
         );    
        }
    }
    
    
    private String getNameForAddress(String address,boolean searchAPI, Connection connection)
    {
        try
        {
            Object nameObject = dbManager.tryGetItemValue("names", "name", "address", Utilities.SingleQuotedString(address), connection);
            if(nameObject == null)
            {
                if(!searchAPI)
                    return "";
                
                String name;
                String jsonString = Utilities.ReadStringFromURL("http://" + dbManager.socket + "/names/address/" + address);
                JSONArray nameArray = new JSONArray(jsonString);
                if (nameArray.length() > 0)
                {
                    JSONObject jso = nameArray.getJSONObject(0);
                    name = jso.getString("name");

                    if(name.contains("'"))
                        name = name.replace("'", "''");

                    return name;
                }
                else
                    return "";
            }
            else
            {
                String name = nameObject.toString();
                if(name.contains("'"))
                    name = name.replace("'", "''");

                return name;
            }
        }
        catch (IOException | TimeoutException | JSONException e)
        {
            BackgroundService.AppendLog("Could not fetch name from API");
            BackgroundService.AppendLog(e);
            return "";
        }            
    }
    
    public String convertToCSV(String[] data)
    {
        return Stream.of(data)
                .map(this::escapeSpecialCharacters)
                .collect(Collectors.joining(","));
    }

    public String escapeSpecialCharacters(String data)
    {
        String escapedData = data.replaceAll("\\R", " ");
        if (data.contains(",") || data.contains("\"") || data.contains("'"))
        {
            data = data.replace("\"", "\"\"");
            escapedData = "\"" + data + "\"";
        }
        return escapedData;
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

        exportButton = new javax.swing.JButton();
        summaryButton = new javax.swing.JButton();
        infoLabel = new javax.swing.JLabel();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        summaryTableScrollPane = new javax.swing.JScrollPane();
        summaryTable = new javax.swing.JTable();
        devFundTableScrollPane = new javax.swing.JScrollPane();
        devFundTable = new javax.swing.JTable();
        resultsScrollPane = new javax.swing.JScrollPane();
        resultsScrollPane.setVisible(false);
        resultsTable = new javax.swing.JTable();
        buySellTableScrollPane = new javax.swing.JScrollPane();
        buySellTableScrollPane.setVisible(false);
        buySellTable = new javax.swing.JTable();
        ltcTableScrollPane = new javax.swing.JScrollPane();
        ltcTableScrollPane.setVisible(false);
        ltcTable = new javax.swing.JTable();
        dogeTableScrollPane = new javax.swing.JScrollPane();
        dogeTableScrollPane.setVisible(false);
        dogeTable = new javax.swing.JTable();

        setLayout(new java.awt.GridBagLayout());

        exportButton.setText("Export to CSV file");
        exportButton.setEnabled(false);
        exportButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                exportButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(10, 125, 10, 0);
        add(exportButton, gridBagConstraints);

        summaryButton.setText("Get summary");
        summaryButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                summaryButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 125);
        add(summaryButton, gridBagConstraints);

        infoLabel.setFont(new java.awt.Font("Segoe UI", 1, 14)); // NOI18N
        infoLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        infoLabel.setText("Click on 'Get summary' to get an overview of DevFund donors and beneficiaries");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
        add(infoLabel, gridBagConstraints);

        summaryTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][]
            {

            },
            new String []
            {
                "Address", "Name", "Total donated", "Total received", "Total donations made", "Total donations received"
            }
        )
        {
            boolean[] canEdit = new boolean []
            {
                true, false, false, false, false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex)
            {
                return canEdit [columnIndex];
            }
        });
        summaryTableScrollPane.setViewportView(summaryTable);

        jTabbedPane1.addTab("Summary", summaryTableScrollPane);

        devFundTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][]
            {

            },
            new String []
            {
                "Timestamp", "Type", "Amount", "Creator", "Creator name", "Recipient", "Recipient name", "Blockheight"
            }
        )
        {
            boolean[] canEdit = new boolean []
            {
                false, false, false, false, false, false, false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex)
            {
                return canEdit [columnIndex];
            }
        });
        devFundTableScrollPane.setViewportView(devFundTable);

        jTabbedPane1.addTab("All transactions", devFundTableScrollPane);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        add(jTabbedPane1, gridBagConstraints);

        resultsTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][]
            {
                {null, null, null, null, null, null},
                {null, null, null, null, null, null},
                {null, null, null, null, null, null},
                {null, null, null, null, null, null}
            },
            new String []
            {
                "Total funds received", "Total funds paid out", "Total donations received", "Total donations paid out", "Total donors", "Total beneficiaries"
            }
        ));
        resultsScrollPane.setViewportView(resultsTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipady = 20;
        add(resultsScrollPane, gridBagConstraints);

        buySellTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][]
            {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String []
            {
                "Balance", "Total QORT bought", "Total QORT sold", "Total QORT listed"
            }
        ));
        buySellTableScrollPane.setViewportView(buySellTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipady = 20;
        add(buySellTableScrollPane, gridBagConstraints);

        ltcTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][]
            {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String []
            {
                "Total LTC bought", "Total LTC sold", "Average LTC buy price", "Average LTC sell price"
            }
        ));
        ltcTableScrollPane.setViewportView(ltcTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipady = 20;
        add(ltcTableScrollPane, gridBagConstraints);

        dogeTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][]
            {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String []
            {
                "Total Doge bought", "Total Doge sold", "Average Doge buy price", "Average Doge sell price"
            }
        ));
        dogeTableScrollPane.setViewportView(dogeTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipady = 20;
        add(dogeTableScrollPane, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents

    private void summaryButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_summaryButtonActionPerformed
    {//GEN-HEADEREND:event_summaryButtonActionPerformed
        Thread thread = new Thread(()->
        {            
            summaryButton.setEnabled(false);
            
            final String devFundAddress = "QWfYVQfuz2rVskYkpkVvLxL3kUPmBgKhHV" ;

            try(Connection connection = ConnectionDB.getConnection("minters"))
            {  
                if(!dbManager.TableExists("dev_fund", connection))
                    dbManager.CreateTable(new String[]
                    {
                        "dev_fund",
                        "name","varchar(max)",
                        "address","varchar(100)",
                        "donated","double",
                        "received","double",
                        "donated_count","int",
                        "received_count","int"                          
                    }, connection);              
                
                dbManager.ExecuteUpdate("delete from dev_fund", connection);                      
                dbManager.ExecuteUpdate("update dev_fund set donated=0", connection);  
                dbManager.ExecuteUpdate("update dev_fund set received=0", connection); 
                dbManager.ExecuteUpdate("update dev_fund set donated_count=0", connection); 
                dbManager.ExecuteUpdate("update dev_fund set received_count=0", connection); 
                  
                DefaultTableModel model = (DefaultTableModel) devFundTable.getModel();
                model.setRowCount(0);  

                infoLabel.setText(Utilities.AllignCenterHTML("Looking up all DevFund transactions please wait..."));                
                
                balance =  Double.parseDouble(Utilities.ReadStringFromURL(
                    "http://" + dbManager.socket + "/addresses/balance/" + devFundAddress)); 

                try
                {
                    String jsonString = Utilities.ReadStringFromURL("http://" + dbManager.socket + "/transactions/search?txType=PAYMENT"
                            + "&txType=AT&txType=DEPLOY_AT&address=" + devFundAddress + "&confirmationStatus=CONFIRMED&limit=0&reverse=true");    

                   JSONArray txArray = new JSONArray(jsonString);
                   JSONObject jso;
                   JSONObject namesObject = new JSONObject();

                   infoLabel.setText(Utilities.AllignCenterHTML("Found " + txArray.length() + " transactions for DevFund, processing results..."));

                   
                   double totalPlus = 0;
                   double totalMinus = 0;
                   
                   for(int i = 0; i < txArray.length(); i++)
                   {
                       jso = txArray.getJSONObject(i);
                       
                       //In this block the buy/sell activity is fetched                       
                       if(jso.getString("type").equals("DEPLOY_AT") || jso.getString("type").equals("AT"))  //CHANGE TO EQUALS  AT IF DEPLOY AT NOT USED
                       {
                            String atAddress;
                            
                            if(jso.has("atAddress"))
                                 atAddress = jso.getString("atAddress");
                            else
                                atAddress = jso.getString("aTAddress");

                            String atString;

                            try
                            {
                                atString = Utilities.ReadStringFromURL("http://" + dbManager.socket + "/crosschain/trade/" + atAddress);
                            }
                            catch (IOException | TimeoutException e)
                            {
                                System.err.println("Could not get data from AT address " + atAddress + " , skipping trade.");
                                BackgroundService.AppendLog("Could not get data from AT address " + atAddress + " , skipping trade.");
                                continue;
                            }

                            JSONObject atAddressObject = new JSONObject(atString);

                            String status = atAddressObject.getString("mode"); 

                            if (status.equals("REDEEMED") || status.equals("TRADING"))
                            {
                                String buyer;
                                
                                if(!atAddressObject.has("qortalPartnerReceivingAddress"))
                                    buyer = atAddressObject.getString("qortalPartnerAddress");
                                else
                                    buyer = atAddressObject.getString("qortalPartnerReceivingAddress");

                                String foreignChain = atAddressObject.getString("foreignBlockchain");
                                double qortAmount = atAddressObject.getDouble("qortAmount");
                                double foreignAmount = atAddressObject.getDouble("expectedForeignAmount");

                                boolean sellingQort = jso.getString("type").equals("DEPLOY_AT");

                                //Due to some completed trades stuck in TRADING mode, we get the QORT sells from deploy_at and the QORT buys from at
                                if(buyer.equals(devFundAddress) && !sellingQort)
                                {
                                    if(foreignChain.equals("LITECOIN"))
                                    {
                                        ltcSold += foreignAmount;
                                        qortBoughtWithLtc += qortAmount;
                                    }
                                    if(foreignChain.equals("DOGECOIN"))
                                    {
                                        dogeSold += foreignAmount;
                                        qortBoughtWithDoge += qortAmount;
                                    }

                                    qortBought += qortAmount;
                                }
                                else if(sellingQort)
                                {
                                    if(foreignChain.equals("LITECOIN"))
                                    {
                                        ltcBought += foreignAmount;
                                        qortSoldForLtc += qortAmount;
                                    }
                                    if(foreignChain.equals("DOGECOIN"))
                                    {
                                        dogeBought += foreignAmount;
                                        qortSoldForDoge += qortAmount;
                                    }

                                    qortSold += qortAmount;                                        
                                }                                
                            }
                            else if(status.equals("LISTED"))
                                listedAmount += atAddressObject.getDouble("qortAmount");
                           
                           continue;
                       }
                       
                       //From this point the sent/received data is fetched                     

                       long timestamp = jso.getLong("timestamp");
                       int blockHeight = jso.getInt("blockHeight");
                       double amount = jso.getDouble("amount");
                       String type = jso.getString("type");
                       String creatorAddress = jso.getString("creatorAddress");
                       
                       String creatorName;
                       if(namesObject.has(creatorAddress))
                           creatorName = namesObject.getString(creatorAddress);
                       else
                       {
                           creatorName = getNameForAddress(creatorAddress, true, connection);
                           namesObject.put(creatorAddress, creatorName);
                       }
                       String recipient = "";
                       String recipientName = "";
                       if(jso.has("recipient"))
                       {
                            recipient = jso.getString("recipient");
                            if(namesObject.has(recipient))
                                recipientName = namesObject.getString(recipient);
                            else
                            {
                                recipientName = getNameForAddress(recipient, true, connection);
                                namesObject.put(recipient, recipientName);
                            }
                       }
                       
                       if(creatorAddress.equals(devFundAddress))
                           totalMinus += amount;
                       else
                           totalPlus += amount;

                       final String finalRecipient = recipient;
                       final String finalRecName = recipientName;
                       SwingUtilities.invokeLater(()->
                       {
                            model.addRow(new Object[]
                                {
                                    Utilities.DateFormatShort(timestamp),
                                    type,
                                    String.format("%,.2f", amount),
                                    creatorAddress,
                                    creatorName,
                                    finalRecipient,
                                    finalRecName,
                                    Utilities.numberFormat(blockHeight)
                                }
                            );                       
                       });
                       
                       boolean isDonation = !creatorAddress.equals(devFundAddress);
                       
                       if(isDonation)
                       {
                           Object donatedObject = dbManager.tryGetItemValue("dev_fund", "donated", "address",
                                   Utilities.SingleQuotedString(creatorAddress), connection);
                           double myDonated = donatedObject == null ? 0 : (double) donatedObject;
                           Object donatedCountObject = dbManager.tryGetItemValue("dev_fund", "donated_count", "address",
                                   Utilities.SingleQuotedString(creatorAddress), connection);
                           double myDonatedCount = donatedCountObject == null ? 0 : (int) donatedCountObject;                           
                           
                           if(donatedObject == null)
                           {
                               dbManager.InsertIntoDB(new String[]
                               {
                                   "dev_fund",
                                   "address",Utilities.SingleQuotedString(creatorAddress),
                                   "name",Utilities.SingleQuotedString(creatorName),
                                   "donated",String.valueOf(amount),
                                   "donated_count", "1",
                                   "received","0",
                                   "received_count","0"
                               }, connection);
                           }
                           else
                           {
                                dbManager.ChangeValue("dev_fund", "donated", String.valueOf(myDonated + amount), "address", 
                                        Utilities.SingleQuotedString(creatorAddress),connection);
                                dbManager.ChangeValue("dev_fund", "donated_count", String.valueOf(myDonatedCount + 1), "address", 
                                        Utilities.SingleQuotedString(creatorAddress),connection);                               
                            }
                       }
                       else
                       {
                           Object receivedObject = dbManager.tryGetItemValue("dev_fund", "received", "address",
                                   Utilities.SingleQuotedString(recipient), connection);
                           double myReceived = receivedObject == null ? 0 : (double) receivedObject;
                           Object receivedCountObject = dbManager.tryGetItemValue("dev_fund", "received_count", "address",
                                   Utilities.SingleQuotedString(recipient), connection);
                           double myReceivedCount = receivedCountObject == null ? 0 : (int) receivedCountObject;
                           
                           if(receivedObject == null)
                           {
                               dbManager.InsertIntoDB(new String[]
                               {
                                   "dev_fund",
                                   "address",Utilities.SingleQuotedString(recipient),
                                   "name",Utilities.SingleQuotedString(recipientName),
                                   "donated","0",
                                   "donated_count", "0",
                                   "received",String.valueOf(amount),
                                   "received_count","1"                                   
                               }, connection);
                           }
                           else
                           {
                                dbManager.ChangeValue("dev_fund", "received", String.valueOf(myReceived + amount), "address", 
                                        Utilities.SingleQuotedString(recipient),connection);
                                dbManager.ChangeValue("dev_fund", "received_count", String.valueOf(myReceivedCount + 1), "address", 
                                        Utilities.SingleQuotedString(recipient),connection);                                   
                           }                                                 
                       }      
                   }                   
                   
                   Statement statement = connection.createStatement();
                   ResultSet resultSet = statement.executeQuery("select donated, received, donated_count, received_count from dev_fund");
                   
                   while(resultSet.next())
                   {
                       //use donated here totalReceived is the dev fund received
                       totalReceived += resultSet.getDouble("donated");
                       //use received here totalPaid is the devfund paid
                       totalPaid += resultSet.getDouble("received");
                       
                       totalReceivedCount += resultSet.getInt("donated_count");
                       totalPaidCount += resultSet.getInt("received_count");
                       
                       if(resultSet.getDouble("donated") > 0)
                           totalDonors++;
                       if(resultSet.getDouble("received") > 0)
                           totalBeneficiaries++;
                   }
                   
                   DefaultTableModel tableModel = (DefaultTableModel) resultsTable.getModel();
                   tableModel.setRowCount(0);
                   
                    tableModel.addRow(new Object[]
                    {
                        String.format("%,.2f", totalReceived),
                        String.format("%,.2f", totalPaid),
                        totalReceivedCount,
                        totalPaidCount,
                        totalDonors,
                        totalBeneficiaries
                    });
                    
                   tableModel = (DefaultTableModel) buySellTable.getModel();
                   tableModel.setRowCount(0);
                   
                    tableModel.addRow(new Object[]
                    {
                        String.format("%,.2f", balance),
                        String.format("%,.2f", qortBought),
                        String.format("%,.2f", qortSold),
                        String.format("%,.2f", listedAmount),
                    });
                    
                   tableModel = (DefaultTableModel) ltcTable.getModel();
                   tableModel.setRowCount(0);
                   
                    tableModel.addRow(new Object[]
                    {
                        String.format("%,.2f", ltcBought),
                        String.format("%,.2f", ltcSold),
                        String.format("%,.5f", (ltcBought / qortSoldForLtc)),
                        String.format("%,.5f", (ltcSold / qortBoughtWithLtc))
                    });
                    
                   tableModel = (DefaultTableModel) dogeTable.getModel();
                   tableModel.setRowCount(0);
                   
                    tableModel.addRow(new Object[]
                    {
                        String.format("%,.2f", dogeBought),
                        String.format("%,.2f", dogeSold),
                        String.format("%,.5f", (dogeBought / qortSoldForDoge)),
                        String.format("%,.5f", (dogeSold / qortBoughtWithDoge))
                    });

                   orderKey = "desc";
                   fillSummaryTable("donated",connection);            
                   
                   resultsScrollPane.setVisible(true);
                   buySellTableScrollPane.setVisible(true);
                   ltcTableScrollPane.setVisible(true);
                   dogeTableScrollPane.setVisible(true);
                   infoLabel.setVisible(false);

                }
                catch(ConnectException e)
                {
                    JOptionPane.showMessageDialog(this, "Could not connect to Qortal core, is your core/SSH tunnel active?");
                }
                catch (IOException | TimeoutException | JSONException e)
                {
                    JOptionPane.showMessageDialog(this, "Unexpected error : " + e.toString());
                    BackgroundService.AppendLog(e);
                }                   
            }
            catch (Exception e)
            {
                BackgroundService.AppendLog(e);
            }   
            
            summaryButton.setEnabled(true);
            exportButton.setEnabled(true);
        
        });
        thread.start();        
            
    }//GEN-LAST:event_summaryButtonActionPerformed

    private void exportButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_exportButtonActionPerformed
    {//GEN-HEADEREND:event_exportButtonActionPerformed
        JFileChooser jfc = new JFileChooser(System.getProperty("user.home"));
        
        FileNameExtensionFilter filter = new FileNameExtensionFilter("csv files (*.csv)", "csv");
        //show preferred filename in filechooser
        jfc.setSelectedFile(new File("DevFund-Summary-" + Utilities.DateFormatPath(System.currentTimeMillis()) + ".csv")); 
            
        // add filters
        jfc.setAcceptAllFileFilterUsed(false);
        jfc.addChoosableFileFilter(filter);
        jfc.setFileFilter(filter);
        int returnValue = jfc.showSaveDialog(null);

        if (returnValue == JFileChooser.APPROVE_OPTION)
        {            
            File selectedFile = jfc.getSelectedFile();    
            
            try
            {
                ArrayList<String[]> dataLines = new ArrayList<>();

                dataLines.add(new String[]{""});
                dataLines.add(new String[]{""});   
                dataLines.add(new String[]{"Funds received","Funds paid out","Donations received","Donations paid","Total donors","Total beneficiaries"});

                dataLines.add(new String[]
                {
                    String.valueOf(totalReceived),
                    String.valueOf(totalPaid),
                    String.valueOf(totalReceivedCount),
                    String.valueOf(totalPaidCount),
                    String.valueOf(totalDonors),
                    String.valueOf(totalBeneficiaries),
                });  

                dataLines.add(new String[]{""});
                dataLines.add(new String[]{""});            
                dataLines.add(new String[]{"Balance","QORT bought","QORT sold","QORT listed"}); 

                dataLines.add(new String[]
                {
                    String.valueOf(balance),
                    String.valueOf(qortBought),
                    String.valueOf(qortSold),
                    String.valueOf(listedAmount),
                }); 

                dataLines.add(new String[]{""});
                dataLines.add(new String[]{""});            
                dataLines.add(new String[]{"LTC bought","LTC sold","Avg LTC buy price","Avg LTC sell price"}); 

                dataLines.add(new String[]
                {
                    String.valueOf(ltcBought),
                    String.valueOf(ltcSold),
                    String.valueOf((ltcBought / qortSoldForLtc)),
                    String.valueOf((ltcSold / qortBoughtWithLtc)),
                }); 

                dataLines.add(new String[]{""});
                dataLines.add(new String[]{""});            
                dataLines.add(new String[]{"Doge bought","Doge sold","Avg Doge buy price","Avg Doge sell price"}); 

                dataLines.add(new String[]
                {
                    String.valueOf(dogeBought),
                    String.valueOf(dogeSold),
                    String.valueOf((dogeBought / qortSoldForDoge)),
                    String.valueOf((dogeSold / qortBoughtWithDoge)),
                }); 


                try(Connection connection = ConnectionDB.getConnection("minters"))
                {
                    Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("select * from dev_fund order by donated desc");

                    dataLines.add(new String[]{""});
                    dataLines.add(new String[]{""});            
                    dataLines.add(new String[]{"Address","Name","Total donated","Total received","Donations made","Donations received"}); 

                    while(resultSet.next())
                    {
                        dataLines.add(new String[]
                         {
                             resultSet.getString("address"),
                             resultSet.getString("name"),
                             String.valueOf(resultSet.getDouble("donated")),
                             String.valueOf(resultSet.getDouble("received")),
                             String.valueOf(resultSet.getInt("donated_count")),
                             String.valueOf(resultSet.getInt("received_count"))
                         }
                     );    
                    }

                }
                catch (Exception e)
                {
                    BackgroundService.AppendLog(e);
                }

                File csvOutputFile = selectedFile;
                try (PrintWriter pw = new PrintWriter(csvOutputFile))
                {
                    dataLines.stream()
                            .map(this::convertToCSV)
                            .forEach(pw::println);
                }
    //            assertTrue(csvOutputFile.exists());
    
                JOptionPane.showMessageDialog(this, "File was saved to " + selectedFile.getPath());

            }
            catch (HeadlessException | FileNotFoundException e)
            {
                BackgroundService.AppendLog(e);
            }
        }     
    }//GEN-LAST:event_exportButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTable buySellTable;
    private javax.swing.JScrollPane buySellTableScrollPane;
    private javax.swing.JTable devFundTable;
    private javax.swing.JScrollPane devFundTableScrollPane;
    private javax.swing.JTable dogeTable;
    private javax.swing.JScrollPane dogeTableScrollPane;
    private javax.swing.JButton exportButton;
    private javax.swing.JLabel infoLabel;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JTable ltcTable;
    private javax.swing.JScrollPane ltcTableScrollPane;
    private javax.swing.JScrollPane resultsScrollPane;
    private javax.swing.JTable resultsTable;
    private javax.swing.JButton summaryButton;
    private javax.swing.JTable summaryTable;
    private javax.swing.JScrollPane summaryTableScrollPane;
    // End of variables declaration//GEN-END:variables
}
