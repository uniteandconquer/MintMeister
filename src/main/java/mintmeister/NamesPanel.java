package mintmeister;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.ConnectException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import org.json.JSONArray;
import org.json.JSONObject;

public class NamesPanel extends javax.swing.JPanel
{
    private boolean nameUpdateHalted;
    private DatabaseManager dbManager;
    private JSONObject jSONObject;
    private JSONArray jSONArray;
    private String jsonString;
    private GUI gui;
    private String orderKey = "desc";
    
    public NamesPanel()
    {
        initComponents();
        
        namesTable.getTableHeader().addMouseListener(new MouseAdapter()
           {
               @Override
               public void mouseClicked(MouseEvent e)
               {
                   orderKey = orderKey.equals("asc") ? "desc" : "asc";  
                   int col = namesTable.columnAtPoint(e.getPoint());
                   String headerName = namesTable.getColumnName(col);     
                   fillNamesTable(headerName, orderKey);
               }
           });
        
        namesTable.getSelectionModel().addListSelectionListener((ListSelectionEvent event) ->
        {
            if(event.getValueIsAdjusting())
                return;

            if(namesTable.getSelectedRow() < 0)
                return;

            DefaultListSelectionModel model = (DefaultListSelectionModel) event.getSource();

            //We don't want to update the selected name label on every selection event when multiple
            //items are selected
            if(model.getSelectedItemsCount() > 1)
            {
                int firstIndex = model.getSelectedIndices()[0];
                String benefactor = namesTable.getValueAt(firstIndex, 3).toString();
                for(int i = 1; i < model.getSelectedItemsCount();i++)
                {
                    int currentIndex = model.getSelectedIndices()[i];
                    if(!namesTable.getValueAt(currentIndex, 3).toString().equals(benefactor))
                    {
                        selectedNameLabel.setText("multiple benefactors selected");
                        selectBenificiariesButton.setEnabled(false);
                        return;
                    }
                    if(benefactor.isBlank())
                    {                            
                        selectedNameLabel.setText("multiple names selected");
                        selectBenificiariesButton.setEnabled(false);
                        return;
                    }
                }

                //When user clicks selectBenificiariesButton, multiple events will be triggered, this return statement
                //ensures that the selected names label shows the info for the first selected index, all the others will 
                //be skipped
                return;                    
            }

            //first check if name not blank
            String name = namesTable.getValueAt(namesTable.getSelectedRow(), 1).toString();          
            String benefactor = namesTable.getValueAt(namesTable.getSelectedRow(), 3).toString();     
            String timestamp = namesTable.getValueAt(namesTable.getSelectedRow(), 0).toString();

            int beneficiariesCount = 0;
            if(!benefactor.isBlank())
            {
                selectBenificiariesButton.setEnabled(true);
                for(int i = 0; i < namesTable.getRowCount(); i++)
                {
                    if(benefactor.equals(namesTable.getValueAt(i, 3).toString()))
                        beneficiariesCount++;
                }
            }
            else                    
                selectBenificiariesButton.setEnabled(false);

            if(beneficiariesCount <= 1)
                selectBenificiariesButton.setEnabled(false);

            double percentage = ((double) beneficiariesCount / namesTable.getRowCount()) * 100;                
            double scale = Math.pow(10, 2);
            percentage = Math.round(percentage * scale) / scale;

            String labelString = name + " : registered on " + timestamp;
            if(!benefactor.isBlank())
                labelString +=  "  |  Benefactor is " + benefactor + "<br/>" + "Benefactor has " + beneficiariesCount + " beneficiaries (" +
                        percentage + "% of all names)";
            selectedNameLabel.setText(Utilities.AllignCenterHTML(labelString));

        });
    }
    
    protected void initialise(GUI gui)
    {
        this.gui = gui;
        dbManager = gui.dbManager;
        fillNamesTable("TIMESTAMP", "ASC");
    }
    
    private void getAllNames()
    {
        try(Connection connection = ConnectionDB.getConnection("minters"))
        {
             if(!dbManager.TableExists("names", connection))
            {
                dbManager.CreateTable(new String[]{"names","timestamp","long","name","varchar(250)",
                    "address","varchar(100)", "benefactor","varchar(100)"}, connection);  
            }
             
            ArrayList<Object> names = dbManager.GetColumn("names", "name", "name", "asc", connection);      
            
            jsonString = Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/names?limit=0");
            jSONArray = new JSONArray(jsonString);
            System.err.println("Found " + jSONArray.length() + " registered names\n");
            
            namesProgressBar.setStringPainted(true);
            
            final int namesToUpdate = jSONArray.length() - names.size();
            int updatedCount = 0;
            
            for(int i = 0; i < jSONArray.length(); i++)
            {
                if(nameUpdateHalted)
                {
                    nameUpdateHalted = false;
                    break;
                }     
                
                jSONObject = jSONArray.getJSONObject(i);
                String name = jSONObject.getString("name");
                
                if(!names.contains(name))
                {    
                    updatedCount++;                    
                    
                    final int current = updatedCount;
                    SwingUtilities.invokeLater(()->
                    {
                        double percent = ((double) current / namesToUpdate ) * 100;
                        namesProgressBar.setValue((int)percent);   
                        namesProgressBar.setString((int) percent + "% done  |  Added " + current + " out of " + namesToUpdate + 
                                " new names found  |  Total names found : " + jSONArray.length());
                    });   
                    
                    
                    String URL_name = URLEncoder.encode(name, StandardCharsets.UTF_8.toString());
                    URL_name = URL_name.replaceAll("\\+", "%20");
                    String address = jSONObject.getString("owner");                    

                    jsonString = Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/names/" + URL_name);
                    jSONObject = new JSONObject(jsonString);
                    long timestamp = jSONObject.getLong("registered");             
                    
                    double balance =  Double.parseDouble(Utilities.ReadStringFromURL(
                        "http://" + gui.dbManager.socket + "/addresses/balance/" + address));  
                    String benefactor = "";
                    
                    if(balance < 1.0)
                    {                         
                        //if address balance is smaller than 1 qort and the last payment it received is smaller than 1 qort set the 
                        //benefactor address in the names table
                        jsonString = Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/transactions/search?txType=PAYMENT&address=" 
                                + address + "&confirmationStatus=CONFIRMED&limit=1");
                        JSONArray jSONArray2 = new JSONArray(jsonString);
                        
                        if(jSONArray2.length() > 0)
                        {
                            jSONObject = jSONArray2.getJSONObject(0);
                            if(jSONObject.getDouble("amount") < 1.0)
                                benefactor = jSONObject.getString("creatorAddress");
                        }
                    }

                    if(name.contains("'"))
                        name = name.replace("'", "''");

                    dbManager.InsertIntoDB(new String[]{"names",
                        "timestamp",String.valueOf(timestamp),
                        "name",Utilities.SingleQuotedString(name),
                        "address",Utilities.SingleQuotedString(address),
                        "benefactor",Utilities.SingleQuotedString(benefactor)}, connection);
                }
            }            
            namesUpdateStopped();
            
        }
        catch(ConnectException e)
        {
            namesUpdateStopped();     
            namesStatusLabel.setText("Could not connect to Qortal core, make sure your core is online and/or you SSH tunnel is open");       
        }
        catch (Exception e)
        {
            namesUpdateStopped();
            BackgroundService.AppendLog(e);
        }
        
        updateListButton.setEnabled(true);
    }  
    
    private void namesUpdateStopped()
    {      
        namesProgressBar.setVisible(false); 
        namesProgressBar.setStringPainted(false);
        namesProgressBar.setValue(0);

        fillNamesTable("TIMESTAMP", "ASC");
        updateListButton.setEnabled(true);
        stopNamesButton.setEnabled(false);
    }   
    
    private void fillNamesTable(String header, String orderKey)
    {
        try(Connection connection = ConnectionDB.getConnection("minters"))
        {
            dbManager.FillJTableOrder("names", header, orderKey, namesTable, connection);
        }
        catch (Exception ex)
        {
            BackgroundService.AppendLog(ex);
        }
        
        namesStatusLabel.setText("Total registered names found: " + namesTable.getRowCount());
    }
    
     private void searchForName()
    {
         if(searchInput.getText().isBlank())
            return;
         
        String address = searchInput.getText();
        int rowIndex = -1;
        
        //first search for name
        for(int i = 0; i < namesTable.getRowCount(); i++)
        {
            String rowEntry = namesTable.getValueAt(i, 1).toString();
            if(caseCheckbox.isSelected())
            {
                if(rowEntry.contains(address))
                {
                    rowIndex = i;
                    break;
                }
            }
            else
            {                
                if(rowEntry.toLowerCase().contains(address.toLowerCase()))
                {
                    rowIndex = i;
                    break;
                }
            }                
        }
        if(rowIndex == -1)
        {
            //search for address if name not found
            for(int i = 0; i < namesTable.getRowCount(); i++)
            {
                String rowEntry = namesTable.getValueAt(i, 2).toString();
                
                if(caseCheckbox.isSelected())
                {
                    if(rowEntry.contains(address))
                    {
                        rowIndex = i;
                        break;
                    }
                }
                else
                {                
                    if(rowEntry.toLowerCase().contains(address.toLowerCase()))
                    {
                        rowIndex = i;
                        break;
                    }
                }          
            }
        }
        
        if(rowIndex == -1)
        {
            namesStatusLabel.setText("Name or address containing '" + searchInput.getText() + "' not found");
            namesTable.clearSelection();
            return;
        }
        
        namesTable.setRowSelectionInterval(rowIndex, rowIndex);
        
        //scroll as close to middle as possible
        JViewport viewport = namesScrollpane.getViewport();
        Dimension extentSize = viewport.getExtentSize();   
        int visibleRows = extentSize.height/namesTable.getRowHeight();
        
        //first scroll all the way up (scrolling up to found name was not working properly)
        namesTable.scrollRectToVisible(new Rectangle(namesTable.getCellRect(0, 0, true)));   
        
        int scrollToRow = rowIndex + (visibleRows / 2);        
        if(scrollToRow >= namesTable.getRowCount())
            scrollToRow = namesTable.getRowCount() - 1;
        
        if(rowIndex <= visibleRows / 2)
            scrollToRow = 0;
        
        namesTable.scrollRectToVisible(new Rectangle(namesTable.getCellRect(scrollToRow, 0, true)));        
    }
    
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents()
    {
        java.awt.GridBagConstraints gridBagConstraints;

        namesTab = new javax.swing.JSplitPane();
        namesScrollpane = new javax.swing.JScrollPane();
        namesTable = new javax.swing.JTable();
        namesMenuScrollpane = new javax.swing.JScrollPane();
        namesMenuPanel = new javax.swing.JPanel();
        namesProgressBar = new javax.swing.JProgressBar();
        namesProgressBar.setVisible(false);
        updateListButton = new javax.swing.JButton();
        stopNamesButton = new javax.swing.JButton();
        namesStatusLabel = new javax.swing.JLabel();
        selectedNameLabel = new javax.swing.JLabel();
        jSeparator3 = new javax.swing.JSeparator();
        selectBenificiariesButton = new javax.swing.JButton();
        searchButton = new javax.swing.JButton();
        jLabel3 = new javax.swing.JLabel();
        searchInput = new javax.swing.JTextField();
        caseCheckbox = new javax.swing.JCheckBox();

        namesTab.setDividerLocation(225);
        namesTab.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        namesScrollpane.setViewportView(namesTable);

        namesTab.setBottomComponent(namesScrollpane);

        namesMenuPanel.setLayout(new java.awt.GridBagLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.ipady = 10;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.PAGE_END;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        namesMenuPanel.add(namesProgressBar, gridBagConstraints);

        updateListButton.setText("Update list");
        updateListButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                updateListButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 100);
        namesMenuPanel.add(updateListButton, gridBagConstraints);

        stopNamesButton.setText("Stop");
        stopNamesButton.setEnabled(false);
        stopNamesButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                stopNamesButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(10, 100, 0, 0);
        namesMenuPanel.add(stopNamesButton, gridBagConstraints);

        namesStatusLabel.setText("Found no registered names");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 5;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        namesMenuPanel.add(namesStatusLabel, gridBagConstraints);

        selectedNameLabel.setText("No name selected");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 5;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        namesMenuPanel.add(selectedNameLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        namesMenuPanel.add(jSeparator3, gridBagConstraints);

        selectBenificiariesButton.setText("Select all benificiaries");
        selectBenificiariesButton.setEnabled(false);
        selectBenificiariesButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                selectBenificiariesButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 5;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        namesMenuPanel.add(selectBenificiariesButton, gridBagConstraints);

        searchButton.setText("Search");
        searchButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                searchButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.insets = new java.awt.Insets(0, 400, 0, 0);
        namesMenuPanel.add(searchButton, gridBagConstraints);

        jLabel3.setText("Search for name or address");
        jLabel3.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
        namesMenuPanel.add(jLabel3, gridBagConstraints);

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
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        namesMenuPanel.add(searchInput, gridBagConstraints);

        caseCheckbox.setText("Case sensitive");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 415);
        namesMenuPanel.add(caseCheckbox, gridBagConstraints);

        namesMenuScrollpane.setViewportView(namesMenuPanel);

        namesTab.setLeftComponent(namesMenuScrollpane);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 715, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(namesTab, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 715, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 553, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(namesTab, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 553, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void updateListButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_updateListButtonActionPerformed
    {//GEN-HEADEREND:event_updateListButtonActionPerformed
        namesProgressBar.setVisible(true);
        updateListButton.setEnabled(false);
        stopNamesButton.setEnabled(true);
        nameUpdateHalted = false;
        namesStatusLabel.setText("Fetching newly registered names from blockchain...");

        Thread thread = new Thread(()->{getAllNames();});
        thread.start();
    }//GEN-LAST:event_updateListButtonActionPerformed

    private void stopNamesButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_stopNamesButtonActionPerformed
    {//GEN-HEADEREND:event_stopNamesButtonActionPerformed
        nameUpdateHalted = true;
    }//GEN-LAST:event_stopNamesButtonActionPerformed

    private void selectBenificiariesButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_selectBenificiariesButtonActionPerformed
    {//GEN-HEADEREND:event_selectBenificiariesButtonActionPerformed
        String benefactor = namesTable.getValueAt(namesTable.getSelectedRow(), 3).toString();
        namesTable.getSelectionModel().clearSelection();

        for(int i = 0; i < namesTable.getRowCount(); i++)
        {
            if(namesTable.getValueAt(i, 3).toString().equals(benefactor))
            namesTable.getSelectionModel().addSelectionInterval(i, i);
        }
    }//GEN-LAST:event_selectBenificiariesButtonActionPerformed

    private void searchButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_searchButtonActionPerformed
    {//GEN-HEADEREND:event_searchButtonActionPerformed
        searchForName();
    }//GEN-LAST:event_searchButtonActionPerformed

    private void searchInputFocusGained(java.awt.event.FocusEvent evt)//GEN-FIRST:event_searchInputFocusGained
    {//GEN-HEADEREND:event_searchInputFocusGained
        searchInput.selectAll();
    }//GEN-LAST:event_searchInputFocusGained

    private void searchInputKeyReleased(java.awt.event.KeyEvent evt)//GEN-FIRST:event_searchInputKeyReleased
    {//GEN-HEADEREND:event_searchInputKeyReleased
        searchForName();
    }//GEN-LAST:event_searchInputKeyReleased


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox caseCheckbox;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JPanel namesMenuPanel;
    private javax.swing.JScrollPane namesMenuScrollpane;
    private javax.swing.JProgressBar namesProgressBar;
    private javax.swing.JScrollPane namesScrollpane;
    private javax.swing.JLabel namesStatusLabel;
    private javax.swing.JSplitPane namesTab;
    private javax.swing.JTable namesTable;
    private javax.swing.JButton searchButton;
    private javax.swing.JTextField searchInput;
    private javax.swing.JButton selectBenificiariesButton;
    private javax.swing.JLabel selectedNameLabel;
    private javax.swing.JButton stopNamesButton;
    private javax.swing.JButton updateListButton;
    // End of variables declaration//GEN-END:variables
}
