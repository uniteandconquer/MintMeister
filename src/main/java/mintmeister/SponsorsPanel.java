
package mintmeister;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SponsorsPanel extends javax.swing.JPanel
{
    private GUI gui;
    private DatabaseManager dbManager;
    private boolean LOOKUP_HALTED;
    private boolean updateInProgress;
    private double balanceThreshold;
    private String orderKey = "asc";
    private boolean includeZeroCount = true;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ChartMaker chartMaker;
    private boolean executorBusy; 
    ArrayList<Integer> searchResults = new ArrayList<>();
    int searchIndex;
    
    public SponsorsPanel()
    {
        initComponents();
    }
    
    protected void initialise(GUI gui,DatabaseManager dbManager)
    {
        this.gui = gui;
        this.dbManager = dbManager;
        
        chartMaker = new ChartMaker("", gui);
        
        Object balanceObject = Utilities.getSetting("sponseeBalanceThreshold", "settings.json");
        if(balanceObject != null)
            sponseeSpinner.setValue(Integer.parseInt(balanceObject.toString()));
        
        Object maxTxObject = Utilities.getSetting("maxTxLookup", "settings.json");
        if(maxTxObject != null)
            maxTxSpinner.setValue(Integer.parseInt(maxTxObject.toString()));
        
        try (Connection connection = ConnectionDB.getConnection("sponsors"))
        {
            if (dbManager.TableExists("reward_shares", connection))
            {
                dbManager.FillJTableOrder("reward_shares", "timestamp", "desc", rewardsharesTable, true,connection);
                
                int totalRewardShares = dbManager.getRowCount("reward_shares", connection);                
                updateStatusLabel.setText(Utilities.AllignCenterHTML(String.format("Total reward share transactions: %s",
                        Utilities.numberFormat(totalRewardShares))));
            }
            if (dbManager.TableExists("sponsors", connection))
                dbManager.FillJTableOrder("sponsors", "sponsee_count", "desc", sponsorsTable, false, connection);
            if (dbManager.TableExists("sponsees", connection))
                fillSponseesTable("");
            
            fillInfoTable();
            
            initListeners();
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }
    
    private void initListeners()
    {
        //Mouse listener not needed (no double click functionality implemented)
        sponsorsTable.getSelectionModel().addListSelectionListener((ListSelectionEvent event) ->
        {            
            if(event.getValueIsAdjusting())
                return;     
            
            toggleSponsorButtons(sponsorsTable.getSelectedRow() >= 0);
            
            sponsorsTableSelected();
        });  
        
        //Mouse listener not needed (no double click functionality implemented)
        sponseesTable.getSelectionModel().addListSelectionListener((ListSelectionEvent event) ->
        {            
            if(event.getValueIsAdjusting())
                return;     
            
            int count = sponseesTable.getSelectedRowCount();
            if(count == 1)
                resultsLabel.setText("1 row is selected");
            else
                resultsLabel.setText(sponseesTable.getSelectedRowCount() + " rows are selected");
        }); 
        
        sponsorsTable.getTableHeader().addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                orderKey = orderKey.equals("asc") ? "desc" : "asc";  
                int col = sponsorsTable.columnAtPoint(e.getPoint());
                String headerName = sponsorsTable.getColumnName(col); 
                arrangeTableColumn(sponsorsTable, "sponsors", headerName);
            }
        });   
        
        sponseesTable.getTableHeader().addMouseListener(new MouseAdapter()
        {          
            @Override
            public void mouseClicked(MouseEvent e)
            {
                orderKey = orderKey.equals("asc") ? "desc" : "asc";  
                int col = sponseesTable.columnAtPoint(e.getPoint());
                String headerName = sponseesTable.getColumnName(col);     
                fillSponseesTable("order by " + headerName + " " + orderKey);
            }
        }); 
        
        rewardsharesTable.getTableHeader().addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                orderKey = orderKey.equals("asc") ? "desc" : "asc";  
                int col = rewardsharesTable.columnAtPoint(e.getPoint());
                String headerName = rewardsharesTable.getColumnName(col); 
                arrangeTableColumn(rewardsharesTable, "reward_shares", headerName);
            }
        });  
        
        initDialogListeners(sponseesTable);
        initDialogListeners(sponsorsTable);
        
        //sponsee info dialog tables
        initTxLookupListeners(mySponsorsTable);
        initTxLookupListeners(sponseeInfoTable);
        initTxLookupListeners(payeesTable);
        initTxLookupListeners(commonPayeesTable);
        initTxLookupListeners(paymentsTable);
        
        //sponsor info dialog tables
        initTxLookupListeners(sponsorInfoTable);
        initTxLookupListeners(commonPayersTable);
        initTxLookupListeners(payeesTableSponsor);        
        
        initTxLookupListeners(flaggedSponsorsTable);
        
        //Transactions dialog also allows looking up transactions for addresses in it's table
        initTxLookupListeners(transactionsTable);
    }
    
    private void initDialogListeners(JTable table)
    {
        //Use key binding to re-map the 'Enter' functionality. Otherwise the selected row will increment on enter key release
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "Enter");
        table.getActionMap().put("Enter", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                if(table == sponseesTable)
                    showSponseeInfo();
                else
                    showSponsorInfo();
            }
        });
                
        //needed for double click event
        table.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseReleased(MouseEvent mouseEvent)
            {   
                //don't need to check for single click, the listSelectionListener will pick up a single click event                
                if (mouseEvent.getClickCount() == 2 && table.getSelectedRow() != -1)
                {                    
                    if(table == sponseesTable)
                        showSponseeInfo();
                    else
                        showSponsorInfo();
                }
            }
        });
    }
    
    private void initTxLookupListeners(JTable table)
    {
        //Use key binding to re-map the 'Enter' functionality. Otherwise the selected row will increment on enter key release
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "Enter");
        table.getActionMap().put("Enter", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent ae)
            {
                showTransactionsInfo(table);
            }
        });
                
        //needed for double click event
        table.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseReleased(MouseEvent mouseEvent)
            {   
                //don't need to check for single click, the listSelectionListener will pick up a single click event                
                if (mouseEvent.getClickCount() == 2 && table.getSelectedRow() != -1)
                {  
                    showTransactionsInfo(table);
                }
            }
        });        
    }
    
    private void fillInfoTable()
    {
        try(Connection c = ConnectionDB.getConnection("sponsors"))
        {
            if(!dbManager.TableExists("sponsors", c) || !dbManager.TableExists("sponsees", c))
                return;
            
            long currentTime = System.currentTimeMillis();
            
            Statement statement = c.createStatement();
            ResultSet resultSet = statement.executeQuery("select * from sponsors");
            
            //accounts with reward shares and 0 sponsees
            int rewardshareCreators = 0;
            //accounts with reward shares and >0 sponsees
            int sponsors = 0;
            long earliestScan = Long.MAX_VALUE;
            long lastScan = 0;
            int foundersCount = 0;
            int foundersSponseesCount = 0;
            int mintedAdjustmentSponsors = 0;
            double totalFoundersBalance = 0;
            double totalSponsorsBalance = 0;
            int deepScannedCount = 0;
            int sponsorNamesCount = 0;               
            ArrayList<String> eligibleList = new ArrayList<>();
            
            while(resultSet.next())
            {
                eligibleList.add(resultSet.getString("address"));
                
                rewardshareCreators++;
                
                int sponseeCount = resultSet.getInt("sponsee_count");
                if(sponseeCount > 0)
                    sponsors++;
                
                Object balance = resultSet.getObject("balance");
                totalSponsorsBalance += balance == null ? 0 : (double)balance;
                
                if(resultSet.getString("is_founder").equals("F"))
                {
                    foundersCount++;
                    totalFoundersBalance += balance == null ? 0 : (double)balance;
                    foundersSponseesCount += sponseeCount;
                }
                if(resultSet.getInt("minted_adjustment") > 0)
                    mintedAdjustmentSponsors++;
                
                Object nameObject = resultSet.getObject("name");
                if(nameObject != null && !nameObject.toString().isBlank())
                    sponsorNamesCount++;
                
                Object scanned = resultSet.getObject("scanned");
                deepScannedCount += scanned == null ? 0 : 1;
                
                if(scanned != null && (long)scanned < earliestScan)
                    earliestScan = (long)scanned;
                if(scanned != null && (long)scanned > lastScan)
                    lastScan = (long)scanned;
            }
            
            statement = c.createStatement();
            resultSet = statement.executeQuery("select * from sponsees");
            
            ArrayList<String> sponseesList = new ArrayList<>();
            ArrayList<String> multiSponsored = new ArrayList<>();
            ArrayList<String> activeMonth = new ArrayList<>();
            ArrayList<String> activeQuarter = new ArrayList<>();
            ArrayList<String> activeYear = new ArrayList<>();
            
            double totalBalance = 0;
            int sponseeNamesCount = 0;
            
            while(resultSet.next())
            {                
                String sponsorAddress = resultSet.getString("sponsor_address");
                long rewardShareTime = resultSet.getLong("timestamp_start");
                long timePassed = currentTime - rewardShareTime;
                
                if(timePassed < 18144000000L)
                {
                    if(!activeMonth.contains(sponsorAddress))
                        activeMonth.add(sponsorAddress);
                }
                if(timePassed < 54432000000L)
                {
                    if(!activeQuarter.contains(sponsorAddress))
                        activeQuarter.add(sponsorAddress);
                }
                if(timePassed < 943488000000L)
                {
                    if(!activeYear.contains(sponsorAddress))
                        activeYear.add(sponsorAddress);
                }
                
                //Must be checked after getting the data for sponsor, we want to iterate over
                //every sponsor, but not count the sponsee data below multiple times
                String address = resultSet.getString("address");
                if(sponseesList.contains(address))
                {
                    if(!multiSponsored.contains(address))
                        multiSponsored.add(address);
                    
                    continue;
                }
                
                sponseesList.add(address);
                
                Object level = resultSet.getObject("level");
                if(level != null)
                {
                    if(!eligibleList.contains(address) && (byte) level > 4)
                        eligibleList.add(address); 
                }
                
                Object balance = resultSet.getObject("balance");
                totalBalance += balance == null ? 0 : (double) balance;
                
                Object nameObject = resultSet.getObject("name");
                if(nameObject != null && !nameObject.toString().isBlank())
                    sponseeNamesCount++;                
            }            
            
            DefaultTableModel model = (DefaultTableModel)infoTable.getModel();
            model.setRowCount(0);   
            model.addRow(new Object[]{});
            
            long lastUpdateTime = (long)dbManager.GetFirstItem("reward_shares", "timestamp", c);
            
            if(currentTime - lastUpdateTime > 604800000)
            {
                model.addRow(new Object[]{"REWARD SHARE DATA IS MORE THAN 1 WEEK OLD","You can map the latest data in the 'Update' tab"});
                model.addRow(new Object[]{});
            }
            if(rewardshareCreators != deepScannedCount)
            {
                model.addRow(new Object[]{"POSSIBLE INCOMPLETE DATA","Not all sponsors have been deep scanned"});
                model.addRow(new Object[]{});
            }
            if(currentTime - earliestScan > 604800000)
            {
                model.addRow(new Object[]{"SPONSORS DATA IS MORE THAN 1 WEEK OLD","Deep scan all sponsors now for the latest data"});
                model.addRow(new Object[]{});
            }
            double percent = ( (double) deepScannedCount / rewardshareCreators) * 100;            
            model.addRow(new Object[]{"Percentage of deep scanned sponsors",String.format("%,.2f%% (%s/%s)",
                    percent,Utilities.numberFormat(deepScannedCount),Utilities.numberFormat(rewardshareCreators))});
            if(earliestScan != Long.MAX_VALUE)
                model.addRow(new Object[]{"Earliest deep scan",Utilities.DateFormat(earliestScan)});
            if(lastScan != 0)
                model.addRow(new Object[]{"Latest deep scan",Utilities.DateFormat(lastScan)});
            
            model.addRow(new Object[]{});
            model.addRow(new Object[]{"Sponsors info:"});
            model.addRow(new Object[]{});
            model.addRow(new Object[]{"Total sponsors count",Utilities.numberFormat(sponsors)});
            model.addRow(new Object[]{"Total sponsors balance",String.format("%,.2f",totalSponsorsBalance)});
            model.addRow(new Object[]{"Total reward share creators",Utilities.numberFormat(rewardshareCreators)});
            model.addRow(new Object[]{"Total founders with sponsees",foundersCount});
            model.addRow(new Object[]{"Total founders sponsee count",Utilities.numberFormat(foundersSponseesCount)});
            model.addRow(new Object[]{"Total founders balance",String.format("%,.2f",totalFoundersBalance)});
            model.addRow(new Object[]{"Total sponsors with minted adjustment",Utilities.numberFormat(mintedAdjustmentSponsors)});
            percent = ( (double) sponsorNamesCount / rewardshareCreators) * 100;            
            model.addRow(new Object[]{"Percentage of sponsors with a registered name",String.format("%,.2f%% (%s/%s)",
                    percent,Utilities.numberFormat(sponsorNamesCount),Utilities.numberFormat(rewardshareCreators))});            
            model.addRow(new Object[]{"Number of eligible sponsors",Utilities.numberFormat(eligibleList.size())});
            percent = ( (double) sponsors / eligibleList.size()) * 100;            
            model.addRow(new Object[]{"Percentage of eligible sponsors with sponsees",String.format("%,.2f%% (%s/%s)",
                    percent,Utilities.numberFormat(sponsors),Utilities.numberFormat(eligibleList.size()))});            
            percent = ( (double) activeMonth.size() / sponsors) * 100;            
            model.addRow(new Object[]{"Percentage of active sponsors (Month)",String.format("%,.2f%% (%s/%s)",
                    percent,Utilities.numberFormat(activeMonth.size()),Utilities.numberFormat(sponsors))});            
            percent = ( (double) activeQuarter.size() / sponsors) * 100;            
            model.addRow(new Object[]{"Percentage of active sponsors (3 Months)",String.format("%,.2f%% (%s/%s)",
                    percent,Utilities.numberFormat(activeQuarter.size()),Utilities.numberFormat(sponsors))});            
            percent = ( (double) activeYear.size() / sponsors) * 100;            
            model.addRow(new Object[]{"Percentage of active sponsors (Year)",String.format("%,.2f%% (%s/%s)",
                    percent,Utilities.numberFormat(activeYear.size()),Utilities.numberFormat(sponsors))});
                   
            model.addRow(new Object[]{});
            model.addRow(new Object[]{"Sponsees info:"});
            model.addRow(new Object[]{});
            model.addRow(new Object[]{"Total sponsees count",Utilities.numberFormat(sponseesList.size())});
            model.addRow(new Object[]{"Sponsees with multiple sponsors",Utilities.numberFormat(multiSponsored.size())});
            model.addRow(new Object[]{"Total sponsees balance",String.format("%,.2f",totalBalance)});
            percent = ( (double) sponseeNamesCount / sponseesList.size()) * 100;            
            model.addRow(new Object[]{"Percentage of sponsees with a registered name",String.format("%,.2f%% (%s/%s)",
                    percent,Utilities.numberFormat(sponseeNamesCount),Utilities.numberFormat(sponseesList.size()))});
            model.addRow(new Object[]{});
            model.addRow(new Object[]{});            
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }
    
    private void showTransactionsInfo(JTable table)
    { 
        if(executorBusy)
        {
            transactionsInfoLabel.setText("Please wait for all transactions to be loaded first.");
            return;
        }
        
        String thisAddress = null;
        
        //Transactions table is the only one with multiple address columns. 
        //If we can determine that the user clicked on a specific address, we look up the
        //transactions for that address, otherwise we default to iterating columns (below)
        if(table == transactionsTable)
        {
            if(table.getSelectedColumn() >= 0)
            {
                String selectedColumn = table.getColumnName(table.getSelectedColumn());
                if(selectedColumn.equals("Creator") || selectedColumn.equals("Creator name"))
                    thisAddress = table.getValueAt(table.getSelectedRow(), 3).toString();
                
                if(selectedColumn.equals("Recipient") || selectedColumn.equals("Recipient name"))
                    thisAddress = table.getValueAt(table.getSelectedRow(), 5).toString();
            }
        }        
        if(table == sponsorInfoTable || table == sponseeInfoTable)
        {
            if(table.getSelectedRow() == 0 || table.getSelectedRow() == 1)
                thisAddress = table.getValueAt(0, 1).toString();
            //only lookup if user clicked on name or address rows
            else
                return;
        }
        if(table == flaggedSponsorsTable || table == mySponsorsTable)
            thisAddress = table.getValueAt(table.getSelectedRow(), 1).toString();
        
        if(thisAddress == null)
        {
            for(int i = 0; i < table.getColumnCount(); i++)
            {
                String columnName = table.getColumnName(i);            
                if(columnName.equals("Address") || columnName.equals("Recipient"))
                {
                    thisAddress = table.getValueAt(table.getSelectedRow(), i).toString();
                    break;
                }            
            }  
        }
        
        if(thisAddress == null)
            return;
        
        try(Connection connection = ConnectionDB.getConnection("minters"))
        {  
            DefaultTableModel model = (DefaultTableModel) transactionsTable.getModel();
            model.setRowCount(0);  
            
            String name = getNameForAddress(thisAddress, true, connection);

            transactionsInfoLabel.setText(Utilities.AllignCenterHTML("Looking up last " + maxTxSpinner.getValue() + 
                    " trade deployments and/or payments<br/>for '" + (name.isBlank() ? thisAddress : name) + "', please wait..."));
                
            SwingUtilities.invokeLater(() ->
            {
                transactionsDialog.pack();
                int x = gui.getX() + ((gui.getWidth() / 2) - (transactionsDialog.getWidth() / 2));
                int y = gui.getY() + ((gui.getHeight() / 2) - (transactionsDialog.getHeight() / 2));
                transactionsDialog.setLocation(x, y);
                transactionsDialog.setVisible(true);
            });  

            final String address = thisAddress;

            executor.submit(()->
            {  
                executorBusy = true;
                
                try
                {
                    String jsonString = Utilities.ReadStringFromURL("http://" + dbManager.socket + "/transactions/search?txType=PAYMENT"
                                      + "&txType=TRANSFER_ASSET&txType=DEPLOY_AT&address=" + address + 
                                      "&confirmationStatus=CONFIRMED&limit=" + String.valueOf(maxTxSpinner.getValue()) + "&reverse=true");    

                   JSONArray txArray = new JSONArray(jsonString);
                   JSONObject jso;
                   JSONObject namesObject = new JSONObject();

                   transactionsInfoLabel.setText(Utilities.AllignCenterHTML("Found " + txArray.length() + " trade deployments and/or payments"
                                       + "<br/>for '" + (name.isBlank() ? address : name) + "', loading transactions into table..."));

                   for(int i = 0; i < txArray.length(); i++)
                   {
                       jso = txArray.getJSONObject(i);

                       long timestamp = jso.getLong("timestamp");
                       int blockHeight = jso.getInt("blockHeight");
                       double amount = jso.getDouble("amount");
                       String type = jso.getString("type");
                       String creatorAddress = jso.getString("creatorAddress");
                       //We don't want to look any names more than once, store them in a json and only 
                       //look up a name if it doesn't exist there yet
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
                   }
                   
                   transactionsInfoLabel.setText(Utilities.AllignCenterHTML("Finished loading " + txArray.length() + " trade deployments and/or payments"
                                       + "<br/>for '" + (name.isBlank() ? address : name) + "'"));
                   
                }
                catch(ConnectException e)
                {
                    transactionsDialog.setVisible(false);
                    JOptionPane.showMessageDialog(this, "Could not connect to Qortal core, is your core/SSH tunnel active?");
                }
                catch (IOException | TimeoutException | JSONException e)
                {
                    transactionsDialog.setVisible(false);
                    BackgroundService.AppendLog(e);
                }   
                
                executorBusy = false;
            });
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }   
    }
    
    private void toggleSponsorButtons(boolean isEnabled)
    {
        deepScanSelectedButton.setEnabled(isEnabled);
        selectBulkRewardSharesButton.setEnabled(isEnabled);
        selectBulkSelfSharesButton.setEnabled(isEnabled);
        selectSimilarDurationButton.setEnabled(isEnabled);
    }
    
    private void arrangeTableColumn(JTable table, String dbTableName, String headerName)
    {  
        try(Connection c = ConnectionDB.getConnection("sponsors"))
        {
            dbManager.FillJTableOrder(dbTableName, headerName, orderKey, table,false, c);
        }
        catch (Exception ex)
        {
            BackgroundService.AppendLog(ex);
        }        
    }
    
    private void fillSponseesTable(String orderString)
    {
        String condition = "";
        if(sponsorsTable.getSelectedRow() >= 0)
        {
            String sponsorAddress = sponsorsTable.getValueAt(sponsorsTable.getSelectedRow(), 0).toString();
            condition = "where sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress);
        }

        ArrayList<String> selected = new ArrayList<>();
        if(accountCheckbox.isSelected())
            selected.add("level,blocks_minted,minted_adjustment");
        if(balanceCheckbox.isSelected())
            selected.add("balance_flag,balance");
        if(paymentsCheckbox.isSelected())
            selected.add("payments_to_sponsor,payments_count,payments");
        if(payeesCheckbox.isSelected())
            selected.add("payees_count,payees,common_payees_count,common_payees");

        String selection = "sponsor_address,sponsor_name,address,name,";
        if(selected.size() == 4)
            selection = "*";
        else
        {
            for(String s : selected)
                selection += s + ",";
            selection += "sponsors,timestamp_start,timestamp_end,duration,blockheight_start,blockheight_end";
        }
                
        try(Connection c = ConnectionDB.getConnection("sponsors"))
        {
            if(!dbManager.TableExists("sponsees", c))
                return;
            
            Statement statement = c.createStatement();
            ResultSet resultSet = statement.executeQuery("select " + selection + " from sponsees " + condition + "  " + orderString);
            sponseesTable.setModel(Utilities.BuildTableModel("", resultSet,false));
        }
        catch (Exception ex)
        {
            BackgroundService.AppendLog(ex);
        }  
    }
    
    private void sponsorsTableSelected()
    {
        if(sponsorsTable.getSelectedRow() < 0)
            return;
        
        fillSponseesTable("");
    }
    
    private void startSearch()
    {
        searchInfoLabel.setText("");
        
        if(searchInput.getText().isBlank())
            return;
        
        searchResults.clear();
        searchIndex = 0;
        
        boolean sponseesSearch = sponseeRadio.isSelected();
        if(sponseesSearch && sponsorsTable.getSelectedRow() >= 0)
        {
            sponsorsTable.clearSelection();
            fillSponseesTable("");
        }
        
        int nameColumn = sponseesSearch ? 3 : 1;
        int addressColumn = sponseesSearch ? 2 : 0;
        JTable table = sponseesSearch ? sponseesTable : sponsorsTable;   
        
        String address = searchInput.getText();
        
        //first search for name
        for(int i = 0; i < table.getRowCount(); i++)
        {
            String rowEntry = table.getValueAt(i, nameColumn).toString();
            if(caseCheckbox.isSelected())
            {
                if(rowEntry.contains(address))
                    searchResults.add(i);
            }
            else
            {                
                if(rowEntry.toLowerCase().contains(address.toLowerCase()))
                    searchResults.add(i);
            }                
        }    
        
        for (int i = 0; i < table.getRowCount(); i++)
        {
            String rowEntry = table.getValueAt(i, addressColumn).toString();

            if (caseCheckbox.isSelected())
            {
                if (rowEntry.contains(address))
                    searchResults.add(i);
            }
            else
            {
                if (rowEntry.toLowerCase().contains(address.toLowerCase()))
                    searchResults.add(i);
            }
        }
        
        if(searchResults.isEmpty()) 
        {
            searchIndex = 0;
            searchButton.setText("Search");
            searchInfoLabel.setText(Utilities.SingleQuotedString(searchInput.getText()) + " not found");            
            table.clearSelection();
            return;
        }
        else
        {     
            searchIndex = 0; 
            if(searchResults.size() == 1)
                searchButton.setText("Search");
            else
                searchButton.setText("Show next");
        }
        
        goToSearchResult(searchResults.get(0));        
    }
    
    private void goToSearchResult(int rowIndex)
    {            
        if(searchResults.size() == 1)
            searchInfoLabel.setText("Found 1 result for " +Utilities.SingleQuotedString(searchInput.getText()));  
        else
            searchInfoLabel.setText(String.format("Showing result %d out of %d for '%s'", 
                    searchIndex + 1,searchResults.size(),searchInput.getText()));
        
        boolean sponseesSearch = sponseeRadio.isSelected();
        JTable table = sponseesSearch ? sponseesTable : sponsorsTable;   
        table.setRowSelectionInterval(rowIndex, rowIndex);
        
        //scroll as close to middle as possible
        JViewport viewport = sponseesTableScrollpane.getViewport();
        Dimension extentSize = viewport.getExtentSize();   
        int visibleRows = extentSize.height/table.getRowHeight();
        
        //first scroll all the way up (scrolling up to found name was not working properly)
        table.scrollRectToVisible(new Rectangle(table.getCellRect(0, 0, true)));   
        
        int scrollToRow = rowIndex + (visibleRows / 2);        
        if(scrollToRow >= table.getRowCount())
            scrollToRow = table.getRowCount() - 1;
        
        if(rowIndex <= visibleRows / 2)
            scrollToRow = 0;
        
        table.scrollRectToVisible(new Rectangle(table.getCellRect(scrollToRow, 0, true)));          
    }
    
    private void startDeepScan(ArrayList<Object> sponsorsToScan)
    {                
        Thread thread = new Thread(()->
         {             
             updateInProgress = true;
             stopDeepScanButton.setEnabled(true);
             
             deepScanProgressBar.setVisible(true);
             deepScanProgressBar.setStringPainted(true);
             deepScanProgressBar.setString("Starting deep scan for " + sponsorsToScan.size() + " sponsors");
             deepScanProgressBar2.setVisible(true);
             deepScanProgressBar2.setStringPainted(true);
                  
            int threshold = (int)sponseeSpinner.getValue();
            balanceThreshold = threshold;
            
            try (Connection connection = ConnectionDB.getConnection("sponsors"))
            {
                if(!dbManager.TableExists("sponsors", connection) || !dbManager.TableExists("sponsees", connection))
                {
                    JOptionPane.showMessageDialog(this, "No sponsors to to deep scan\nStart mapping sponsors in the 'Update' tab");
                    LOOKUP_HALTED = true;
                }
               
                int count = 0;
                long startTime = System.currentTimeMillis();

                for(Object sponsorObject : sponsorsToScan)
                {
                    if(LOOKUP_HALTED)
                        break;

                    count++;
                    String sponsorAddress = sponsorObject.toString();
                    
                    String sponsorName = (String)dbManager.GetItemValue("sponsors", "name", "address", 
                            Utilities.SingleQuotedString(sponsorAddress), connection);
                    //add escape char for string.format if contains % (DON'T CHANGE sponsorName, it is entered in db)
                    if(sponsorName.contains("%"))
                        sponsorName = sponsorName.replace("%", "%%");
                    final String displayName = sponsorName.isBlank() ? sponsorAddress : sponsorName;
                    
                    
                    String jsonString = Utilities.ReadStringFromURL("http://" + dbManager.socket + "/addresses/" + sponsorAddress);
                    JSONObject jso = new JSONObject(jsonString);
                    int level = jso.getInt("level");
                    int blocksMinted = jso.getInt("blocksMinted");
                    double balance =  Double.parseDouble(Utilities.ReadStringFromURL(
                         "http://" + dbManager.socket + "/addresses/balance/" + sponsorAddress));  
                    
                    dbManager.ChangeValue("sponsors", "level", String.valueOf(level), "address", Utilities.SingleQuotedString(sponsorAddress), connection);
                    dbManager.ChangeValue("sponsors", "blocks_minted", String.valueOf(blocksMinted), "address", Utilities.SingleQuotedString(sponsorAddress), connection);
                    dbManager.ChangeValue("sponsors", "balance", String.valueOf(balance), "address", Utilities.SingleQuotedString(sponsorAddress), connection);
                    
                   Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                   ResultSet resultSet = statement.executeQuery(
                           "select address from sponsees where sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress));

                   JSONArray paymentsArray;

                   JSONArray latestPaymentsArray;
                   JSONObject entryObject;

                   ArrayList<String> payees = new ArrayList<>();       

                   //A json object which holds the sponsee addresses as key and a concactenated string that
                   //holds all the payees that the sponsee has transacted with as value
                   JSONObject myPayees = new JSONObject();
                   
                   int count2 = 0;
                   
                   resultSet.beforeFirst();
                    int totalIterations = 0;
                    while(resultSet.next())
                        totalIterations++;

                    resultSet.beforeFirst();
                    
                    double totalSponseesBalance = 0;
                    
                   //Iterate through all sponsees of current/selected sponsor
                   while (resultSet.next())
                   {
                       if(LOOKUP_HALTED)
                           break;

                        count2++;

                       final int current = count2;
                       final int total = totalIterations;

                        SwingUtilities.invokeLater(() ->
                        {
                            double percent = ((double) current / total) * 100;
                            deepScanProgressBar2.setValue((int) percent);
                            deepScanProgressBar2.setString(String.format("Deep scanning sponsor " + displayName + " || %.2f%% done", percent));
                        });  

                       String sponseeAddress = resultSet.getString("address");

                       //Update account and balance info
                       jsonString = Utilities.ReadStringFromURL("http://" + dbManager.socket + "/addresses/" + sponseeAddress);
                       jso = new JSONObject(jsonString);
                       level = jso.getInt("level");
                       blocksMinted = jso.getInt("blocksMinted");
                       int mintedAdjustment = jso.getInt("blocksMintedAdjustment");                       

                       balance =  Double.parseDouble(Utilities.ReadStringFromURL(
                            "http://" + dbManager.socket + "/addresses/balance/" + sponseeAddress));  
                       totalSponseesBalance += balance;
                       String balanceFlag = balance < balanceThreshold ? "*" : "";                    

                       dbManager.ChangeValue("sponsees", 
                               "level", String.valueOf(level), 
                               "sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress) + " and " +
                               "address=" + Utilities.SingleQuotedString(sponseeAddress) , connection);

                       dbManager.ChangeValue("sponsees", 
                               "blocks_minted", String.valueOf(blocksMinted), 
                               "sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress) + " and " +
                               "address=" + Utilities.SingleQuotedString(sponseeAddress) , connection);

                       dbManager.ChangeValue("sponsees", 
                               "minted_adjustment", String.valueOf(mintedAdjustment), 
                               "sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress) + " and " +
                               "address=" + Utilities.SingleQuotedString(sponseeAddress) , connection);

                       dbManager.ChangeValue("sponsees", 
                               "balance", String.valueOf(balance), 
                               "sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress) + " and " +
                               "address=" + Utilities.SingleQuotedString(sponseeAddress) , connection);

                       dbManager.ChangeValue("sponsees", 
                               "balance_flag", Utilities.SingleQuotedString(balanceFlag), 
                               "sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress) + " and " +
                               "address=" + Utilities.SingleQuotedString(sponseeAddress) , connection);


                       //Start looking for common payers/payees of payments
                       jsonString = Utilities.ReadStringFromURL("http://" + dbManager.socket + "/transactions/search?txType=PAYMENT"
                               + "&txType=TRANSFER_ASSET&address=" + sponseeAddress + 
                               "&confirmationStatus=CONFIRMED&limit=0&reverse=true");                        

                       paymentsArray = new JSONArray(jsonString);

                       payees.clear();  
                       //Used to persist all common payees of all of this sponsors' sponsees
                       //To the iteration that follows below this one
                       String payeesString = "";

                       int paymentsToSponsor = 0;

                       if(paymentsArray.length() > 0)
                       {
                           //Convert the API object to custom object which only holds timestamp, amount and recipient
                           latestPaymentsArray = new JSONArray();
                           
                           //Used to keep a list of payees and how often this sponsee has transacted with each one
                           JSONObject payeesObject = new JSONObject();

                           for(int i = 0; i < paymentsArray.length(); i++)
                           {
                               jso = paymentsArray.getJSONObject(i);

                               String payee = jso.getString("recipient");

                               entryObject = new JSONObject();
                               entryObject.put("timestamp", jso.getLong("timestamp"));
                               entryObject.put("amount", jso.getDouble("amount"));
                               entryObject.put("recipient", payee);

                               //If is outgoing tx
                               if(!payee.equals(sponseeAddress))
                               {        
                                   //If this recipients list for this sponsee does not contain this recipient
                                   if(!payees.contains(payee))
                                   {
                                       //This string will later be split using the % regex
                                       payeesString += payee + "%";   
                                       
                                       if(payeesObject.toString().length() + payee.length() + 10 < 8000)
                                            payeesObject.put(payee, "1");

                                       payees.add(payee);
                                   }
                                   else
                                   {
                                       //It's possible (but unlikely) payee was not added due to varchar size restriction (8000 chars)
                                        if(payeesObject.has(payee))
                                             payeesObject.put(payee, String.valueOf(payeesObject.getInt(payee) + 1));
                                   }
                               }

                               //Limit json object/string to 8000 chars (varchar max chars)
                               if(entryObject.toString().length() + latestPaymentsArray.toString().length() < 8000)
                                   latestPaymentsArray.put(entryObject);

                               if(payee.equals(sponsorAddress))
                                   paymentsToSponsor++;
                           }

                           //This json object has a key for sponsee of the current/selected sponsor with a list 
                           //of all of that sponsee's recipients as a string value (key = this sponsee -> value = all recipients)
                           if(!payeesString.isEmpty())
                               myPayees.put(sponseeAddress, payeesString);                            

                           if(latestPaymentsArray.length() > 0)
                               dbManager.ChangeValue("sponsees", 
                                       "payments", Utilities.SingleQuotedString(latestPaymentsArray.toString()), 
                                       "sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress) + " and " +
                                       "address=" + Utilities.SingleQuotedString(sponseeAddress) , connection);
                           
                           if(payeesObject.length() > 0)
                               dbManager.ChangeValue("sponsees", 
                                       "payees", Utilities.SingleQuotedString(payeesObject.toString()), 
                                       "sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress) + " and " +
                                       "address=" + Utilities.SingleQuotedString(sponseeAddress) , connection);
                           
                            dbManager.ChangeValue("sponsees", 
                                    "payees_count", String.valueOf(payeesObject.length()), 
                                    "sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress) + " and " +
                                    "address=" + Utilities.SingleQuotedString(sponseeAddress) , connection);
                               
                           if(paymentsArray.length() > 0)
                               dbManager.ChangeValue("sponsees", 
                                       "payments_count", String.valueOf(paymentsArray.length()), 
                                       "sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress) + " and " +
                                       "address=" + Utilities.SingleQuotedString(sponseeAddress) , connection);

                           if(paymentsToSponsor > 0)
                               dbManager.ChangeValue("sponsees", 
                                       "payments_to_sponsor", String.valueOf(paymentsToSponsor), 
                                       "sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress) + " and " +
                                       "address=" + Utilities.SingleQuotedString(sponseeAddress) , connection);

                           if(payees.size() > 0)
                           {
                               dbManager.ChangeValue("sponsees", 
                                       "payees_count", String.valueOf(payees.size()), 
                                       "sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress) + " and " +
                                       "address=" + Utilities.SingleQuotedString(sponseeAddress) , connection);                       
                           }    
                       }
                   }

                   statement = connection.createStatement();
                   resultSet = statement.executeQuery(
                           "select address from sponsees where sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress));
                   
                   ArrayList<String> commonPayers = new ArrayList<>();
                   
                   JSONObject sponsorPayeesObject = new JSONObject();        

                   //Now we do a double iteration, first we iterate through all the sponsees
                   while(resultSet.next())
                   {
                       if(LOOKUP_HALTED)
                           break;

                       String address1 = resultSet.getString("address");
                       //Skip sponsees that do not have an entry in the json object
                       if(!myPayees.has(address1))
                           continue;

                       //This is the json object that will hold the common recipients and will be inserted into the database
                       JSONObject myCommonPayees = new JSONObject();                        

                       //Get recipients from json object and split into array of recipients
                       String payeesString = myPayees.getString(address1);      
                       java.util.List<String> recipientsArray = Arrays.asList(payeesString.split("%"));
                       
                       for(String s : recipientsArray)
                       {
                           if(!sponsorPayeesObject.has(s))
                           {
                               if(sponsorPayeesObject.toString().length() + s.length() + 10 < 8000)
                                    sponsorPayeesObject.put(s, "1"); 
                           }
                           else
                               sponsorPayeesObject.put(s,String.valueOf(sponsorPayeesObject.getInt(s) + 1));
                       }                       
                       
                       Statement statement2 = connection.createStatement();
                       ResultSet resultSet2 = statement2.executeQuery(
                           "select address from sponsees where sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress));

                       //Iterate through all sponsees again and check for common recipients
                       while(resultSet2.next())
                       {
                            if(LOOKUP_HALTED)
                                break;
                            
                           String address2 = resultSet2.getString("address");

                           //if is self -> skip
                           if(address1.equals(address2))
                               continue;
                           //If other has no recipient entries -> skip
                           if(!myPayees.has(address2))
                               continue;      

                           //Get recipients from json object and split into array of recipients
                           String payeesString2 = myPayees.getString(address2);
                           String[] theirPayees = payeesString2.split("%");

                           for(String s : theirPayees)
                           {
                               //If split has returned empty string -> skip
                               if(s.isBlank())
                                   continue;

                               //If sponsee's recipients array contains the current recipient of cross-checked sponsee
                               //Add a new key to json object if not exist, or increment by 1 if it does exist
                               if(recipientsArray.contains(s))
                               {
                                   if(myCommonPayees.has(s))
                                       myCommonPayees.put(s, myCommonPayees.getInt(s) + 1);
                                   else
                                       myCommonPayees.put(s, 1);

                                   //add this sponsee to list of common payers if a common recipient was found 
                                   //and this sponsee is not yet in the list
                                   if(!commonPayers.contains(address1))
                                       commonPayers.add(address1);
                               }                                
                           } 
                       }

                       if(myCommonPayees.length() > 0)
                       {
                           dbManager.ChangeValue("sponsees", 
                                   "common_payees", Utilities.SingleQuotedString(myCommonPayees.toString()), 
                                   "sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress) + " and " +
                                   "address=" + Utilities.SingleQuotedString(address1) , connection); 

                           dbManager.ChangeValue("sponsees", 
                                   "common_payees_count", String.valueOf(myCommonPayees.length()), 
                                   "sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress) + " and " +
                                   "address=" + Utilities.SingleQuotedString(address1) , connection);        
                       }         
                   }

                    final int current = count;

                    final long timePassed = System.currentTimeMillis() - startTime;
                    long timePerSponsor = timePassed / count;
                    final long timeLeft = (sponsorsToScan.size() - count) * timePerSponsor;

                    SwingUtilities.invokeLater(() ->
                    {
                        double percent = ((double) current / sponsorsToScan.size()) * 100;
                        deepScanProgressBar.setValue((int) percent);
                        deepScanProgressBar.setString(String.format(
                                "%.2f%% done (%d out of %d) || Estimated time left : %s || Time passed : %s", 
                                percent,current,sponsorsToScan.size(),Utilities.MillisToDayHrMinSec(timeLeft),Utilities.MillisToDayHrMinSec(timePassed)));
                    });  
                    
                    if(sponsorPayeesObject.length() > 0)
                        dbManager.ChangeValue("sponsors", "common_payees", Utilities.SingleQuotedString(sponsorPayeesObject.toString()),
                           "address", Utilities.SingleQuotedString(sponsorAddress), connection); 
                    
                    dbManager.ChangeValue("sponsors", "payees_count", String.valueOf(sponsorPayeesObject.length()),
                       "address", Utilities.SingleQuotedString(sponsorAddress), connection); 
                    
                    jso = new JSONObject();
                    for(int i = 0; i < commonPayers.size(); i++)
                        if(jso.toString().length() + commonPayers.get(i).length() < 8000)
                            jso.put( String.valueOf(i+1), commonPayers.get(i));
                    
                    String jsoString = commonPayers.size() > 0 ? jso.toString() : "";
                    dbManager.ChangeValue("sponsors", "common_payers", Utilities.SingleQuotedString(jsoString), 
                           "address", Utilities.SingleQuotedString(sponsorAddress), connection); 
                    
                    dbManager.ChangeValue("sponsors", "payers_count", String.valueOf(commonPayers.size()), 
                            "address", Utilities.SingleQuotedString(sponsorAddress), connection);    
                    
                    dbManager.ChangeValue("sponsors", "sponsees_balance", String.valueOf(totalSponseesBalance), 
                           "address", Utilities.SingleQuotedString(sponsorAddress), connection); 

                    dbManager.ChangeValue("sponsors", "scanned", String.valueOf(System.currentTimeMillis()), 
                            "address", Utilities.SingleQuotedString(sponsorAddress), connection);
                    
                    dbManager.FillJTableOrder("sponsors", "scanned", "desc", sponsorsTable,false,connection); 
                    fillInfoTable();

                 }//end sponsorObject iteration   

               }
               catch (ConnectException e)
               {
                   JOptionPane.showMessageDialog(this, "Could not connect to Qortal core, is your core/SSH tunnel active?");
                   BackgroundService.AppendLog(e);
               }
               catch (Exception e)
               {
                   JOptionPane.showMessageDialog(this, "Could not finish deep scan.\n" + e.toString());
                   BackgroundService.AppendLog(e);
               }
             
            updateInProgress = false;
            LOOKUP_HALTED = false;
            stopDeepScanButton.setEnabled(false);
            deepScanProgressBar.setVisible(false);
            deepScanProgressBar.setString("");
            deepScanProgressBar.setStringPainted(false);
            deepScanProgressBar2.setVisible(false);
            deepScanProgressBar2.setString("");
            deepScanProgressBar2.setStringPainted(false);
             
            BackgroundService.AppendLog("Total API calls : " + BackgroundService.totalApiCalls);
             
         });
         thread.start();
         
    }
    
     private void startMapping()
     {  
        updateInProgress = true;
         
        Thread thread = new Thread(()->
        {   
            String updateString;
            
            updateStatusLabel.setText("Update in progress");
            
            progressBar.setVisible(true);
            
            File sponsorsFile = new File(System.getProperty("user.dir") + "/minters/sponsors.mv.db");
            if (!sponsorsFile.exists())
                ConnectionDB.CreateDatabase("sponsors");           
            
            try(Connection connection = ConnectionDB.getConnection("sponsors"))
            {       
                if (!dbManager.TableExists("reward_shares", connection))
                {
                    dbManager.CreateTable(new String[]
                    {
                        "reward_shares",
                        "timestamp","long",
                        "blockheight","int",
                        "creator_address","varchar(100)",
                        "recipient","varchar(100)",
                        "account_level","boolean",
                        "share_percent","float",
                        "signature","varchar(100)"
                    }, connection);                    
                }
                
                startMappingButton.setEnabled(false);   
                sponseeSpinner.setEnabled(false);
                stopButton.setEnabled(true);

                lookupStatusLabel.setText(Utilities.AllignCenterHTML(
                        "Fetching all reward share transactions from the blockchain<br/>"
                                + "This may take a while depending on the last time since this update was executed<br/><br/>"
                                + "Please wait..."));                     
                
                extractRewardShares(connection);  
                
                int totalRewardShares = dbManager.getRowCount("reward_shares", connection);                
                String statusString = Utilities.AllignCenterHTML(
                        String.format("Reward share update finished, total reward share transactions: %s",Utilities.numberFormat(totalRewardShares)));               
                lookupStatusLabel.setText(statusString);
                
                updateSponsors(connection);   
                
                //only allow stop on extractTransactions, extract trades should never take long enough to warrant 
                //terminating the execution
                stopButton.setEnabled(false);     

                LOOKUP_HALTED = false;
                
                updateString = "Last updated : %s";

                //Moved to end, ensures lookup continues in case of array index out of bounds exception
                dbManager.FillJTableOrder("reward_shares", "timestamp", "desc", rewardsharesTable, true,connection);
            }
            catch (ConnectException e)
            {
                lookupStatusLabel.setText(Utilities.AllignCenterHTML("Could not connect to Qortal core<br/>"
                        + "Make sure your core is online and/or your SHH tunnel is open"));
                BackgroundService.AppendLog(e);
                progressBar.setStringPainted(false);
                stopButton.setEnabled(false);
                LOOKUP_HALTED = false;
                updateString = "Last update attempt : %s";
                JOptionPane.showMessageDialog(this, "Could not finish update\n" + e.toString());
            }
            catch (Exception e)
            {
                lookupStatusLabel.setText(Utilities.AllignCenterHTML("An unexpected exception occured:<br/>"
                        + e.toString()));
                BackgroundService.AppendLog(e);
                progressBar.setStringPainted(false);
                stopButton.setEnabled(false);
                LOOKUP_HALTED = false;
                updateString = "Last update attempt : %s";
                JOptionPane.showMessageDialog(this, "Could not finish update\n" + e.toString());
            }
            
            BackgroundService.AppendLog("Total API calls : " + BackgroundService.totalApiCalls);
                        
            progressBar.setVisible(false);
            startMappingButton.setEnabled(true);
            sponseeSpinner.setEnabled(true);
            
            updateStatusLabel.setText(String.format(updateString, Utilities.DateFormat(System.currentTimeMillis()))); 
            LOOKUP_HALTED = false;
            
            sponseeProgressbar.setVisible(false);
            sponseeProgressbar.setValue(0);
            sponseeProgressbar.setString("");
            sponseeProgressbar.setStringPainted(false);
            
            updateInProgress = false;
            
            fillInfoTable();
            
            System.gc();
        });
        thread.start();                
}

private void extractRewardShares(Connection connection) throws ConnectException, TimeoutException, IOException
{                
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        progressBar.setString("Fetching reward shares from blockchain  |  Please wait...");

        //used for progress bar calculation (final int highest derives from this)
        int highestBlock = Utilities.FindChainHeight();
        int totalTxCount = dbManager.getRowCount("reward_shares", connection);

        //API call is called starting at last checked block until current chain height
        int offset = 0; //dbManager.getRowCount("all_trades", connection);
        int limit = 100; //txSlider.getValue(); 
        JSONArray txArray;
        JSONObject txObject;
        String jsonString;

        //used to keep the log from displaying errors when empty tx table is created
        boolean tableEmpty = dbManager.getRowCount("reward_shares", connection) == 0; // dbManager.GetColumn("reward_shares", "blockheight", "", "", connection).isEmpty();
        //used to know when to check the signature and for progress bar calculation  
        final int lastCheckedBlock = tableEmpty ? 0 : (int) dbManager.GetFirstItem("reward_shares", "blockheight", "timestamp", "desc", connection);

        //used to avoid duplicate database entries, to be safe we use a buffer of the 20 last signatures
        ArrayList<Object> lastSignatures = dbManager.GetColumn("reward_shares", "signature", "timestamp", "desc", 20, connection);

        int totalTxFound = 0;

        long startTime = System.currentTimeMillis();

        do
        {
            if (LOOKUP_HALTED)
                break;

            jsonString = Utilities.ReadStringFromURL("http://" + dbManager.socket + "/transactions/search?startBlock=" + (lastCheckedBlock - 1) + "&"
                    + "txType=REWARD_SHARE&txType=ACCOUNT_LEVEL&confirmationStatus=CONFIRMED&limit=" + limit + "&offset=" + offset + "&reverse=false");

            txArray = new JSONArray(jsonString);

            int blockHeight = lastCheckedBlock;

            for (int i = 0; i < txArray.length(); i++)
            {
                txObject = txArray.getJSONObject(i);
                       
                //Addresses that have an ACCOUNT_LEVEL TX will most likely always have their first reward share tx be 
                //a self share, they did not need to get sponsored. We add an entry with timestamp (needed for fillTable)
                //recipient (this address) and an account_level flag. When iterating through all potential sponsees of a sponsor
                //we check for an entry with the recipient's address and the account_level flag, if we find one we do not make
                //a sponsee entry in the sponsees table for that address
                if(txObject.getString("type").equals("ACCOUNT_LEVEL"))
                {
                    String recipient = txObject.getString("target");
                    dbManager.InsertIntoDB(new String[]
                    {
                        "reward_shares",
                        "timestamp",String.valueOf(txObject.getLong("timestamp")),
                        "recipient",Utilities.SingleQuotedString(recipient),
                        "account_level","true"
                    }, connection);
                
                    continue;
                }                

                
                long timestamp = txObject.getLong("timestamp");
                blockHeight = txObject.getInt("blockHeight");
                String creatorAddress = txObject.getString("creatorAddress");
                String recipient = txObject.getString("recipient");
                double sharePercent = txObject.getDouble("sharePercent");
                String signature = txObject.getString("signature");
                
                if(lastSignatures.contains(signature)) 
                {
                    BackgroundService.AppendLog("Existing tx signature found : " + signature);
                    continue;
                }
                
                dbManager.InsertIntoDB(new String[]
                {
                    "reward_shares",
                    "timestamp",String.valueOf(timestamp),
                    "blockheight",String.valueOf(blockHeight),
                    "creator_address",Utilities.SingleQuotedString(creatorAddress),
                    "recipient",Utilities.SingleQuotedString(recipient),
                    "share_percent",String.valueOf(sharePercent),
                    "signature",Utilities.SingleQuotedString(signature)
                }, connection);
                
                
                //can't use txArray lenght, some tx's in array might be skipped
                totalTxFound++;
                totalTxCount++;
            }

            highestBlock = highestBlock == 0 ? blockHeight : highestBlock;
            final int highest = highestBlock;
            final int blocksLeft = highest - blockHeight;
            final int height = blockHeight;
            final int txFound = totalTxFound;
            final int txCount = totalTxCount;

            SwingUtilities.invokeLater(() ->
            {
                int blocksDone = height - lastCheckedBlock;
                long timePassed = System.currentTimeMillis() - startTime;
                double blocksPerMs = ((double) blocksDone / timePassed);
                long timeLeft = (long) (blocksLeft / blocksPerMs);

                double txPerBlock = ((double) txFound / blocksDone);
                int txExpected = (int) (blocksLeft * txPerBlock);

                double percent = ((double) height / highest) * 100; //    (highest - (lastBlockHeight - lastCheckedBlock)) / highest) * 100;
                progressBar.setValue((int) percent);
                progressBar.setString(String.format("%.2f%% done  |  ", percent)
                        + "Estimated time left : " + Utilities.MillisToDayHrMinSec(timeLeft) + "  |  "
                        + "Time passed : " + Utilities.MillisToDayHrMinSec(timePassed));

                lookupStatusLabel.setText(Utilities.AllignCenterHTML("Blocks done : " + Utilities.numberFormat(height) + "  ||  "
                        + "Blocks left : " + Utilities.numberFormat(blocksLeft) + "  ||  "
                        + "Blocks done this session " + Utilities.numberFormat(blocksDone) + "  ||  "
                        + "Last block : " + Utilities.numberFormat(highest) + "<br/><br/>"
                        + "Total reward shares found : " + Utilities.numberFormat(txCount) + "  ||  "
                        + "Reward shares found this session : " + Utilities.numberFormat(txFound) + " ||  "
                        + "Estimated reward shares left to find :  " + Utilities.numberFormat(txExpected)));

                //Use own connection, due to EDT thread other connection could have been closed
                try(Connection c = ConnectionDB.getConnection("sponsors"))
                {
                    dbManager.FillJTableOrder("reward_shares", "timestamp", "desc", rewardsharesTable,true, c);               
                }
                catch (Exception e){BackgroundService.AppendLog(e);}
                
            });

            offset += limit;

        }
        while (txArray.length() > 0);

        final int txFound = totalTxFound;
        final int txCount = totalTxCount;

        SwingUtilities.invokeLater(() ->
        {
            progressBar.setValue(100);
            progressBar.setString("Found " + Utilities.numberFormat(txFound) + " transactions  |  "
                    + "Total transactions : " + Utilities.numberFormat(txCount) + "  |  "
                    + "Total lookup time : " + Utilities.MillisToDayHrMinSec(System.currentTimeMillis() - startTime));
        });

    }  
 
    private void updateSponsors(Connection sponsorsConnection) throws TimeoutException, IOException, SQLException
    {
        try(Connection namesConnection = ConnectionDB.getConnection("minters"))
        {
            
            
//            System.err.println("UNCOMMENT LTS=0 WHEN UPDATING FROM DROPPED SPONSORS/SPONSEES TABLES");
//            System.err.println("REMOVE DROP TABLE STATEMENT??");
//            dbManager.ExecuteUpdate("drop table sponsors", sponsorsConnection);
//            dbManager.ExecuteUpdate("drop table sponsees", sponsorsConnection);
            
            
            if (!dbManager.TableExists("sponsors", sponsorsConnection));
            dbManager.CreateTable(new String[]
            {
                "sponsors",
                "address", "varchar(100)",
                "name", "varchar(max)",
                "sponsee_count","int",
                "level","tinyint",
                "is_founder", "varchar(1)",
                "blocks_minted","int",
                "minted_adjustment","int",
                "balance","double",
                "sponsees_balance","double",
                "payers_count","int",
                "common_payers","varchar(max)",
                "payees_count","int",
                "common_payees","varchar(max)",
                "scanned","long"
            }, sponsorsConnection);        

            if (!dbManager.TableExists("sponsees", sponsorsConnection));
            dbManager.CreateTable(new String[]
            {
                "sponsees",
                "sponsor_address", "varchar(100)",
                "sponsor_name", "varchar(max)",
                "address", "varchar(100)",
                "name", "varchar(max)",
                "level","tinyint",
                "blocks_minted","int",
                "minted_adjustment","int",
                "balance_flag","varchar(1)",
                "balance","double",
                "payments_to_sponsor","int",
                "payments_count","int",
                "payments","varchar(max)",
                "payees_count","int",
                "payees","varchar(max)",
                "common_payees_count","int",
                "common_payees","varchar(max)",
                "sponsors", "varchar(max)",
                "timestamp_start", "long",
                "timestamp_end", "long",
                "duration","long",
                "blockheight_start", "int",
                "blockheight_end", "int"
            }, sponsorsConnection);

            Statement statement = sponsorsConnection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            ResultSet resultSet = statement.executeQuery("select distinct creator_address from reward_shares where creator_address != recipient");
            int sponsorsCount = 0;
            resultSet.beforeFirst();
            while(resultSet.next())
                sponsorsCount++;

            resultSet.beforeFirst();
            int count = 0;
            
            sponseeProgressbar.setVisible(true);
            sponseeProgressbar.setValue(0);
            sponseeProgressbar.setStringPainted(true);
            
            long startTime = System.currentTimeMillis();
            
            Object lastTimeStampObject = dbManager.GetFirstItem("reward_shares", "timestamp", "timestamp", "desc", sponsorsConnection);
            long lastTimestamp = lastTimeStampObject == null ? 0 : (long) lastTimeStampObject;

            //Iterate through the DISTINCT sponsor addresses returned by above query
            while(resultSet.next())
            {                
                count++;

                String creatorAddress = resultSet.getString("creator_address"); 
                String sponsorName = getNameForAddress(creatorAddress,true, namesConnection);               

                int sponseesAdded = updateSponsees(creatorAddress,sponsorName,lastTimestamp, sponsorsConnection,namesConnection);
                
                //Only true if lookup was halted
                if(sponseesAdded < 0)
                    return;
                
                //check if sponsor entry exists, if it doesn't we create a new entry, otherwise we update the existing one
                Object sponsorEntry = dbManager.tryGetItemValue(
                        "sponsors", "is_founder", "address", Utilities.SingleQuotedString(creatorAddress), sponsorsConnection);
                
                String jsonString = Utilities.ReadStringFromURL("http://" + dbManager.socket + "/addresses/" + creatorAddress);
                JSONObject sponsorObject = new JSONObject(jsonString);
                int level = sponsorObject.getInt("level");
                int blocksMinted = sponsorObject.getInt("blocksMinted");
                int mintedAdjustment = sponsorObject.getInt("blocksMintedAdjustment");
                boolean isFounder = sponsorObject.getInt("flags") == 1;
                
               //could use any field to verify is sponsor entry exists, using founder cause it's the smallest
                if(sponsorEntry == null)
                {
                    String founderFlag = isFounder ? "F" : "";

                    //includeZeroCount allows for sponsors with only unsuccessful sponsorship attempts to be entered into sponsors list
                    if(includeZeroCount || sponseesAdded > 0)
                    {
                        dbManager.InsertIntoDB(new String[]
                        {
                            "sponsors",
                            "address", Utilities.SingleQuotedString(creatorAddress),
                            "name", Utilities.SingleQuotedString(sponsorName),
                            "level",String.valueOf(level),
                            "sponsee_count", String.valueOf(sponseesAdded),
                            "is_founder", Utilities.SingleQuotedString(founderFlag),
                            "blocks_minted",String.valueOf(blocksMinted),
                            "minted_adjustment",String.valueOf(mintedAdjustment)
                        }, sponsorsConnection);
                    }
                        
                }
                else
                {
                    int existingSponsees = (int) dbManager.GetFirstItem("sponsors", "sponsee_count", sponsorsConnection);                    
                    
                    dbManager.ChangeValue("sponsors", "sponsee_count", String.valueOf((existingSponsees + sponseesAdded)), "address",
                            Utilities.SingleQuotedString(creatorAddress), sponsorsConnection);
                    dbManager.ChangeValue("sponsors", "name", Utilities.SingleQuotedString(sponsorName), "address",
                            Utilities.SingleQuotedString(creatorAddress), sponsorsConnection);  
                    dbManager.ChangeValue("sponsors", "level", String.valueOf((level)), "address",
                            Utilities.SingleQuotedString(creatorAddress), sponsorsConnection);
                    dbManager.ChangeValue("sponsors", "blocks_minted", String.valueOf((blocksMinted)), "address",
                            Utilities.SingleQuotedString(creatorAddress), sponsorsConnection);
                    dbManager.ChangeValue("sponsors", "minted_adjustment", String.valueOf((mintedAdjustment)), "address",
                            Utilities.SingleQuotedString(creatorAddress), sponsorsConnection);                  
                }

                final int sponsorsFound = sponsorsCount;
                final int current = count;
                
                final long timePassed = System.currentTimeMillis() - startTime;
                long timePerSponsor = timePassed / count;
                final long timeLeft = (sponsorsFound - count) * timePerSponsor;

                SwingUtilities.invokeLater(() ->
                {
                    double percent = ((double) current / sponsorsFound) * 100;
                    progressBar.setValue((int) percent);
                    progressBar.setString(String.format(
                            "Updating sponsor data || %.2f%% done (%d out of %d) || Estimated time left : %s || Time passed : %s", 
                            percent,current,sponsorsFound,Utilities.MillisToDayHrMinSec(timeLeft),Utilities.MillisToDayHrMinSec(timePassed)));
                });               

            }
            /*One problem with the self share check, is that the sponsorship could have been terminated and the 
            sponsee still may have been sponsored by someone else later on, so someone could show up as a 
            sponsee for multiple sponsors in the list. For the self sponsors this shouldn't be a problem, but for 
            mapping sponsor/sponsee relationships it introduces inaccuracies.*/
            
            sponseeProgressbar.setVisible(false);     
            
            ArrayList<String> checkedSponsees = new ArrayList<>();
            int currentSponsor  = 0;
            //Get a list of all sponsors
            ArrayList<Object> sponsorsList = dbManager.GetColumn("sponsors", "address", "", "", sponsorsConnection);
            for(Object sponsorObject : sponsorsList)
            {
                currentSponsor++;
                
                String sponsorAdddress = sponsorObject.toString();
                
                //Get the sponsees from current sponsor
                statement = sponsorsConnection.createStatement();
                resultSet = statement.executeQuery(
                        "select address from sponsees where sponsor_address=" + Utilities.SingleQuotedString(sponsorAdddress));
                
                Statement statement2; 
                ResultSet resultSet2;
                
                //Iterate through all the sponsees for current sponsor
                while(resultSet.next())
                {
                    String sponseeAddress = resultSet.getString("address");
                    
                    if(checkedSponsees.contains(sponseeAddress))
                        continue;
                    
                    checkedSponsees.add(sponseeAddress);
                    
                    //Get all sponsors with this sponsee
                    statement2 = sponsorsConnection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                    resultSet2 = statement2.executeQuery(
                            "select sponsor_address,sponsor_name,timestamp_start,blockheight_start from sponsees where address=" + Utilities.SingleQuotedString(sponseeAddress));
                 
                    
                    resultSet2.beforeFirst();
                    count = 0;
                    //Iterate through every sponsor that has this sponsee
                    while(resultSet2.next())
                        count++;
                    
                    if(count > 1)
                    {
                        JSONObject jso = new JSONObject();
                        
                        resultSet2.beforeFirst();
                        
                        long earliestTimestamp = Long.MAX_VALUE;
                        int earliestBlockHeight = Integer.MAX_VALUE;
                         
                        int currentEntry = 0;
                        while(resultSet2.next())
                        {
                            if(LOOKUP_HALTED)
                                return;
                            
                            currentEntry++;
                            String sponsorName = resultSet2.getString("sponsor_name");
                            if(sponsorName.contains("'"))
                                sponsorName = sponsorName.replace("'", "''");
                            
                            jso.put("address_" + currentEntry, resultSet2.getString("sponsor_address"));
                            jso.put("name_" + currentEntry, sponsorName);                            
                            
                            if(earliestTimestamp > resultSet2.getLong("timestamp_start"))
                                earliestTimestamp = resultSet2.getLong("timestamp_start");
                            if(earliestBlockHeight > resultSet2.getInt("blockheight_start"))
                                earliestBlockHeight = resultSet2.getInt("blockheight_start");
                        }
                        
                        long timestampEnd = (long)dbManager.GetFirstItem("sponsees", "timestamp_end", 
                                "where address=" + Utilities.SingleQuotedString(sponseeAddress), "timestamp_end", "desc", sponsorsConnection);
                       
                        //Enter the sponsors json object in the sponsors column for all entries of this sponsee in sponsees table
                        dbManager.ChangeValue("sponsees", "sponsors", Utilities.SingleQuotedString(jso.toString()), 
                                "address" ,Utilities.SingleQuotedString(sponseeAddress), sponsorsConnection);
                        
                        //If a sponsee has multiple sponsors, set the timestamp_start and blockheight_start values to the ones
                        //for the sponsor with the earliest (first) rewardshare with this sponsee as recipient
                        dbManager.ChangeValue("sponsees", "timestamp_start", String.valueOf(earliestTimestamp), 
                                "address" ,Utilities.SingleQuotedString(sponseeAddress), sponsorsConnection);                        
                        dbManager.ChangeValue("sponsees", "blockheight_start", String.valueOf(earliestBlockHeight), 
                                "address" ,Utilities.SingleQuotedString(sponseeAddress), sponsorsConnection);                 
                        dbManager.ChangeValue("sponsees", "duration", String.valueOf(timestampEnd - earliestTimestamp), 
                                "address" ,Utilities.SingleQuotedString(sponseeAddress), sponsorsConnection);
                    }
                }
                
                int current = currentSponsor;
                
                SwingUtilities.invokeLater(() ->
                {
                    double percent = ((double) current / sponsorsList.size()) * 100;
                    progressBar.setValue((int) percent);
                    progressBar.setString(String.format(
                            "Looking for duplicate sponsors || %.2f%% done (%d out of %d)", 
                            percent,current,sponsorsList.size()));
                });   
                
            }

            dbManager.FillJTableOrder("sponsors", "sponsee_count","desc",sponsorsTable,false, sponsorsConnection);
            fillSponseesTable("");
        }
        catch (Exception e)
        {
            throw e;
        }
    }

    private int updateSponsees(String sponsorAddress, String sponsorName,long lastTimestamp,Connection sponsorsConnection, Connection namesConnection) throws TimeoutException, IOException, SQLException
    {
        ArrayList<String> sponseesList = new ArrayList<>();           
        
        
//        System.err.println("UNCOMMENT LTS=0 WHEN UPDATING FROM DROPPED SPONSORS/SPONSEES TABLES");
//        lastTimestamp = 0;
                

        Statement sponsorStatement = sponsorsConnection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
        ResultSet sponsorResultSet = sponsorStatement.executeQuery(
                "select creator_address,recipient,timestamp,blockheight,share_percent from reward_shares "
             + "where creator_address = " + Utilities.SingleQuotedString(sponsorAddress) 
             + " and timestamp > " + String.valueOf(lastTimestamp));
        
        sponsorResultSet.beforeFirst();
        int rowCount = 0;
        while(sponsorResultSet.next())
            rowCount++;
        
        int count = 0;
        sponsorResultSet.beforeFirst();
        while(sponsorResultSet.next())
        {
            count++;
            String sponseeAddress = sponsorResultSet.getString("recipient");

            if (!sponsorAddress.equals(sponseeAddress))
            {
                //Check if sponsee has created a self-share to determine whether sponsorship was ended with success
                if (!sponseesList.contains(sponseeAddress))
                {                    
                    Statement accountLevelStatement = sponsorsConnection.createStatement();
                    ResultSet accountLevelResultSet = accountLevelStatement.executeQuery(
                            "select recipient,account_level from reward_shares where recipient=" + Utilities.SingleQuotedString(sponseeAddress) + 
                                    " and account_level=true");
                    
                    //If an address has an account_level transaction (pre-levelled) its first reward share tx will most likely be a self
                    //share. It will not be a sponsee, but some of these do have reward share tx's as a recipient. In any  case
                    //we can ommit them from the sponsees table.
                    if(accountLevelResultSet.next())
                        continue;
                    
                    Statement duplicatesStatement = sponsorsConnection.createStatement();
                    ResultSet duplicatesResultSet = duplicatesStatement.executeQuery(
                            "select sponsor_address,address,name from sponsees where"
                                    + " sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress) 
                                    + " and address=" + Utilities.SingleQuotedString(sponseeAddress));
                    if(duplicatesResultSet.next())
                    {                                                
                        //check if new name was registered if name blank
                        String nameEntry = duplicatesResultSet.getString("name");
                        if(nameEntry.isBlank())
                        {
                            String checkedName = getNameForAddress(sponseeAddress,true, namesConnection);
                            if(!checkedName.isBlank())
                                dbManager.ChangeValue("sponsees", 
                                        "name", Utilities.SingleQuotedString(checkedName), 
                                        "sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress) + " and " +
                                        "address=" + Utilities.SingleQuotedString(sponseeAddress) , sponsorsConnection);
                        }
                        
                        //move to next sponsee iteration if entry was found
                        continue;
                    }
                    
                    //If no existing sponsee entry found, check for selfshare and add new entry if found
                    
                    //order by timestamp to ensure first selfshare is used as sponsor success timestamp
                    Statement sponseeStatement = sponsorsConnection.createStatement();
                    ResultSet sponseeResultSet = sponseeStatement.executeQuery(
                            "select creator_address,timestamp,blockheight from reward_shares "
                                    + "where recipient=" + Utilities.SingleQuotedString(sponseeAddress) + " order by timestamp asc");
                    
                    while(sponseeResultSet.next())
                    {
                        if(LOOKUP_HALTED)
                            break;
                        
                        if (sponseeAddress.equals(sponseeResultSet.getString("creator_address")))
                        {
                            sponseesList.add(sponseeAddress);

                            long timestampStart = sponsorResultSet.getLong("timestamp");
                            int blockheightStart = sponsorResultSet.getInt("blockHeight");
                            long timestampEnd = sponseeResultSet.getLong("timestamp");
                            int blockheightEnd = sponseeResultSet.getInt("blockHeight");
                            String sponseeName = getNameForAddress(sponseeAddress,true, namesConnection);           
                            long duration = timestampEnd - timestampStart;

                            dbManager.InsertIntoDB(new String[]
                            {
                                "sponsees",
                                "sponsor_address", Utilities.SingleQuotedString(sponsorAddress),
                                "sponsor_name", Utilities.SingleQuotedString(sponsorName),
                                "address", Utilities.SingleQuotedString(sponseeAddress),
                                "name", Utilities.SingleQuotedString(sponseeName),
                                "timestamp_start", String.valueOf(timestampStart),
                                "timestamp_end", String.valueOf(timestampEnd),
                                "duration",String.valueOf(duration),
                                "blockheight_start", String.valueOf(blockheightStart),
                                "blockheight_end", String.valueOf(blockheightEnd)
                            }, sponsorsConnection);

                            //exit sponsee resultset iteration if self share was found
                            break;
                        }
                    }
                    
                    final int rows = rowCount;
                    final int current = count;
                    
                    final String displayName;
                    if(sponsorName.isBlank())
                        displayName = sponsorAddress;
                    else
                    {
                        //add escape char for string.format if contains % (DON'T CHANGE sponsorName, it is entered in db)
                        String thisSponsor = sponsorName;
                        if(thisSponsor.contains("%"))
                            thisSponsor = thisSponsor.replace("%", "%%");
                        
                        displayName = thisSponsor;
                    }
                    
                    SwingUtilities.invokeLater(() ->
                    {
                        double percent = ((double) current / rows) * 100;     
                        sponseeProgressbar.setValue((int) percent);
                        sponseeProgressbar.setString(String.format("Updating sponsees for " + displayName + " || %.2f%% done", percent));
                    });
                }
            }
            
            if(LOOKUP_HALTED)
                return -1;
        }
        
        return sponseesList.size();
        
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
    
    private void selectByBulk(String column,int threshold)
    {
        if(sponsorsTable.getSelectedRow() < 0)
        {
            JOptionPane.showMessageDialog(this, "No sponsor selected");
            return;
        }
        
        sponseesTable.clearSelection();
        
        String sponsorAddress = sponsorsTable.getValueAt(sponsorsTable.getSelectedRow(), 0).toString();
        
        try(Connection c = ConnectionDB.getConnection("sponsors"))
        {
            ArrayList<String> results = new ArrayList<>();
            
            Statement statement = c.createStatement();
            ResultSet resultSet = statement.executeQuery(
                    "select " + column + ",address from sponsees where sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress) + 
                    " order by " + column + " desc");
            
            class Pair
            {
                public String address;
                public long value;
                
                public Pair(String address, long value)
                {
                    this.address = address;
                    this.value = value;
                }
            }
            ArrayList<Pair> addresses = new ArrayList<>();            
            
            while(resultSet.next())
                addresses.add(new Pair(resultSet.getString("address"),resultSet.getInt(column)));            
            
            for(int i = 0; i < addresses.size() - 1; i++)
            {
                int blockHeight = (int)addresses.get(i).value;
                int nextBlockHeight = (int)addresses.get(i + 1).value;

                if(blockHeight - nextBlockHeight <= threshold)
                {
                    results.add(addresses.get(i).address);
                    //no need to check prev if this row is marked for selection
                    continue;
                }

                if(i == 0)
                    continue;

                int prevBlockHeight = (int)addresses.get(i -1).value;
                if(prevBlockHeight - blockHeight <= threshold)
                    results.add(addresses.get(i).address);
            }       
           
            for(int i = 0 ; i < sponseesTable.getRowCount(); i++)
            {
                String sponsee = sponseesTable.getValueAt(i, 2).toString();
                
                if(results.contains(sponsee))
                    sponseesTable.getSelectionModel().addSelectionInterval(i, i);
            } 
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }        
    }
    
    private void showSponseeInfo()
    {
        try(Connection c = ConnectionDB.getConnection("sponsors"))
        {            
            try(Connection namesConnection = ConnectionDB.getConnection("minters"))
            {
                String sponseeAddress = sponseesTable.getValueAt(sponseesTable.getSelectedRow(), 2).toString();
                String sponsorAddress = sponseesTable.getValueAt(sponseesTable.getSelectedRow(), 0).toString();
                
                Statement statement = c.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "select sponsors,payments,common_payees,payees from sponsees where address=" + 
                        Utilities.SingleQuotedString(sponseeAddress) + " and sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress));

                while(resultSet.next())
                {
                    JSONArray paymentsArray;
                    JSONObject jso;

                    String jsonString = resultSet.getString("sponsors");                         
                    var model = (DefaultTableModel) mySponsorsTable.getModel();
                    model.setRowCount(0);  

                    if(jsonString != null && !jsonString.isBlank())
                    {
                        jso = new JSONObject(jsonString);
                        int count = 1;
                        while(true)
                        {
                            if(!jso.has("address_" + count))
                                break;

                            model.addRow(new Object[]{jso.getString("name_" + count), jso.getString("address_" + count)});   
                            count++;
                        }
                    }
                    else
                    {          
                        model.addRow(new Object[]
                        {
                            sponseesTable.getValueAt(sponseesTable.getSelectedRow(), 1),
                            sponseesTable.getValueAt(sponseesTable.getSelectedRow(), 0)
                        });
                    }   

                    ArrayList<String> columns = new ArrayList<>();
                    for(int i = 0; i < sponseesTable.getColumnCount(); i++)
                        columns.add(sponseesTable.getColumnName(i));

                    model = (DefaultTableModel) sponseeInfoTable.getModel();
                    model.setRowCount(0);  

                    model.addRow(new Object[]{"Sponsee address", sponseeAddress});
                    model.addRow(new Object[]{"Sponsee name", sponseesTable.getValueAt(sponseesTable.getSelectedRow(), 3)});                     
                    model.addRow(new Object[]{"Started sponsorship", sponseesTable.getValueAt(sponseesTable.getSelectedRow(), columns.indexOf("TIMESTAMP_START"))});
                    model.addRow(new Object[]{"Ended sponsorship", sponseesTable.getValueAt(sponseesTable.getSelectedRow(), columns.indexOf("TIMESTAMP_END"))});
                    model.addRow(new Object[]{"Sponsorship duration", sponseesTable.getValueAt(sponseesTable.getSelectedRow(), columns.indexOf("DURATION"))});
                            
                    Object level = dbManager.GetItemValue("sponsees", "level","where address=" + Utilities.SingleQuotedString(sponseeAddress) + 
                                    " and sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress), c);
                    boolean deepScanned = level != null;

                    if(!deepScanned)
                        model.addRow(new Object[]{"Deep scanned", "false"});
                    else
                    {
                        model.addRow(new Object[]{"Deep scanned", "true"});
                        model.addRow(new Object[]{"Account level", (byte) level});
                        int blocksMinted = (int) dbManager.GetItemValue("sponsees", "blocks_minted", "where address=" + Utilities.SingleQuotedString(sponseeAddress) + 
                                    " and sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress), c);
                        model.addRow(new Object[]{"Blocks minted", Utilities.numberFormat(blocksMinted)});
                        
                        int mintedAdj = (int) dbManager.GetItemValue("sponsees", "minted_adjustment", "where address=" + Utilities.SingleQuotedString(sponseeAddress) + 
                                    " and sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress), c);
                        model.addRow(new Object[]{"Blocks minted adjustment", Utilities.numberFormat(mintedAdj)});     
                        
                        double balance = (double) dbManager.GetItemValue("sponsees", "balance", "where address=" + Utilities.SingleQuotedString(sponseeAddress) + 
                                    " and sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress), c);
                        model.addRow(new Object[]{"Balance", String.format("%,.2f", balance)});
                        
                        Object payments =  dbManager.GetItemValue("sponsees", "payments_count", "where address=" + Utilities.SingleQuotedString(sponseeAddress) + 
                                    " and sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress), c);                 
                        model.addRow(new Object[]{"Payments made/received", payments == null ? "0" : payments});
                        
                        Object paymentsToSponsor = dbManager.GetItemValue("sponsees", "payments_to_sponsor","where address=" + Utilities.SingleQuotedString(sponseeAddress) + 
                                    " and sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress), c);
                        model.addRow(new Object[]{"Payments to sponsor", paymentsToSponsor == null ? "0" : paymentsToSponsor});
                        
                        Object payeesCount = dbManager.GetItemValue("sponsees", "payees_count", "where address=" + Utilities.SingleQuotedString(sponseeAddress) + 
                                    " and sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress), c);                
                        model.addRow(new Object[]{"Payees count", payeesCount == null ? "0" : payeesCount});
                        
                        Object commonPayees = dbManager.GetItemValue("sponsees", "common_payees_count", "where address=" + Utilities.SingleQuotedString(sponseeAddress) + 
                                    " and sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress), c);      
                        model.addRow(new Object[]{"Payees in common", commonPayees == null ? "0" : commonPayees});
                    }

                    model = (DefaultTableModel) paymentsTable.getModel();
                    model.setRowCount(0);                 

                    jsonString = resultSet.getString("payments");    
                    if(jsonString != null && !jsonString.isBlank())
                    {
                        paymentsArray = new JSONArray(jsonString);
                        for(int i = 0; i < paymentsArray.length(); i++)
                        {
                            jso = paymentsArray.getJSONObject(i);

                            String recipient = jso.getString("recipient");
                            String type = recipient.equals(sponseeAddress) ? "Received" : "Sent";
                            String name = getNameForAddress(recipient,false, namesConnection);

                            model.addRow(new Object[]{
                                Utilities.DateFormat(jso.getLong("timestamp")),
                                String.format("%,.2f", jso.getDouble("amount")),
                                recipient,
                                name,
                                type
                            });                               
                        }                  
                    }
                    
                    class Entry
                    {
                        String address;
                        String name;
                        int count;
                        public Entry(String address,String name,int count){this.address = address;this.name = name;this.count=count;}
                    }
                    ArrayList<Entry> entries = new ArrayList<>();
                    
                    model = (DefaultTableModel) payeesTable.getModel();
                    model.setRowCount(0);                              
                            
                    jsonString = resultSet.getString("payees");     
                    if(jsonString != null && !jsonString.isBlank())
                    {
                        jso = new JSONObject(jsonString);

                        Iterator<String> keys = jso.keys();
                        while(keys.hasNext())
                        {
                            String key = keys.next();
                            String name = getNameForAddress(key,false, namesConnection);
                            entries.add(new Entry(key,name,jso.getInt(key)));
                        }   
                    }  
                    Collections.sort(entries, (Entry s1, Entry s2) -> Integer.compare(s2.count, s1.count));
                    
                    for(Entry e : entries)
                    {
                        model.addRow(new Object[]
                        {
                            e.address, e.name, e.count
                        });
                    }
                    
                    entries.clear();

                    model = (DefaultTableModel) commonPayeesTable.getModel();
                    model.setRowCount(0);                              
                            
                    jsonString = resultSet.getString("common_payees");     
                    if(jsonString != null && !jsonString.isBlank())
                    {
                        jso = new JSONObject(jsonString);

                        Iterator<String> keys = jso.keys();
                        while(keys.hasNext())
                        {
                            String key = keys.next();
                            String name = getNameForAddress(key,false, namesConnection);
                            entries.add(new Entry(key,name,jso.getInt(key)));
                        }   
                    }
                    Collections.sort(entries, (Entry s1, Entry s2) -> Integer.compare(s2.count, s1.count));
                    
                    for(Entry e : entries)
                    {
                        model.addRow(new Object[]
                        {
                            e.address, e.name, e.count
                        });
                    }                    
                }
            }
            catch (Exception e)
            {
                BackgroundService.AppendLog(e);
            }
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
        
         SwingUtilities.invokeLater(() ->
        {
            int x = gui.getX() + ((gui.getWidth() / 2) - (sponseeInfoDialog.getWidth() / 2));
            int y = gui.getY() + ((gui.getHeight() / 2) - (sponseeInfoDialog.getHeight() / 2));
            sponseeInfoDialog.setLocation(x, y);
            sponseeInfoDialog.setVisible(true);
        });         
    }
    
    private void showSponsorInfo()
    {
        try(Connection c = ConnectionDB.getConnection("sponsors"))
        {
            String sponsorAddress = sponsorsTable.getValueAt(sponsorsTable.getSelectedRow(), 0).toString(); 
            String sponsorName = sponsorsTable.getValueAt(sponsorsTable.getSelectedRow(), 1).toString(); 
            
            try(Connection namesConnection = ConnectionDB.getConnection("minters"))
            { 

                var model = (DefaultTableModel) sponsorInfoTable.getModel();
                model.setRowCount(0); 

                boolean isFounder = sponsorsTable.getValueAt(sponsorsTable.getSelectedRow(), 4).toString().equals("F");

                model.addRow(new Object[]{"Sponsor address", sponsorAddress});
                model.addRow(new Object[]{"Sponsor name", sponsorsTable.getValueAt(sponsorsTable.getSelectedRow(), 1)});      
                model.addRow(new Object[]{"Number of sponsees", sponsorsTable.getValueAt(sponsorsTable.getSelectedRow(), 2)}); 
                model.addRow(new Object[]{"Level", sponsorsTable.getValueAt(sponsorsTable.getSelectedRow(), 3)}); 
                model.addRow(new Object[]{"Is a founder",isFounder});  
                model.addRow(new Object[]{"Blocks minted", Utilities.numberFormat((int)sponsorsTable.getValueAt(sponsorsTable.getSelectedRow(), 5))});   
                model.addRow(new Object[]{"Blocks minted adjustment", Utilities.numberFormat((int)sponsorsTable.getValueAt(sponsorsTable.getSelectedRow(), 6))});  
                
                Object balance = dbManager.GetItemValue("sponsors", "balance","where address=" + Utilities.SingleQuotedString(sponsorAddress), c);
                boolean deepScanned = balance != null;
                
                if(!deepScanned)
                {                    
                    model.addRow(new Object[]{"Deep scanned", "False"});  
                }
                else
                {
                    model.addRow(new Object[]{"Deep scanned", "True"});
                    double sponseesBalance = (double)sponsorsTable.getValueAt(sponsorsTable.getSelectedRow(), 8);
                    model.addRow(new Object[]{"Balance", String.format("%,.2f", (double)balance)});  
                    model.addRow(new Object[]{"Total sponsees balance", String.format("%,.2f", sponseesBalance)});  
                    model.addRow(new Object[]{"Common payers count", 
                        Utilities.numberFormat((int)sponsorsTable.getValueAt(sponsorsTable.getSelectedRow(), 9))});   
                    model.addRow(new Object[]{"Common payees count", 
                        Utilities.numberFormat((int)sponsorsTable.getValueAt(sponsorsTable.getSelectedRow(), 11))});     
                }

                model = (DefaultTableModel) commonPayersTable.getModel();
                model.setRowCount(0); 

                JSONObject jso;

                Object jsoObject = sponsorsTable.getValueAt(sponsorsTable.getSelectedRow(), 10);
                String jsonString = jsoObject == null ? "" : jsoObject.toString();            

                if(!jsonString.isBlank())
                {
                    jso = new JSONObject(jsonString);
                    int count = 1;
                    while(true)
                    {
                        if(!jso.has(String.valueOf(count)))
                            break;

                        String address = jso.getString(String.valueOf(count));
                        String name = getNameForAddress(address, false, namesConnection);
                        model.addRow(new Object[]{address, name});   
                        count++;
                    }
                }
                
                model = (DefaultTableModel) payeesTableSponsor.getModel();
                model.setRowCount(0); 
                jsoObject = sponsorsTable.getValueAt(sponsorsTable.getSelectedRow(), 12);
                jsonString = jsoObject == null ? "" : jsoObject.toString();            

                if(!jsonString.isBlank())
                {
                    class Entry
                    {
                        String address;
                        String name;
                        int count;
                        public Entry(String address,String name,int count){this.address = address;this.name = name;this.count=count;}
                    }
                    ArrayList<Entry> entries = new ArrayList<>();
                    jso = new JSONObject(jsonString);
                    
                     Iterator<String> keys = jso.keys();
                    while (keys.hasNext())
                    {
                        String key = keys.next();
                        String name = getNameForAddress(key, false, namesConnection);
                        entries.add(new Entry(key,name,jso.getInt(key)));
                    }
                    Collections.sort(entries, (Entry s1, Entry s2) -> Integer.compare(s2.count, s1.count));
                    
                    for(Entry e : entries)
                    {
                        model.addRow(new Object[]
                        {
                            e.address, e.name, e.count
                        });
                    }                    
                }    
            }
            catch (Exception e)
            {
                BackgroundService.AppendLog(e);
            } 
            
            loadSponsorshipsChart(sponsorAddress,sponsorName);            
            
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
        
         SwingUtilities.invokeLater(() ->
        {
            int x = gui.getX() + ((gui.getWidth() / 2) - (sponsorInfoDialog.getWidth() / 2));
            int y = gui.getY() + ((gui.getHeight() / 2) - (sponsorInfoDialog.getHeight() / 2));
            sponsorInfoDialog.setLocation(x, y);
            sponsorInfoDialog.setVisible(true);
        });         
    }
    
    private void loadSponsorshipsChart(String sponsorAddress,String sponsorName)
    {
        try(Connection c = ConnectionDB.getConnection("sponsors"))
        {
            chartPlaceHolder.removeAll();
            Statement statement = c.createStatement();
            ResultSet resultSet = statement.executeQuery(
                    "select timestamp_start from sponsees where sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress));

            String type = "week";
            for(Enumeration<AbstractButton> buttons = buttonGroup.getElements(); buttons.hasMoreElements();)
            {
                AbstractButton button = buttons.nextElement();
                if(button.isSelected())
                {
                    type = button.getActionCommand();
                    break;
                }
            }    
            
            String displayName = sponsorName.isBlank() ? sponsorAddress : sponsorName;
            chartPlaceHolder.add(chartMaker.createSponsorshipsChartPanel(resultSet,displayName,type));
            chartPlaceHolder.revalidate();
            chartPlaceHolder.repaint();
        }
        catch (Exception e)
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

        buttonGroup = new javax.swing.ButtonGroup();
        flaggedSponsorsDialog = new javax.swing.JDialog();
        jScrollPane1 = new javax.swing.JScrollPane();
        flaggedSponsorsTable = new javax.swing.JTable();
        sponseeInfoDialog = new javax.swing.JDialog();
        jTabbedPane2 = new javax.swing.JTabbedPane();
        sponseeInfoTab = new javax.swing.JPanel();
        jScrollPane4 = new javax.swing.JScrollPane();
        mySponsorsTable = new javax.swing.JTable();
        jScrollPane2 = new javax.swing.JScrollPane();
        sponseeInfoTable = new javax.swing.JTable();
        paymentsTab = new javax.swing.JScrollPane();
        paymentsTable = new javax.swing.JTable();
        payeesTab = new javax.swing.JPanel();
        payeesScrollpane = new javax.swing.JScrollPane();
        payeesTable = new javax.swing.JTable();
        jLabel11 = new javax.swing.JLabel();
        commonPayeesTab = new javax.swing.JPanel();
        commonPayeesScrollpane = new javax.swing.JScrollPane();
        commonPayeesTable = new javax.swing.JTable();
        jLabel9 = new javax.swing.JLabel();
        sponsorInfoDialog = new javax.swing.JDialog();
        jTabbedPane3 = new javax.swing.JTabbedPane();
        sponsorInfoTab = new javax.swing.JPanel();
        jScrollPane6 = new javax.swing.JScrollPane();
        sponsorInfoTable = new javax.swing.JTable();
        payersTab = new javax.swing.JPanel();
        commonPayersScrollpane = new javax.swing.JScrollPane();
        commonPayersTable = new javax.swing.JTable();
        jLabel10 = new javax.swing.JLabel();
        payeesTabSponsor = new javax.swing.JPanel();
        payeesScrollpaneSponsor = new javax.swing.JScrollPane();
        payeesTableSponsor = new javax.swing.JTable();
        jLabel12 = new javax.swing.JLabel();
        chartsTab = new javax.swing.JPanel();
        chartPlaceHolder = new javax.swing.JPanel();
        minuteRadio = new javax.swing.JRadioButton();
        hourRadio = new javax.swing.JRadioButton();
        dayRadio = new javax.swing.JRadioButton();
        weekRadio = new javax.swing.JRadioButton();
        monthRadio = new javax.swing.JRadioButton();
        transactionsDialog = new javax.swing.JDialog();
        transactionsMainPanel = new javax.swing.JPanel();
        transactionsTableScrollpane = new javax.swing.JScrollPane();
        transactionsTable = new javax.swing.JTable();
        transactionsInfoLabel = new javax.swing.JLabel();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        sponsorsTab = new javax.swing.JSplitPane();
        topPane = new javax.swing.JTabbedPane();
        searchAndSelectTab = new javax.swing.JScrollPane();
        searchAndSelectTab.getVerticalScrollBar().setUnitIncrement(10);
        menuPanel = new javax.swing.JPanel();
        deselectButton = new javax.swing.JButton();
        accountCheckbox = new javax.swing.JCheckBox();
        balanceCheckbox = new javax.swing.JCheckBox();
        paymentsCheckbox = new javax.swing.JCheckBox();
        payeesCheckbox = new javax.swing.JCheckBox();
        searchInput = new javax.swing.JTextField();
        searchButton = new javax.swing.JButton();
        caseCheckbox = new javax.swing.JCheckBox();
        searchInfoLabel = new javax.swing.JLabel();
        sponseeRadio = new javax.swing.JRadioButton();
        sponsorRadio = new javax.swing.JRadioButton();
        selectBulkRewardSharesButton = new javax.swing.JButton();
        selectBulkSelfSharesButton = new javax.swing.JButton();
        rewardSharesSpinner = new javax.swing.JSpinner();
        jLabel4 = new javax.swing.JLabel();
        selfSharesSpinner = new javax.swing.JSpinner();
        jLabel5 = new javax.swing.JLabel();
        selectSimilarDurationButton = new javax.swing.JButton();
        jLabel6 = new javax.swing.JLabel();
        durationSpinner = new javax.swing.JSpinner();
        resultsLabel = new javax.swing.JLabel();
        showFlaggedSponsorsListButton = new javax.swing.JButton();
        maxTxSpinner = new javax.swing.JSpinner();
        jLabel13 = new javax.swing.JLabel();
        jSeparator3 = new javax.swing.JSeparator();
        infoTab = new javax.swing.JPanel();
        jScrollPane5 = new javax.swing.JScrollPane();
        infoTable = new javax.swing.JTable();
        deepScanTab = new javax.swing.JScrollPane();
        deepScanTab.getVerticalScrollBar().setUnitIncrement(10);
        menuPanel1 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        sponseeSpinner = new javax.swing.JSpinner();
        stopDeepScanButton = new javax.swing.JButton();
        updateDeepScanButton = new javax.swing.JButton();
        jLabel8 = new javax.swing.JLabel();
        jSeparator1 = new javax.swing.JSeparator();
        deepScanSelectedButton = new javax.swing.JButton();
        deepScanUnscannedButton = new javax.swing.JButton();
        updateDeepScanSpinner = new javax.swing.JSpinner();
        deepScanAllButton = new javax.swing.JButton();
        jLabel7 = new javax.swing.JLabel();
        jSeparator2 = new javax.swing.JSeparator();
        bottomPane = new javax.swing.JPanel();
        deepScanProgressBar = new javax.swing.JProgressBar();
        deepScanProgressBar.setVisible(false);
        deepScanProgressBar2 = new javax.swing.JProgressBar();
        deepScanProgressBar2.setVisible(false);
        tablesSplitpane = new javax.swing.JSplitPane();
        jPanel2 = new javax.swing.JPanel();
        sponsorsTableScrollpane = new javax.swing.JScrollPane();
        sponsorsTable = new javax.swing.JTable();
        sponsorsTable.getTableHeader().setReorderingAllowed(false);
        jLabel3 = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        sponseesTableScrollpane = new javax.swing.JScrollPane();
        sponseesTable = new javax.swing.JTable();
        sponseesTable.getTableHeader().setReorderingAllowed(false);
        jLabel2 = new javax.swing.JLabel();
        updateTab = new javax.swing.JPanel();
        jScrollPane3 = new javax.swing.JScrollPane();
        rewardsharesTable = new javax.swing.JTable();
        startMappingButton = new javax.swing.JButton();
        stopButton = new javax.swing.JButton();
        progressBar = new javax.swing.JProgressBar();
        updateStatusLabel = new javax.swing.JLabel();
        lookupStatusLabel = new javax.swing.JLabel();
        sponseeProgressbar = new javax.swing.JProgressBar();
        sponseeProgressbar.setVisible(false);
        buttonGroup.add(minuteRadio);
        buttonGroup.add(hourRadio);
        buttonGroup.add(dayRadio);
        buttonGroup.add(weekRadio);
        buttonGroup.add(monthRadio);

        flaggedSponsorsDialog.setAlwaysOnTop(true);
        flaggedSponsorsDialog.setMinimumSize(new java.awt.Dimension(230, 27));
        flaggedSponsorsDialog.addWindowFocusListener(new java.awt.event.WindowFocusListener()
        {
            public void windowGainedFocus(java.awt.event.WindowEvent evt)
            {
            }
            public void windowLostFocus(java.awt.event.WindowEvent evt)
            {
                flaggedSponsorsDialogWindowLostFocus(evt);
            }
        });

        flaggedSponsorsTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][]
            {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String []
            {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        jScrollPane1.setViewportView(flaggedSponsorsTable);

        javax.swing.GroupLayout flaggedSponsorsDialogLayout = new javax.swing.GroupLayout(flaggedSponsorsDialog.getContentPane());
        flaggedSponsorsDialog.getContentPane().setLayout(flaggedSponsorsDialogLayout);
        flaggedSponsorsDialogLayout.setHorizontalGroup(
            flaggedSponsorsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 415, Short.MAX_VALUE)
        );
        flaggedSponsorsDialogLayout.setVerticalGroup(
            flaggedSponsorsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 477, Short.MAX_VALUE)
        );

        flaggedSponsorsDialog.pack();
        flaggedSponsorsDialog.setSize(500, 500);

        sponseeInfoDialog.setAlwaysOnTop(true);
        sponseeInfoDialog.setType(java.awt.Window.Type.UTILITY);
        sponseeInfoDialog.addWindowFocusListener(new java.awt.event.WindowFocusListener()
        {
            public void windowGainedFocus(java.awt.event.WindowEvent evt)
            {
            }
            public void windowLostFocus(java.awt.event.WindowEvent evt)
            {
                sponseeInfoDialogWindowLostFocus(evt);
            }
        });

        jTabbedPane2.setMinimumSize(new java.awt.Dimension(200, 200));
        jTabbedPane2.setPreferredSize(new java.awt.Dimension(0, 0));

        sponseeInfoTab.setMinimumSize(new java.awt.Dimension(0, 0));
        sponseeInfoTab.setPreferredSize(new java.awt.Dimension(0, 0));
        sponseeInfoTab.setLayout(new java.awt.GridBagLayout());

        jScrollPane4.setMaximumSize(new java.awt.Dimension(0, 75));
        jScrollPane4.setMinimumSize(new java.awt.Dimension(0, 75));
        jScrollPane4.setPreferredSize(new java.awt.Dimension(0, 75));

        mySponsorsTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][]
            {

            },
            new String []
            {
                "Sponsor name", "Sponsor address"
            }
        ));
        mySponsorsTable.setMaximumSize(new java.awt.Dimension(0, 100));
        mySponsorsTable.setMinimumSize(new java.awt.Dimension(0, 100));
        mySponsorsTable.setPreferredSize(new java.awt.Dimension(0, 100));
        jScrollPane4.setViewportView(mySponsorsTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.PAGE_START;
        gridBagConstraints.weightx = 0.2;
        sponseeInfoTab.add(jScrollPane4, gridBagConstraints);

        sponseeInfoTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][]
            {
                {null, null},
                {null, null},
                {null, null},
                {null, null}
            },
            new String []
            {
                "", ""
            }
        ));
        jScrollPane2.setViewportView(sponseeInfoTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        sponseeInfoTab.add(jScrollPane2, gridBagConstraints);

        jTabbedPane2.addTab("Sponsee info", sponseeInfoTab);

        paymentsTab.setPreferredSize(new java.awt.Dimension(0, 0));

        paymentsTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][]
            {

            },
            new String []
            {
                "Timestamp", "Amount", "Recipient", "Name", "Type"
            }
        )
        {
            boolean[] canEdit = new boolean []
            {
                false, false, false, false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex)
            {
                return canEdit [columnIndex];
            }
        });
        paymentsTab.setViewportView(paymentsTable);

        jTabbedPane2.addTab("Latest payments", paymentsTab);

        payeesTab.setMinimumSize(new java.awt.Dimension(300, 300));
        payeesTab.setLayout(new java.awt.GridBagLayout());

        payeesTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][]
            {

            },
            new String []
            {
                "Address", "Name", "Payments count"
            }
        )
        {
            boolean[] canEdit = new boolean []
            {
                false, false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex)
            {
                return canEdit [columnIndex];
            }
        });
        payeesScrollpane.setViewportView(payeesTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        payeesTab.add(payeesScrollpane, gridBagConstraints);

        jLabel11.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel11.setText("<html><div style='text-align: center;'>These are accounts that have received payments from this sponsee<br/>The payments count is the number of times this sponsee has made a payment to that account</div><html>");
        jLabel11.setMinimumSize(new java.awt.Dimension(74, 50));
        jLabel11.setPreferredSize(new java.awt.Dimension(0, 50));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipady = 20;
        gridBagConstraints.weightx = 0.1;
        payeesTab.add(jLabel11, gridBagConstraints);

        jTabbedPane2.addTab("Payees", payeesTab);

        commonPayeesTab.setMinimumSize(new java.awt.Dimension(300, 300));
        commonPayeesTab.setLayout(new java.awt.GridBagLayout());

        commonPayeesTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][]
            {

            },
            new String []
            {
                "Address", "Name", "Payees in common"
            }
        )
        {
            boolean[] canEdit = new boolean []
            {
                false, false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex)
            {
                return canEdit [columnIndex];
            }
        });
        commonPayeesScrollpane.setViewportView(commonPayeesTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        commonPayeesTab.add(commonPayeesScrollpane, gridBagConstraints);

        jLabel9.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel9.setText("<html><div style='text-align: center;'>These are accounts that have received payments from this sponsee and multiple other sponsees of this sponsor<br/>Payees in common are the number of sponsees that have made payments to that address<br/>Consolidation of rewards from multiple sponsees to one account is a possible indication of self sponsorship</div><html>");
        jLabel9.setMinimumSize(new java.awt.Dimension(74, 50));
        jLabel9.setPreferredSize(new java.awt.Dimension(0, 50));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipady = 20;
        gridBagConstraints.weightx = 0.1;
        commonPayeesTab.add(jLabel9, gridBagConstraints);

        jTabbedPane2.addTab("Payees in common", commonPayeesTab);

        javax.swing.GroupLayout sponseeInfoDialogLayout = new javax.swing.GroupLayout(sponseeInfoDialog.getContentPane());
        sponseeInfoDialog.getContentPane().setLayout(sponseeInfoDialogLayout);
        sponseeInfoDialogLayout.setHorizontalGroup(
            sponseeInfoDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 871, Short.MAX_VALUE)
        );
        sponseeInfoDialogLayout.setVerticalGroup(
            sponseeInfoDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 663, Short.MAX_VALUE)
        );

        sponseeInfoDialog.pack();
        sponseeInfoDialog.setSize(600, 600);

        sponsorInfoDialog.setAlwaysOnTop(true);
        sponsorInfoDialog.setType(java.awt.Window.Type.UTILITY);
        sponsorInfoDialog.addWindowFocusListener(new java.awt.event.WindowFocusListener()
        {
            public void windowGainedFocus(java.awt.event.WindowEvent evt)
            {
            }
            public void windowLostFocus(java.awt.event.WindowEvent evt)
            {
                sponsorInfoDialogWindowLostFocus(evt);
            }
        });

        jTabbedPane3.setMinimumSize(new java.awt.Dimension(0, 0));
        jTabbedPane3.setPreferredSize(new java.awt.Dimension(0, 0));

        sponsorInfoTab.setMinimumSize(new java.awt.Dimension(0, 0));
        sponsorInfoTab.setPreferredSize(new java.awt.Dimension(0, 0));
        sponsorInfoTab.setLayout(new java.awt.GridBagLayout());

        sponsorInfoTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][]
            {
                {null, null},
                {null, null},
                {null, null},
                {null, null}
            },
            new String []
            {
                "", ""
            }
        ));
        jScrollPane6.setViewportView(sponsorInfoTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        sponsorInfoTab.add(jScrollPane6, gridBagConstraints);

        jTabbedPane3.addTab("Sponsor info", sponsorInfoTab);

        payersTab.setMinimumSize(new java.awt.Dimension(300, 300));
        payersTab.setLayout(new java.awt.GridBagLayout());

        commonPayersTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][]
            {

            },
            new String []
            {
                "Address", "Name"
            }
        )
        {
            boolean[] canEdit = new boolean []
            {
                false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex)
            {
                return canEdit [columnIndex];
            }
        });
        commonPayersScrollpane.setViewportView(commonPayersTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        payersTab.add(commonPayersScrollpane, gridBagConstraints);

        jLabel10.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel10.setText("<html><div style='text-align: center;'>These are this sponsors sponsees that have sent payments to common recipients<br/>Consolidation of rewards from multiple sponsees to one account is a possible indication of self sponsorship</div><html>");
        jLabel10.setMinimumSize(new java.awt.Dimension(74, 50));
        jLabel10.setPreferredSize(new java.awt.Dimension(0, 50));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipady = 20;
        gridBagConstraints.weightx = 0.1;
        payersTab.add(jLabel10, gridBagConstraints);

        jTabbedPane3.addTab("Payers", payersTab);

        payeesTabSponsor.setMinimumSize(new java.awt.Dimension(300, 300));
        payeesTabSponsor.setLayout(new java.awt.GridBagLayout());

        payeesTableSponsor.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][]
            {

            },
            new String []
            {
                "Address", "Name", "Sponsee count"
            }
        )
        {
            boolean[] canEdit = new boolean []
            {
                false, false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex)
            {
                return canEdit [columnIndex];
            }
        });
        payeesScrollpaneSponsor.setViewportView(payeesTableSponsor);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        payeesTabSponsor.add(payeesScrollpaneSponsor, gridBagConstraints);

        jLabel12.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel12.setText("<html><div style='text-align: center;'>These are all the addreses to which this sponsor's sponsees have made payments<br/>and the number of sponsees that have made a payment to that address<br/>Consolidation of rewards from multiple sponsees to one account is a possible indication of self sponsorship</div><html>");
        jLabel12.setMinimumSize(new java.awt.Dimension(74, 50));
        jLabel12.setPreferredSize(new java.awt.Dimension(0, 50));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipady = 20;
        gridBagConstraints.weightx = 0.1;
        payeesTabSponsor.add(jLabel12, gridBagConstraints);

        jTabbedPane3.addTab("Payees", payeesTabSponsor);

        chartsTab.setLayout(new java.awt.GridBagLayout());

        chartPlaceHolder.setLayout(new javax.swing.BoxLayout(chartPlaceHolder, javax.swing.BoxLayout.LINE_AXIS));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        chartsTab.add(chartPlaceHolder, gridBagConstraints);

        minuteRadio.setText("Sort by minute");
        minuteRadio.setActionCommand("minute");
        minuteRadio.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                minuteRadioActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
        chartsTab.add(minuteRadio, gridBagConstraints);

        hourRadio.setText("Sort by hour");
        hourRadio.setActionCommand("hour");
        hourRadio.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                minuteRadioActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
        chartsTab.add(hourRadio, gridBagConstraints);

        dayRadio.setText("Sort by day");
        dayRadio.setActionCommand("day");
        dayRadio.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                minuteRadioActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
        chartsTab.add(dayRadio, gridBagConstraints);

        weekRadio.setSelected(true);
        weekRadio.setText("Sort by week");
        weekRadio.setActionCommand("week");
        weekRadio.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                minuteRadioActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
        chartsTab.add(weekRadio, gridBagConstraints);

        monthRadio.setText("Sort by month");
        monthRadio.setActionCommand("month");
        monthRadio.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                minuteRadioActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
        chartsTab.add(monthRadio, gridBagConstraints);

        jTabbedPane3.addTab("Sponsorships chart", chartsTab);

        javax.swing.GroupLayout sponsorInfoDialogLayout = new javax.swing.GroupLayout(sponsorInfoDialog.getContentPane());
        sponsorInfoDialog.getContentPane().setLayout(sponsorInfoDialogLayout);
        sponsorInfoDialogLayout.setHorizontalGroup(
            sponsorInfoDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 706, Short.MAX_VALUE)
        );
        sponsorInfoDialogLayout.setVerticalGroup(
            sponsorInfoDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane3, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 462, Short.MAX_VALUE)
        );

        sponsorInfoDialog.pack();
        sponsorInfoDialog.setSize(600, 600);

        transactionsDialog.setAlwaysOnTop(true);
        transactionsDialog.setType(java.awt.Window.Type.UTILITY);
        transactionsDialog.addWindowFocusListener(new java.awt.event.WindowFocusListener()
        {
            public void windowGainedFocus(java.awt.event.WindowEvent evt)
            {
            }
            public void windowLostFocus(java.awt.event.WindowEvent evt)
            {
                transactionsDialogWindowLostFocus(evt);
            }
        });

        transactionsMainPanel.setMinimumSize(new java.awt.Dimension(300, 300));
        transactionsMainPanel.setLayout(new java.awt.GridBagLayout());

        transactionsTable.setModel(new javax.swing.table.DefaultTableModel(
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
        transactionsTableScrollpane.setViewportView(transactionsTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        transactionsMainPanel.add(transactionsTableScrollpane, gridBagConstraints);

        transactionsInfoLabel.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        transactionsInfoLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        transactionsInfoLabel.setMinimumSize(new java.awt.Dimension(74, 50));
        transactionsInfoLabel.setPreferredSize(new java.awt.Dimension(0, 50));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipady = 20;
        gridBagConstraints.weightx = 0.1;
        transactionsMainPanel.add(transactionsInfoLabel, gridBagConstraints);

        javax.swing.GroupLayout transactionsDialogLayout = new javax.swing.GroupLayout(transactionsDialog.getContentPane());
        transactionsDialog.getContentPane().setLayout(transactionsDialogLayout);
        transactionsDialogLayout.setHorizontalGroup(
            transactionsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(transactionsMainPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 803, Short.MAX_VALUE)
        );
        transactionsDialogLayout.setVerticalGroup(
            transactionsDialogLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(transactionsMainPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        transactionsDialog.pack();
        transactionsDialog.setSize(800, 1000);

        sponsorsTab.setDividerLocation(350);
        sponsorsTab.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        menuPanel.setLayout(new java.awt.GridBagLayout());

        deselectButton.setText("Deselect");
        deselectButton.setMaximumSize(new java.awt.Dimension(275, 27));
        deselectButton.setMinimumSize(new java.awt.Dimension(275, 27));
        deselectButton.setName(""); // NOI18N
        deselectButton.setPreferredSize(new java.awt.Dimension(275, 27));
        deselectButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                deselectButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        menuPanel.add(deselectButton, gridBagConstraints);

        accountCheckbox.setText("Show account info");
        accountCheckbox.setToolTipText("Toggles the 'BLOCKS_MINTED', 'LEVEL' and 'MINTED_ADJ' columns in the sponsees table");
        accountCheckbox.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                accountCheckboxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        menuPanel.add(accountCheckbox, gridBagConstraints);

        balanceCheckbox.setText("Show balance info");
        balanceCheckbox.setToolTipText("Toggles the 'BALANCE' and 'BALANCE_FLAG' columns in the sponsees table");
        balanceCheckbox.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                accountCheckboxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        menuPanel.add(balanceCheckbox, gridBagConstraints);

        paymentsCheckbox.setText("Show payments info");
        paymentsCheckbox.setToolTipText("Toggles the 'PAYMENTS_TO_SPONSOR', 'PAYMENTS_COUNT' and 'PAYMENTS' columns in the sponsees table");
        paymentsCheckbox.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                accountCheckboxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        menuPanel.add(paymentsCheckbox, gridBagConstraints);

        payeesCheckbox.setText("Show payees info");
        payeesCheckbox.setToolTipText("Toggles the 'PAYEES_COUNT', 'PAYEES', 'COMMON_PAYEES_COUNT' and 'COMMON_PAYEES' columns in the sponsees table");
        payeesCheckbox.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                accountCheckboxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        menuPanel.add(payeesCheckbox, gridBagConstraints);

        searchInput.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        searchInput.setMinimumSize(new java.awt.Dimension(300, 27));
        searchInput.setPreferredSize(new java.awt.Dimension(300, 27));
        searchInput.addFocusListener(new java.awt.event.FocusAdapter()
        {
            public void focusGained(java.awt.event.FocusEvent evt)
            {
                searchInputFocusGained(evt);
            }
        });
        searchInput.addKeyListener(new java.awt.event.KeyAdapter()
        {
            public void keyReleased(java.awt.event.KeyEvent evt)
            {
                searchInputKeyReleased(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 10, 0);
        menuPanel.add(searchInput, gridBagConstraints);

        searchButton.setText("Search");
        searchButton.setMaximumSize(new java.awt.Dimension(120, 25));
        searchButton.setMinimumSize(new java.awt.Dimension(120, 25));
        searchButton.setPreferredSize(new java.awt.Dimension(120, 25));
        searchButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                searchButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(1, 5, 10, 0);
        menuPanel.add(searchButton, gridBagConstraints);

        caseCheckbox.setText("Case sensitive");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(1, 0, 10, 5);
        menuPanel.add(caseCheckbox, gridBagConstraints);

        searchInfoLabel.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        searchInfoLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
        menuPanel.add(searchInfoLabel, gridBagConstraints);

        sponseeRadio.setSelected(true);
        sponseeRadio.setText("Sponsee search");
        sponseeRadio.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                sponseeRadioActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        menuPanel.add(sponseeRadio, gridBagConstraints);

        sponsorRadio.setText("Sponsor search");
        sponsorRadio.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                sponsorRadioActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        menuPanel.add(sponsorRadio, gridBagConstraints);

        selectBulkRewardSharesButton.setText("Select bulk reward shares");
        selectBulkRewardSharesButton.setToolTipText("Select all sponsees for which their sponsor has created a reward share within certain time interval");
        selectBulkRewardSharesButton.setEnabled(false);
        selectBulkRewardSharesButton.setMaximumSize(new java.awt.Dimension(275, 27));
        selectBulkRewardSharesButton.setMinimumSize(new java.awt.Dimension(275, 27));
        selectBulkRewardSharesButton.setName(""); // NOI18N
        selectBulkRewardSharesButton.setPreferredSize(new java.awt.Dimension(275, 27));
        selectBulkRewardSharesButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                selectBulkRewardSharesButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        menuPanel.add(selectBulkRewardSharesButton, gridBagConstraints);

        selectBulkSelfSharesButton.setText("Select bulk self shares");
        selectBulkSelfSharesButton.setToolTipText("Select all sponsees which have created a self share within a certain time interval");
        selectBulkSelfSharesButton.setEnabled(false);
        selectBulkSelfSharesButton.setMaximumSize(new java.awt.Dimension(275, 27));
        selectBulkSelfSharesButton.setMinimumSize(new java.awt.Dimension(275, 27));
        selectBulkSelfSharesButton.setName(""); // NOI18N
        selectBulkSelfSharesButton.setPreferredSize(new java.awt.Dimension(275, 27));
        selectBulkSelfSharesButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                selectBulkSelfSharesButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        menuPanel.add(selectBulkSelfSharesButton, gridBagConstraints);

        rewardSharesSpinner.setModel(new javax.swing.SpinnerNumberModel(5, 1, 100, 1));
        rewardSharesSpinner.setToolTipText("Reward shares created within this number of blocks will be selected");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 6;
        menuPanel.add(rewardSharesSpinner, gridBagConstraints);

        jLabel4.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        jLabel4.setText("Time between creation (blocks)");
        jLabel4.setToolTipText("Reward shares created within this number of blocks will be selected");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
        menuPanel.add(jLabel4, gridBagConstraints);

        selfSharesSpinner.setModel(new javax.swing.SpinnerNumberModel(5, 1, 100, 1));
        selfSharesSpinner.setToolTipText("Self shares created within this number of blocks will be selected");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 7;
        menuPanel.add(selfSharesSpinner, gridBagConstraints);

        jLabel5.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        jLabel5.setText("Time between creation (blocks)");
        jLabel5.setToolTipText("Self shares created within this number of blocks will be selected");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
        menuPanel.add(jLabel5, gridBagConstraints);

        selectSimilarDurationButton.setText("<html><div style='text-align: center;'>Select bulk reward and self<br/>shares with a similar duration</div><html>");
        selectSimilarDurationButton.setToolTipText("Select all sponsees which have created a reward share and a self share within a certain time interval and have a similar sponsorship duration");
        selectSimilarDurationButton.setEnabled(false);
        selectSimilarDurationButton.setMaximumSize(new java.awt.Dimension(275, 40));
        selectSimilarDurationButton.setMinimumSize(new java.awt.Dimension(275, 40));
        selectSimilarDurationButton.setOpaque(true);
        selectSimilarDurationButton.setPreferredSize(new java.awt.Dimension(275, 40));
        selectSimilarDurationButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                selectSimilarDurationButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        menuPanel.add(selectSimilarDurationButton, gridBagConstraints);

        jLabel6.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        jLabel6.setText("Deviation allowance (minutes)");
        jLabel6.setToolTipText("The number of minutes which the adjecent entries are allowed to deviate and still be included in the selection");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
        menuPanel.add(jLabel6, gridBagConstraints);

        durationSpinner.setModel(new javax.swing.SpinnerNumberModel(5, 1, 180, 1));
        durationSpinner.setToolTipText("The number of minutes which the adjecent entries are allowed to deviate and still be included in the selection");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 8;
        menuPanel.add(durationSpinner, gridBagConstraints);

        resultsLabel.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        resultsLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        menuPanel.add(resultsLabel, gridBagConstraints);

        showFlaggedSponsorsListButton.setText("Show a list of all sponsors with bulk & self shares with similar duration (using above values)");
        showFlaggedSponsorsListButton.setToolTipText("Select all sponsees which have created a reward share and a self share within a certain time interval and have a similar sponsorship duration");
        showFlaggedSponsorsListButton.setMaximumSize(new java.awt.Dimension(550, 27));
        showFlaggedSponsorsListButton.setMinimumSize(new java.awt.Dimension(550, 27));
        showFlaggedSponsorsListButton.setOpaque(true);
        showFlaggedSponsorsListButton.setPreferredSize(new java.awt.Dimension(550, 27));
        showFlaggedSponsorsListButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                showFlaggedSponsorsListButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 5, 0);
        menuPanel.add(showFlaggedSponsorsListButton, gridBagConstraints);

        maxTxSpinner.setModel(new javax.swing.SpinnerNumberModel(50, 50, 500, 1));
        maxTxSpinner.setToolTipText("The maximum number of transactions to lookup when double clicking on an address in one of the pop up dialogs");
        maxTxSpinner.addChangeListener(new javax.swing.event.ChangeListener()
        {
            public void stateChanged(javax.swing.event.ChangeEvent evt)
            {
                maxTxSpinnerStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 5;
        menuPanel.add(maxTxSpinner, gridBagConstraints);

        jLabel13.setText("Max transactions lookup (50-500)");
        jLabel13.setToolTipText("The maximum number of transactions to lookup when double clicking on an address in one of the pop up dialogs");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
        menuPanel.add(jLabel13, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        menuPanel.add(jSeparator3, gridBagConstraints);

        searchAndSelectTab.setViewportView(menuPanel);

        topPane.addTab("Search & select", searchAndSelectTab);

        infoTab.setLayout(new javax.swing.BoxLayout(infoTab, javax.swing.BoxLayout.LINE_AXIS));

        infoTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][]
            {
                {null, null},
                {null, null},
                {null, null},
                {null, null}
            },
            new String []
            {
                "", ""
            }
        ));
        jScrollPane5.setViewportView(infoTable);

        infoTab.add(jScrollPane5);

        topPane.addTab("Info", infoTab);

        menuPanel1.setLayout(new java.awt.GridBagLayout());

        jLabel1.setText("Sponsee balance threshold (1-100)");
        jLabel1.setToolTipText("Sponsees with a balance lower than this amount will be flagged with an asterisk (*)");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(8, 23, 0, 0);
        menuPanel1.add(jLabel1, gridBagConstraints);

        sponseeSpinner.setModel(new javax.swing.SpinnerNumberModel(10, 1, 100, 1));
        sponseeSpinner.setToolTipText("Sponsees with a balance lower than this amount will be flagged with an asterisk (*)");
        sponseeSpinner.addChangeListener(new javax.swing.event.ChangeListener()
        {
            public void stateChanged(javax.swing.event.ChangeEvent evt)
            {
                sponseeSpinnerStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
        gridBagConstraints.insets = new java.awt.Insets(8, 0, 0, 38);
        menuPanel1.add(sponseeSpinner, gridBagConstraints);

        stopDeepScanButton.setText("Stop deep scan");
        stopDeepScanButton.setToolTipText("Deep scans will extract more information on sponsors and their sponsees from the blockchain");
        stopDeepScanButton.setEnabled(false);
        stopDeepScanButton.setMaximumSize(new java.awt.Dimension(215, 27));
        stopDeepScanButton.setMinimumSize(new java.awt.Dimension(215, 27));
        stopDeepScanButton.setName(""); // NOI18N
        stopDeepScanButton.setPreferredSize(new java.awt.Dimension(215, 27));
        stopDeepScanButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                stopDeepScanButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        menuPanel1.add(stopDeepScanButton, gridBagConstraints);

        updateDeepScanButton.setText("Update now");
        updateDeepScanButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                updateDeepScanButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.insets = new java.awt.Insets(8, 0, 8, 0);
        menuPanel1.add(updateDeepScanButton, gridBagConstraints);

        jLabel8.setText("or more hours");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
        gridBagConstraints.insets = new java.awt.Insets(8, 0, 8, 38);
        menuPanel1.add(jLabel8, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(8, 0, 8, 0);
        menuPanel1.add(jSeparator1, gridBagConstraints);

        deepScanSelectedButton.setText("Deep scan selected sponsor");
        deepScanSelectedButton.setEnabled(false);
        deepScanSelectedButton.setMaximumSize(new java.awt.Dimension(215, 27));
        deepScanSelectedButton.setMinimumSize(new java.awt.Dimension(215, 27));
        deepScanSelectedButton.setPreferredSize(new java.awt.Dimension(215, 27));
        deepScanSelectedButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                deepScanSelectedButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(8, 0, 8, 0);
        menuPanel1.add(deepScanSelectedButton, gridBagConstraints);

        deepScanUnscannedButton.setText("Deep scan all unscanned sponsors");
        deepScanUnscannedButton.setMaximumSize(new java.awt.Dimension(215, 27));
        deepScanUnscannedButton.setMinimumSize(new java.awt.Dimension(215, 27));
        deepScanUnscannedButton.setPreferredSize(new java.awt.Dimension(215, 27));
        deepScanUnscannedButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                deepScanUnscannedButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(8, 0, 8, 0);
        menuPanel1.add(deepScanUnscannedButton, gridBagConstraints);

        updateDeepScanSpinner.setModel(new javax.swing.SpinnerNumberModel(24, 1, 168, 1));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.ipadx = 25;
        gridBagConstraints.insets = new java.awt.Insets(8, 0, 8, 0);
        menuPanel1.add(updateDeepScanSpinner, gridBagConstraints);

        deepScanAllButton.setText("Deep scan all sponsors");
        deepScanAllButton.setMaximumSize(new java.awt.Dimension(215, 27));
        deepScanAllButton.setMinimumSize(new java.awt.Dimension(215, 27));
        deepScanAllButton.setPreferredSize(new java.awt.Dimension(215, 27));
        deepScanAllButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                deepScanAllButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(8, 0, 8, 0);
        menuPanel1.add(deepScanAllButton, gridBagConstraints);

        jLabel7.setText("Update deep scan for sponsors that have not been scanned for");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.insets = new java.awt.Insets(8, 0, 8, 0);
        menuPanel1.add(jLabel7, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(8, 0, 8, 0);
        menuPanel1.add(jSeparator2, gridBagConstraints);

        deepScanTab.setViewportView(menuPanel1);

        topPane.addTab("Deep scan", deepScanTab);

        sponsorsTab.setLeftComponent(topPane);

        bottomPane.setLayout(new java.awt.GridBagLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipady = 15;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        bottomPane.add(deepScanProgressBar, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipady = 15;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 0.1;
        bottomPane.add(deepScanProgressBar2, gridBagConstraints);

        tablesSplitpane.setDividerLocation(400);

        jPanel2.setLayout(new java.awt.GridBagLayout());

        sponsorsTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        sponsorsTableScrollpane.setViewportView(sponsorsTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        jPanel2.add(sponsorsTableScrollpane, gridBagConstraints);

        jLabel3.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel3.setText("Sponsors");
        jPanel2.add(jLabel3, new java.awt.GridBagConstraints());

        tablesSplitpane.setLeftComponent(jPanel2);

        jPanel3.setLayout(new java.awt.GridBagLayout());

        sponseesTableScrollpane.setViewportView(sponseesTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        jPanel3.add(sponseesTableScrollpane, gridBagConstraints);

        jLabel2.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        jLabel2.setText("Sponsees");
        jPanel3.add(jLabel2, new java.awt.GridBagConstraints());

        tablesSplitpane.setRightComponent(jPanel3);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        bottomPane.add(tablesSplitpane, gridBagConstraints);

        sponsorsTab.setRightComponent(bottomPane);

        jTabbedPane1.addTab("Sponsors", sponsorsTab);

        updateTab.setLayout(new java.awt.GridBagLayout());

        jScrollPane3.setViewportView(rewardsharesTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridy = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        updateTab.add(jScrollPane3, gridBagConstraints);

        startMappingButton.setText("Start mapping");
        startMappingButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                startMappingButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(15, 0, 5, 0);
        updateTab.add(startMappingButton, gridBagConstraints);

        stopButton.setText("Stop mapping");
        stopButton.setEnabled(false);
        stopButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                stopButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        updateTab.add(stopButton, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipady = 15;
        gridBagConstraints.insets = new java.awt.Insets(30, 0, 0, 0);
        updateTab.add(progressBar, gridBagConstraints);

        updateStatusLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        updateStatusLabel.setText("updateStatusLabel");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        updateTab.add(updateStatusLabel, gridBagConstraints);
        updateStatusLabel.setText("");

        lookupStatusLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lookupStatusLabel.setText("Click on start mapping to fetch the latest reward shares from the blockchain");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        updateTab.add(lookupStatusLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipady = 15;
        updateTab.add(sponseeProgressbar, gridBagConstraints);

        jTabbedPane1.addTab("Update", updateTab);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 797, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 616, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void startMappingButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_startMappingButtonActionPerformed
    {//GEN-HEADEREND:event_startMappingButtonActionPerformed
        if(updateInProgress)
        {
            JOptionPane.showMessageDialog(this, "A sponsor deep scan is currently in progress, update aborted");
            return;
        }
        
        startMapping();
    }//GEN-LAST:event_startMappingButtonActionPerformed

    private void stopButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_stopButtonActionPerformed
    {//GEN-HEADEREND:event_stopButtonActionPerformed
        LOOKUP_HALTED = true;
    }//GEN-LAST:event_stopButtonActionPerformed

    private void sponseeSpinnerStateChanged(javax.swing.event.ChangeEvent evt)//GEN-FIRST:event_sponseeSpinnerStateChanged
    {//GEN-HEADEREND:event_sponseeSpinnerStateChanged
        Utilities.updateSetting("sponseeBalanceThreshold", String.valueOf(sponseeSpinner.getValue()), "settings.json");
    }//GEN-LAST:event_sponseeSpinnerStateChanged

    private void accountCheckboxActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_accountCheckboxActionPerformed
    {//GEN-HEADEREND:event_accountCheckboxActionPerformed
        fillSponseesTable("");
    }//GEN-LAST:event_accountCheckboxActionPerformed

    private void searchInputFocusGained(java.awt.event.FocusEvent evt)//GEN-FIRST:event_searchInputFocusGained
    {//GEN-HEADEREND:event_searchInputFocusGained
        searchInput.selectAll();
    }//GEN-LAST:event_searchInputFocusGained

    private void searchInputKeyReleased(java.awt.event.KeyEvent evt)//GEN-FIRST:event_searchInputKeyReleased
    {//GEN-HEADEREND:event_searchInputKeyReleased
        if(searchInput.getText().isBlank())
            searchButton.setText("Search");
        
        if(evt.getKeyCode() == KeyEvent.VK_ENTER && !searchResults.isEmpty())
        {
            searchIndex = searchIndex + 1 > searchResults.size() - 1 ? 0 : searchIndex + 1;
            goToSearchResult(searchResults.get(searchIndex));
        }  
        else
            startSearch();
    }//GEN-LAST:event_searchInputKeyReleased

    private void searchButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_searchButtonActionPerformed
    {//GEN-HEADEREND:event_searchButtonActionPerformed
        if(searchResults.isEmpty())
        {
            startSearch();
        }
        else
        {
            searchIndex = searchIndex + 1 > searchResults.size() - 1 ? 0 : searchIndex + 1;
            goToSearchResult(searchResults.get(searchIndex));
        }        
    }//GEN-LAST:event_searchButtonActionPerformed

    private void sponseeRadioActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_sponseeRadioActionPerformed
    {//GEN-HEADEREND:event_sponseeRadioActionPerformed
        sponsorRadio.setSelected(!sponseeRadio.isSelected());
        startSearch();
    }//GEN-LAST:event_sponseeRadioActionPerformed

    private void sponsorRadioActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_sponsorRadioActionPerformed
    {//GEN-HEADEREND:event_sponsorRadioActionPerformed
        sponseeRadio.setSelected(!sponsorRadio.isSelected());
        startSearch();
    }//GEN-LAST:event_sponsorRadioActionPerformed

    private void deselectButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_deselectButtonActionPerformed
    {//GEN-HEADEREND:event_deselectButtonActionPerformed
        boolean refreshSponseesTable = sponsorsTable.getSelectedRow() >= 0;
        
        sponsorsTable.clearSelection();
        sponseesTable.clearSelection();
        
        if(refreshSponseesTable)
            fillSponseesTable("");
    }//GEN-LAST:event_deselectButtonActionPerformed

    private void stopDeepScanButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_stopDeepScanButtonActionPerformed
    {//GEN-HEADEREND:event_stopDeepScanButtonActionPerformed
        LOOKUP_HALTED = true;
    }//GEN-LAST:event_stopDeepScanButtonActionPerformed

    private void selectBulkRewardSharesButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_selectBulkRewardSharesButtonActionPerformed
    {//GEN-HEADEREND:event_selectBulkRewardSharesButtonActionPerformed
        selectByBulk("blockheight_start", (int)rewardSharesSpinner.getValue());
    }//GEN-LAST:event_selectBulkRewardSharesButtonActionPerformed

    private void selectBulkSelfSharesButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_selectBulkSelfSharesButtonActionPerformed
    {//GEN-HEADEREND:event_selectBulkSelfSharesButtonActionPerformed
        selectByBulk("blockheight_end",(int)selfSharesSpinner.getValue());
    }//GEN-LAST:event_selectBulkSelfSharesButtonActionPerformed

    private void selectSimilarDurationButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_selectSimilarDurationButtonActionPerformed
    {//GEN-HEADEREND:event_selectSimilarDurationButtonActionPerformed
        if(sponsorsTable.getSelectedRow() < 0)
        {
            JOptionPane.showMessageDialog(this, "No sponsor selected");
            return;
        }
        
        sponseesTable.clearSelection();
        
        String sponsorAddress = sponsorsTable.getValueAt(sponsorsTable.getSelectedRow(), 0).toString();
        
        try(Connection c = ConnectionDB.getConnection("sponsors"))
        {
            ArrayList<String> bulkRewardShares = new ArrayList<>();
            ArrayList<String> bulkSelfShares = new ArrayList<>();
            ArrayList<String> similarDurations = new ArrayList<>();            
            
            //Get all sponsor created bulk reward shares 
            Statement statement = c.createStatement();
            ResultSet resultSet = statement.executeQuery(
                    "select blockheight_start,address from sponsees where sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress) + 
                    " order by blockheight_start desc");
            
            class Pair
            {
                public String address;
                public long value;
                
                public Pair(String address, long value)
                {
                    this.address = address;
                    this.value = value;
                }
            }
            ArrayList<Pair> addresses = new ArrayList<>();            
            
            int threshold = (int)rewardSharesSpinner.getValue();
            
            while(resultSet.next())
                addresses.add(new Pair(resultSet.getString("address"),resultSet.getInt("blockheight_start")));
            
            
            for(int i = 0; i < addresses.size() - 1; i++)
            {
                int blockHeight = (int)addresses.get(i).value;
                int nextBlockHeight = (int)addresses.get(i + 1).value;

                if(blockHeight - nextBlockHeight <= threshold)
                {
                    bulkRewardShares.add(addresses.get(i).address);
                    //no need to check prev if this row is marked for selection
                    continue;
                }

                if(i == 0)
                    continue;

                int prevBlockHeight = (int)addresses.get(i -1).value;
                if(prevBlockHeight - blockHeight <= threshold)
                    bulkRewardShares.add(addresses.get(i).address);
            }
            
            addresses.clear();
            
            threshold = (int)selfSharesSpinner.getValue();
            
            //Get all bulk self shares
            statement = c.createStatement();
            resultSet = statement.executeQuery(
                    "select blockheight_end,address from sponsees where sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress) + 
                    " order by blockheight_end desc");
            
            while(resultSet.next())
                addresses.add(new Pair(resultSet.getString("address"),resultSet.getInt("blockheight_end")));
            
            
            for(int i = 0; i < addresses.size() - 1; i++)
            {
                int blockHeight = (int)addresses.get(i).value;
                int nextBlockHeight = (int)addresses.get(i + 1).value;

                if(blockHeight - nextBlockHeight <= threshold)
                {
                    bulkSelfShares.add(addresses.get(i).address);
                    //no need to check prev if this row is marked for selection
                    continue;
                }

                if(i == 0)
                    continue;

                int prevBlockHeight = (int)addresses.get(i -1).value;
                if(prevBlockHeight - blockHeight <= threshold)
                    bulkSelfShares.add(addresses.get(i).address);
            }
            
            bulkRewardShares.retainAll(bulkSelfShares);
            
            addresses.clear();
            
            long minutesThreshold = (int)durationSpinner.getValue() * 60000;
            
            //Get all similar sponsorship durations
            statement = c.createStatement();
            resultSet = statement.executeQuery(
                    "select duration,address from sponsees where sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress) + 
                    " order by duration desc");
            
            while(resultSet.next())
                addresses.add(new Pair(resultSet.getString("address"),resultSet.getLong("duration")));
            
            
            for(int i = 0; i < addresses.size() - 1; i++)
            {
                long duration = addresses.get(i).value;
                long nextDuration = addresses.get(i + 1).value;

                if(duration - nextDuration <= minutesThreshold)
                {
                    similarDurations.add(addresses.get(i).address);
                    //no need to check prev if this row is marked for selection
                    continue;
                }

                if(i == 0)
                    continue;

                long previousDuration = addresses.get(i -1).value;
                if(previousDuration - duration <= minutesThreshold)
                    similarDurations.add(addresses.get(i).address);
            }
            
            bulkRewardShares.retainAll(similarDurations);            
            
           
            //test if above algo works by selecting the addresses in the table
            for(int i = 0 ; i < sponseesTable.getRowCount(); i++)
            {
                String sponsee = sponseesTable.getValueAt(i, 2).toString();
                
                if(bulkRewardShares.contains(sponsee))
                    sponseesTable.getSelectionModel().addSelectionInterval(i, i);
            }            
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }//GEN-LAST:event_selectSimilarDurationButtonActionPerformed

    private void deepScanSelectedButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_deepScanSelectedButtonActionPerformed
    {//GEN-HEADEREND:event_deepScanSelectedButtonActionPerformed
        if(sponsorsTable.getSelectedRow() < 0)
        {
            JOptionPane.showMessageDialog(this, "No sponsor selected");
            return;
        }
         if(updateInProgress)
        {
            JOptionPane.showMessageDialog(this, "An update is currently in progress, deep scan aborted");
            return;
        }
        
         Object selectedSponsor = sponsorsTable.getValueAt(sponsorsTable.getSelectedRow(), 0);
         startDeepScan(new ArrayList<>(Arrays.asList(new Object[]{selectedSponsor})));  
    }//GEN-LAST:event_deepScanSelectedButtonActionPerformed

    private void deepScanAllButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_deepScanAllButtonActionPerformed
    {//GEN-HEADEREND:event_deepScanAllButtonActionPerformed
        if(updateInProgress)
        {
            JOptionPane.showMessageDialog(this, "An update is currently in progress, deep scan aborted");
            return;
        }
        try(Connection c = ConnectionDB.getConnection("sponsors"))
        {       
            startDeepScan(dbManager.GetColumn("sponsors", "address", "scanned", "asc", c));
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }//GEN-LAST:event_deepScanAllButtonActionPerformed

    private void deepScanUnscannedButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_deepScanUnscannedButtonActionPerformed
    {//GEN-HEADEREND:event_deepScanUnscannedButtonActionPerformed
        if(updateInProgress)
        {
            JOptionPane.showMessageDialog(this, "An update is currently in progress, deep scan aborted");
            return;
        }
        try(Connection c = ConnectionDB.getConnection("sponsors"))
        {   
            startDeepScan(dbManager.GetColumn("sponsors", "address", " where scanned is null ", "", "", c));
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }//GEN-LAST:event_deepScanUnscannedButtonActionPerformed

    private void updateDeepScanButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_updateDeepScanButtonActionPerformed
    {//GEN-HEADEREND:event_updateDeepScanButtonActionPerformed
        if(updateInProgress)
        {
            JOptionPane.showMessageDialog(this, "An update is currently in progress, deep scan aborted");
            return;
        }
        try(Connection c = ConnectionDB.getConnection("sponsors"))
        {   
            long scanThreshold = System.currentTimeMillis() - ( ( (int) updateDeepScanSpinner.getValue()) * 3600000);
            
            startDeepScan(dbManager.GetColumn("sponsors", "address", 
                    " where scanned is null or scanned < " + String.valueOf(scanThreshold), "", "", c));            
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }//GEN-LAST:event_updateDeepScanButtonActionPerformed

    private void showFlaggedSponsorsListButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_showFlaggedSponsorsListButtonActionPerformed
    {//GEN-HEADEREND:event_showFlaggedSponsorsListButtonActionPerformed
        try(Connection c = ConnectionDB.getConnection("sponsors"))
        {
            ArrayList<String> bulkRewardShares = new ArrayList<>();
            ArrayList<String> bulkSelfShares = new ArrayList<>();
            ArrayList<String> similarDurations = new ArrayList<>();
            
            class FlaggedSponsor
            {
                public String address;
                public String name;
                public int flaggedCount;
                
                public FlaggedSponsor(String address,String name,int flaggedCount)
                {
                    this.address = address;
                    this.name = name;
                    this.flaggedCount = flaggedCount;
                }
            }
            
            ArrayList<FlaggedSponsor> flaggedSponsors = new ArrayList<>();
            
            for(Object sponsor : dbManager.GetColumn("sponsors", "address", "", "", c))
            {
                String sponsorAddress = sponsor.toString();
                String sponsorName = (String)dbManager.GetItemValue(
                        "sponsors", "name", "address", Utilities.SingleQuotedString(sponsorAddress), c);
                if(sponsorName.contains("'"))
                    sponsorName = sponsorName.replace("'", "''");
                
                bulkRewardShares.clear();
                bulkSelfShares.clear();
                similarDurations.clear();

                //Get all sponsor created bulk reward shares 
                Statement statement = c.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "select blockheight_start,address from sponsees where sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress) + 
                        " order by blockheight_start desc");

                class Pair
                {
                    public String address;
                    public long value;

                    public Pair(String address, long value)
                    {
                        this.address = address;
                        this.value = value;
                    }
                }
                ArrayList<Pair> addresses = new ArrayList<>();            

                int threshold = (int)rewardSharesSpinner.getValue();

                while(resultSet.next())
                    addresses.add(new Pair(resultSet.getString("address"),resultSet.getInt("blockheight_start")));

                for(int i = 0; i < addresses.size() - 1; i++)
                {
                    int blockHeight = (int)addresses.get(i).value;
                    int nextBlockHeight = (int)addresses.get(i + 1).value;

                    if(blockHeight - nextBlockHeight <= threshold)
                    {
                        bulkRewardShares.add(addresses.get(i).address);
                        //no need to check prev if this row is marked for selection
                        continue;
                    }

                    if(i == 0)
                        continue;

                    int prevBlockHeight = (int)addresses.get(i -1).value;
                    if(prevBlockHeight - blockHeight <= threshold)
                        bulkRewardShares.add(addresses.get(i).address);
                }

                addresses.clear();

                threshold = (int)selfSharesSpinner.getValue();

                //Get all bulk self shares
                statement = c.createStatement();
                resultSet = statement.executeQuery(
                        "select blockheight_end,address from sponsees where sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress) + 
                        " order by blockheight_end desc");

                while(resultSet.next())
                    addresses.add(new Pair(resultSet.getString("address"),resultSet.getInt("blockheight_end")));


                for(int i = 0; i < addresses.size() - 1; i++)
                {
                    int blockHeight = (int)addresses.get(i).value;
                    int nextBlockHeight = (int)addresses.get(i + 1).value;

                    if(blockHeight - nextBlockHeight <= threshold)
                    {
                        bulkSelfShares.add(addresses.get(i).address);
                        //no need to check prev if this row is marked for selection
                        continue;
                    }

                    if(i == 0)
                        continue;

                    int prevBlockHeight = (int)addresses.get(i -1).value;
                    if(prevBlockHeight - blockHeight <= threshold)
                        bulkSelfShares.add(addresses.get(i).address);
                }

                bulkRewardShares.retainAll(bulkSelfShares);

                addresses.clear();

                long minutesThreshold = (int)durationSpinner.getValue() * 60000;

                //Get all similar sponsorship durations
                statement = c.createStatement();
                resultSet = statement.executeQuery(
                        "select duration,address from sponsees where sponsor_address=" + Utilities.SingleQuotedString(sponsorAddress) + 
                        " order by duration desc");

                while(resultSet.next())
                    addresses.add(new Pair(resultSet.getString("address"),resultSet.getLong("duration")));


                for(int i = 0; i < addresses.size() - 1; i++)
                {
                    long duration = addresses.get(i).value;
                    long nextDuration = addresses.get(i + 1).value;

                    if(duration - nextDuration <= minutesThreshold)
                    {
                        similarDurations.add(addresses.get(i).address);
                        //no need to check prev if this row is marked for selection
                        continue;
                    }

                    if(i == 0)
                        continue;

                    long previousDuration = addresses.get(i -1).value;
                    if(previousDuration - duration <= minutesThreshold)
                        similarDurations.add(addresses.get(i).address);
                }

                bulkRewardShares.retainAll(similarDurations);   
                
                if(bulkRewardShares.isEmpty())
                    continue;
                
                flaggedSponsors.add(new FlaggedSponsor(sponsorAddress,sponsorName,bulkRewardShares.size()));   
            }
            
            if(dbManager.TableExists("dubious", c))
                dbManager.ExecuteUpdate("drop table dubious", c);
            
            dbManager.CreateTable(new String[]{"dubious","name","varchar(max)","address","varchar(100)","count","int"}, c);
            
            for(FlaggedSponsor ds : flaggedSponsors)
                dbManager.InsertIntoDB(new String[]
                {
                    "dubious",
                    "name",Utilities.SingleQuotedString(ds.name),
                    "address",Utilities.SingleQuotedString(ds.address),
                    "count",String.valueOf(ds.flaggedCount)
                }, c);    
            
            dbManager.FillJTableOrder("dubious", "count", "desc", flaggedSponsorsTable,true, c);      
            flaggedSponsorsTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);                
            flaggedSponsorsTable.getColumnModel().getColumn(0).setMinWidth(115);
            flaggedSponsorsTable.getColumnModel().getColumn(1).setMinWidth(285);
            flaggedSponsorsTable.getColumnModel().getColumn(2).setPreferredWidth(Integer.MAX_VALUE);                 
            
             SwingUtilities.invokeLater(() ->
            {
                int x = gui.getX() + ((gui.getWidth() / 2) - (flaggedSponsorsDialog.getWidth() / 2));
                int y = gui.getY() + ((gui.getHeight() / 2) - (flaggedSponsorsDialog.getHeight() / 2));
                flaggedSponsorsDialog.setLocation(x, y);
                flaggedSponsorsDialog.setVisible(true);
            });         
            
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }//GEN-LAST:event_showFlaggedSponsorsListButtonActionPerformed

    private void sponseeInfoDialogWindowLostFocus(java.awt.event.WindowEvent evt)//GEN-FIRST:event_sponseeInfoDialogWindowLostFocus
    {//GEN-HEADEREND:event_sponseeInfoDialogWindowLostFocus
        if(transactionsDialog.isVisible())
            return;
        
        sponseeInfoDialog.setVisible(false);
    }//GEN-LAST:event_sponseeInfoDialogWindowLostFocus

    private void flaggedSponsorsDialogWindowLostFocus(java.awt.event.WindowEvent evt)//GEN-FIRST:event_flaggedSponsorsDialogWindowLostFocus
    {//GEN-HEADEREND:event_flaggedSponsorsDialogWindowLostFocus
        flaggedSponsorsDialog.setVisible(false);
    }//GEN-LAST:event_flaggedSponsorsDialogWindowLostFocus

    private void sponsorInfoDialogWindowLostFocus(java.awt.event.WindowEvent evt)//GEN-FIRST:event_sponsorInfoDialogWindowLostFocus
    {//GEN-HEADEREND:event_sponsorInfoDialogWindowLostFocus
        if(transactionsDialog.isVisible())
            return;
        
        sponsorInfoDialog.setVisible(false);
    }//GEN-LAST:event_sponsorInfoDialogWindowLostFocus

    private void transactionsDialogWindowLostFocus(java.awt.event.WindowEvent evt)//GEN-FIRST:event_transactionsDialogWindowLostFocus
    {//GEN-HEADEREND:event_transactionsDialogWindowLostFocus
        transactionsDialog.setVisible(false);
    }//GEN-LAST:event_transactionsDialogWindowLostFocus

    private void maxTxSpinnerStateChanged(javax.swing.event.ChangeEvent evt)//GEN-FIRST:event_maxTxSpinnerStateChanged
    {//GEN-HEADEREND:event_maxTxSpinnerStateChanged
        Utilities.updateSetting("maxTxLookup", String.valueOf(maxTxSpinner.getValue()), "settings.json");
    }//GEN-LAST:event_maxTxSpinnerStateChanged

    private void minuteRadioActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_minuteRadioActionPerformed
    {//GEN-HEADEREND:event_minuteRadioActionPerformed
        if(sponsorsTable.getSelectedRow() < 0)
            return;
        
        String sponsorAddress = sponsorsTable.getValueAt(sponsorsTable.getSelectedRow(), 0).toString();
        String sponsorName = sponsorsTable.getValueAt(sponsorsTable.getSelectedRow(), 1).toString();
        loadSponsorshipsChart(sponsorAddress,sponsorName);
    }//GEN-LAST:event_minuteRadioActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox accountCheckbox;
    private javax.swing.JCheckBox balanceCheckbox;
    private javax.swing.JPanel bottomPane;
    private javax.swing.ButtonGroup buttonGroup;
    private javax.swing.JCheckBox caseCheckbox;
    private javax.swing.JPanel chartPlaceHolder;
    private javax.swing.JPanel chartsTab;
    private javax.swing.JScrollPane commonPayeesScrollpane;
    private javax.swing.JPanel commonPayeesTab;
    private javax.swing.JTable commonPayeesTable;
    private javax.swing.JScrollPane commonPayersScrollpane;
    private javax.swing.JTable commonPayersTable;
    private javax.swing.JRadioButton dayRadio;
    private javax.swing.JButton deepScanAllButton;
    private javax.swing.JProgressBar deepScanProgressBar;
    private javax.swing.JProgressBar deepScanProgressBar2;
    private javax.swing.JButton deepScanSelectedButton;
    private javax.swing.JScrollPane deepScanTab;
    private javax.swing.JButton deepScanUnscannedButton;
    private javax.swing.JButton deselectButton;
    private javax.swing.JSpinner durationSpinner;
    private javax.swing.JDialog flaggedSponsorsDialog;
    private javax.swing.JTable flaggedSponsorsTable;
    private javax.swing.JRadioButton hourRadio;
    private javax.swing.JPanel infoTab;
    private javax.swing.JTable infoTable;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JScrollPane jScrollPane6;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JTabbedPane jTabbedPane2;
    private javax.swing.JTabbedPane jTabbedPane3;
    private javax.swing.JLabel lookupStatusLabel;
    private javax.swing.JSpinner maxTxSpinner;
    private javax.swing.JPanel menuPanel;
    private javax.swing.JPanel menuPanel1;
    private javax.swing.JRadioButton minuteRadio;
    private javax.swing.JRadioButton monthRadio;
    private javax.swing.JTable mySponsorsTable;
    private javax.swing.JCheckBox payeesCheckbox;
    private javax.swing.JScrollPane payeesScrollpane;
    private javax.swing.JScrollPane payeesScrollpaneSponsor;
    private javax.swing.JPanel payeesTab;
    private javax.swing.JPanel payeesTabSponsor;
    private javax.swing.JTable payeesTable;
    private javax.swing.JTable payeesTableSponsor;
    private javax.swing.JPanel payersTab;
    private javax.swing.JCheckBox paymentsCheckbox;
    private javax.swing.JScrollPane paymentsTab;
    private javax.swing.JTable paymentsTable;
    private javax.swing.JProgressBar progressBar;
    private javax.swing.JLabel resultsLabel;
    private javax.swing.JSpinner rewardSharesSpinner;
    private javax.swing.JTable rewardsharesTable;
    private javax.swing.JScrollPane searchAndSelectTab;
    private javax.swing.JButton searchButton;
    private javax.swing.JLabel searchInfoLabel;
    private javax.swing.JTextField searchInput;
    private javax.swing.JButton selectBulkRewardSharesButton;
    private javax.swing.JButton selectBulkSelfSharesButton;
    private javax.swing.JButton selectSimilarDurationButton;
    private javax.swing.JSpinner selfSharesSpinner;
    private javax.swing.JButton showFlaggedSponsorsListButton;
    private javax.swing.JDialog sponseeInfoDialog;
    private javax.swing.JPanel sponseeInfoTab;
    private javax.swing.JTable sponseeInfoTable;
    private javax.swing.JProgressBar sponseeProgressbar;
    private javax.swing.JRadioButton sponseeRadio;
    private javax.swing.JSpinner sponseeSpinner;
    private javax.swing.JTable sponseesTable;
    private javax.swing.JScrollPane sponseesTableScrollpane;
    private javax.swing.JDialog sponsorInfoDialog;
    private javax.swing.JPanel sponsorInfoTab;
    private javax.swing.JTable sponsorInfoTable;
    private javax.swing.JRadioButton sponsorRadio;
    private javax.swing.JSplitPane sponsorsTab;
    private javax.swing.JTable sponsorsTable;
    private javax.swing.JScrollPane sponsorsTableScrollpane;
    private javax.swing.JButton startMappingButton;
    private javax.swing.JButton stopButton;
    private javax.swing.JButton stopDeepScanButton;
    private javax.swing.JSplitPane tablesSplitpane;
    private javax.swing.JTabbedPane topPane;
    private javax.swing.JDialog transactionsDialog;
    private javax.swing.JLabel transactionsInfoLabel;
    private javax.swing.JPanel transactionsMainPanel;
    private javax.swing.JTable transactionsTable;
    private javax.swing.JScrollPane transactionsTableScrollpane;
    private javax.swing.JButton updateDeepScanButton;
    private javax.swing.JSpinner updateDeepScanSpinner;
    private javax.swing.JLabel updateStatusLabel;
    private javax.swing.JPanel updateTab;
    private javax.swing.JRadioButton weekRadio;
    // End of variables declaration//GEN-END:variables


}
