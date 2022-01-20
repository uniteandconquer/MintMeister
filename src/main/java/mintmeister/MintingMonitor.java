package mintmeister;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MintingMonitor extends javax.swing.JPanel
{

    private Timer timer;
    private Timer countDownTimer;
    private Timer updateTimer;
    private int mappingDelta;
    private ArrayList<Minter> minters;
    private ArrayList<String> addresses;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private GUI gui;
    private String jsonString;
    private JSONObject jSONObject;
    private JSONArray jSONArray;
    private final int timeTreshold = 5;
    private int[] groups;
    int iterations;
    long startTime;
    private DatabaseManager dbManager;
    private String orderKey = "desc";
    private long nextIteration;
    protected boolean mappingHalted;
    protected boolean exitInitiated;
    private int populateTick;
    protected ChartMaker chartMaker;
    private JPanel chartPanel;
    private PieChart pieChart;
    private final DefaultTreeModel chartsTreeModel;
    private DefaultMutableTreeNode pieNode;
    private DefaultMutableTreeNode lineNode;
    private final int[] levels = { 0, 7200, 72000 , 201600 , 374400 ,618400 , 
        964000 , 1482400 , 2173600 , 3037600 , 4074400 };

    public MintingMonitor()
    {
        initComponents();

        minters = new ArrayList<>();
        addresses = new ArrayList<>();
        mappingDelta = minutesSlider.getValue() * 60000; //300000; 
        mappingDeltaLabel.setText("Iterate once every " + minutesSlider.getValue() + " minutes");     
        chartsTreeModel = (DefaultTreeModel) chartsTree.getModel();   
    }

    protected void initialise(GUI gui)
    {
        this.gui = gui;
        dbManager = this.gui.dbManager;
        chartMaker = new ChartMaker("", gui);
        
        fillMinterTable("MINTED_END","desc");
        setMinterRank();   
        
        fillLevellingTable();
        
        populateChartsTree();
        
        if(mintersTable.getRowCount() == 0)
            continueButton.setEnabled(false);
        
        mintersTable.getTableHeader().addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                orderKey = orderKey.equals("asc") ? "desc" : "asc";  
                int col = mintersTable.columnAtPoint(e.getPoint());
                String headerName = mintersTable.getColumnName(col);                
                fillMinterTable(headerName,orderKey);
            }
        });
        
        mintersTable.getSelectionModel().addListSelectionListener((ListSelectionEvent event) ->
        {
            if(event.getValueIsAdjusting())
                return;
            
            if(mintersTable.getSelectedRow() < 0)
                return;
            
            //first check if name not blank
            String nameOrAddress = mintersTable.getValueAt(mintersTable.getSelectedRow(), 1).toString();
            if(nameOrAddress.isBlank())
            {
                nameOrAddress = mintersTable.getValueAt(mintersTable.getSelectedRow(), 0).toString();
                String start = nameOrAddress.substring(0, 4);
                String end = nameOrAddress.substring(nameOrAddress.length() - 4, nameOrAddress.length());
                nameOrAddress = start + "..." + end;
            }
            String duration = mintersTable.getValueAt(mintersTable.getSelectedRow(), 8).toString();
            int rank_session = (int) mintersTable.getValueAt(mintersTable.getSelectedRow(), 4);
            int rank_all_time = (int) mintersTable.getValueAt(mintersTable.getSelectedRow(), 5);
            int level = (int)mintersTable.getValueAt(mintersTable.getSelectedRow(), 3);
            int bph = (int) mintersTable.getValueAt(mintersTable.getSelectedRow(), 2);
            int minted = (int)mintersTable.getValueAt(mintersTable.getSelectedRow(), 9);
            String levelTime = mintersTable.getValueAt(mintersTable.getSelectedRow(),12).toString();
            String levelDuration = mintersTable.getValueAt(mintersTable.getSelectedRow(), 13).toString();
            if(level < 10)
            {
                int blocksLeft = levels[level + 1] - (int)mintersTable.getValueAt(mintersTable.getSelectedRow(), 11);
            
                minterInfoLabel.setText(String.format("<html><div style='text-align: center;'>"
                        + "%s : rank all time %d  |  rank session %d  |  level %d  |  %d blocks/hour  |  minted %d blocks in %s<br/>"
                        + "%s blocks to next level  |  reaches level %d in %s, on %s (based on session blocks/hour)</div></html>",
                        nameOrAddress,rank_all_time, rank_session,level,bph,minted,duration,Utilities.integerFormat(blocksLeft),level + 1,levelDuration,levelTime));                
            }
            else
                minterInfoLabel.setText(String.format("%s : rank all time %d  |  rank session %d  |  level %d  |"
                        + "  %d blocks/hour  |  minted %d blocks in %s",
                        nameOrAddress,rank_all_time, rank_session,level,bph,minted,duration));                
            
        });
        
         //Used this listener to add double click functionality. Private class TreeSelector (under GUI class) is obsolete if we keep this method
        MouseListener ml = new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                int selRow = chartsTree.getRowForLocation(e.getX(), e.getY());
                TreePath selPath = chartsTree.getPathForLocation(e.getX(), e.getY());
                if (selRow != -1)
                {
                    if (e.getClickCount() == 1)
                    {
                        chartsTreeSelected(selPath, false);
                    }
                    else if (e.getClickCount() == 2)
                    {
                        chartsTreeSelected(selPath, true);
                    }
                }
            }
        };
        chartsTree.addMouseListener(ml);
        
        updateTimer = new Timer();
        updateTimer.scheduleAtFixedRate(new TimerTask()
        {
            @Override
            public void run()
            {
                updateDataTimeLabels();
            }
        }, 0, 60000);
        
    }//end initialise()
    
    private void updateDataTimeLabels()
    {
        try (Connection connection = ConnectionDB.getConnection("minters"))
        {            
            long lastUpdate = (long) dbManager.GetColumn("minters", "timestamp_end", "timestamp_end", "desc", connection).get(0);
            long timeLeft = System.currentTimeMillis() - lastUpdate;
            String labelText = String.format("Based on latest data from %s (%s ago)",
                    Utilities.DateFormatShort(lastUpdate),Utilities.MillisToDayHrMinShort(timeLeft));
            labelText += timeLeft > 600000 ? "  |  For the most recent data, restart your mapping session" :  "";
            dataTimeLabel.setText(labelText);
            dataTimeLabel1.setText(labelText);
        }
        catch(Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }
    
    private void fillMinterTable(String headerName,String order)
    {
        File dbFile = new File(System.getProperty("user.dir") + "/minters/minters.mv.db");
        
        if (!dbFile.exists())
            ConnectionDB.CreateDatabase("minters");    

        try (Connection connection = ConnectionDB.getConnection("minters"))
        {                        
            if (dbManager.TableExists("minters", connection))
            {        
                dbManager.FillJTableOrder("minters", headerName,order, mintersTable, connection);
            }
            else
            {           
                    dbManager.CreateTable(new String[]{"minters","address","varchar(100)","name","varchar(100)",
                                "blocks_hour","int","level","int","rank_session","int","rank_all_time","int", "timestamp_start","long",
                                "timestamp_end","long","duration","long","minted_session","int", "minted_start","int","minted_end","int",
                                "level_timestamp","long","level_duration","long"}, connection);
            } 
            
            //the following 2 tables are used to store some data to track network growth/progress over time
            if(!dbManager.TableExists("minters_data", connection))
            {
                dbManager.CreateTable(new String[]{"minters_data","timestamp","long","minters_count","int","names_registered","int",
                    "avg_bph","double","total_minted_network","long","level_ups","int"}, connection);  
            }
            if(!dbManager.TableExists("levels_data", connection))
            {
                dbManager.CreateTable(new String[]{"levels_data","timestamp", "long","level","int","count","int",
                    "names_registered","int","avg_bph","double","total_minted_level","long"}, connection);               
            }
            
            ArrayList<Object> durations = dbManager.GetColumn("minters", "duration", "duration", "desc", connection);
            
            long duration = (long) durations.get(0);
            long start = (long) dbManager.GetItemValue("minters", "timestamp_start", "duration", String.valueOf(duration), connection);
            long end = (long) dbManager.GetItemValue("minters", "timestamp_end", "duration", String.valueOf(duration), connection);
                        
            listInfoLabel.setText(String.format("<html><div style='text-align: center;'>"
                    + "Session info:<br/><br/></div>"
                    + "Session start : %s<br/>"
                    + "Session end  : %s<br/>"
                    + "<div style='text-align: center;'>"
                    + "Duration : %s<br/>"
                    + "Total minters : %d</div></html>",
                    Utilities.DateFormatShort(start),
                    Utilities.DateFormatShort(end),
                    Utilities.MillisToDayHrMin(duration),
                    mintersTable.getRowCount()));
            
            listInfoLabel1.setText(String.format(
                    "Session info |  Start : %s  |  End : %s  |  Duration : %s  |  Total minters : %d",
                    Utilities.DateFormatShort(start),
                    Utilities.DateFormatShort(end),
                    Utilities.MillisToDayHrMin(duration),
                    mintersTable.getRowCount()));
        }
        catch(Exception e)
        {
           BackgroundService.AppendLog(e);
        }    
        
        updateDataTimeLabels();
    }

    private void startMapping()
    {            
        if (timer == null)
        {
            timer = new Timer();
        }
        else
        {
            timer.cancel();
            timer = new Timer();
        }
        
        timer.scheduleAtFixedRate(new TimerTask()
        {
            @Override
            public void run()
            {
                try
                {   
                    if(countDownTimer != null)
                        countDownTimer.cancel();

                    iterations++;
                    long currentTime = System.currentTimeMillis();
                    long mappingTime = currentTime - startTime;
                    int totalBph = 0;
                    long totalBlocks = 0;
                    int levelUps = 0;
                    int[] levelCount = new int[10];
                    int[] levelBph = new int[10];
                    int[] levelNamesReg = new int[10];
                    int[] levelTotalMinted = new int[10];
                    
                    appendText(String.format("----------------------------------------------\n\n"
                            + "Starting iteration %d at %s\nTotal mapping time is %s\n\n", iterations,Utilities.DateFormat(currentTime),
                            Utilities.MillisToDayHrMinSec(mappingTime)));
                    
                    //If ReadStringFromURL throws an error, timer is cancelled
                    Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/admin/status");

                    populateAddressesList();                       

                    groups = new int[8];

                    if(!mappingHalted)
                        appendText("Fetching blocks minted data for all minters in list...\n\n");

                    try(Connection connection = ConnectionDB.getConnection("minters"))
                    {
                        createTempTable(connection);

                        int namesCount = 0;

                        for(int i = 0; i < minters.size();i++)
                        {     
                            //just cancelling the timer will not end this loop but only the next iteration of the timer
                            if(mappingHalted)
                            {
                                SwingUtilities.invokeLater(()->
                                {                               
                                    mappingHalted = false;
                                    stopMapping();
                                });
                                return;
                            }

                            currentTime = System.currentTimeMillis();

                            final int current = i;
                            SwingUtilities.invokeLater(()->
                            {
                                double percent = ((double) current / minters.size()) * 100;
                                progressBar.setValue((int)percent);   
                                progressBar.setString((int) percent + "%");
                            });                                

                            Minter minter = minters.get(i);
                            jsonString = Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/addresses/" + minter.address);
                            jSONObject = new JSONObject(jsonString);
                            int blocksMinted = jSONObject.getInt("blocksMinted");
                            int level = jSONObject.getInt("level");
                            if(level > minter.level)
                            {
                                levelUps++;
                                minter.level = level;
                            }
                            levelCount[level - 1]++;
                            totalBlocks += blocksMinted;
                            levelTotalMinted[level - 1] += blocksMinted;

                            long timePassedMinutes = (currentTime - minter.timestampStart) / 60000;

                            double bph = ((double) (blocksMinted - minter.blocksMintedStart) / timePassedMinutes) * 60;                                
                            minter.blocksPerHour = (int) bph ;

                            //escape single quote for H2 varchar entry
                            String name = minter.name;
                            if(name.contains("'"))
                                name = name.replace("'", "''");
                            
                            int mintedSession = blocksMinted - minter.blocksMintedStart;
                            double millisecLeft = 0;
                            long timestampLevelUp = 0;
                            if(mintedSession > 10 && bph > 0 && bph <= 60)
                            {
                                if(level < 10)
                                {
                                    int blocksLeft = levels[level + 1] - blocksMinted;
                                    double hoursLeft = ((double) blocksLeft / (int)bph);
                                    millisecLeft = hoursLeft * 3600000;
                                    
                                    timestampLevelUp = System.currentTimeMillis() + (long)millisecLeft;
                                }  
                                else
                                {
                                    timestampLevelUp = currentTime;
                                }
                            }

                            dbManager.InsertIntoDB(
                                    new String[]{"minters_temp",
                                        "address", Utilities.SingleQuotedString(minter.address),
                                        "name", Utilities.SingleQuotedString(name),
                                        "blocks_hour", String.valueOf((int) bph),
                                        "level", String.valueOf(minter.level),
                                        "timestamp_start", String.valueOf(minter.timestampStart),
                                        "timestamp_end", String.valueOf(currentTime),
                                        "duration", String.valueOf(currentTime - minter.timestampStart),
                                        "minted_session", String.valueOf(mintedSession),
                                        "minted_start", String.valueOf(minter.blocksMintedStart),
                                        "minted_end", String.valueOf(blocksMinted),
                                        "level_timestamp", String.valueOf(timestampLevelUp),
                                        "level_duration",String.valueOf((long)millisecLeft)}, connection);                     

                            if(!minter.name.isBlank())
                            {
                                namesCount++;
                                levelNamesReg[level - 1]++;
                            }

                            //add recently added minters to uncounted group
                            if(timePassedMinutes < timeTreshold)
                            {
                                groups[7]++;
                                continue;
                            }
                            //add minters with 0 bph to anomalies
                            if(bph == 0)
                            {
                                groups[0]++;
                                continue;
                            }
                            
                            //add all round decimal integers to group below
                            if((int)bph > 0 && (int)bph < 61 && (int)bph % 10 == 0)
                                bph -= 1;
                            
                            //if blocksminted - blocksmintedStart is below 0 add to anomalies
                            int group = (int) bph / 10 >= 0 ? (int) bph / 10 : 0;
                            group += 1; // 0-10 = 1 | 11-20 = 2 etc...
                            
                            if(group > 6)
                                groups[0]++;
                            else
                            {
                                levelBph[level - 1] += minter.blocksPerHour;
                                totalBph += minter.blocksPerHour;
                                groups[group]++;
                            }       

                        }//end for (minters)
                        
                        progressBar.setValue(0);

                        appendText(String.format(
                                "Blocks per hour:\n\n"
                                 + "  1 - 10 : %d (%.2f%%)\n"
                                 + "11 - 20 : %d (%.2f%%)\n"
                                 + "21 - 30 : %d (%.2f%%)\n"
                                 + "31 - 40 : %d (%.2f%%)\n"
                                 + "41 - 50 : %d (%.2f%%)\n"
                                 + "51 - 60 : %d (%.2f%%)\n\n"
                                 + "anomalies : %d (%.2f%%) (irregular data)\n"
                                 + "uncounted : %d (%.2f%%) (not enough data)\n\n"
                                 + "Names registered : %d out of %d minters (%.2f%%)\n\n", 
                                groups[1],(double) (((double)groups[1] / minters.size()) * 100),
                                groups[2],(double) (((double)groups[2] / minters.size()) * 100),
                                groups[3],(double) (((double)groups[3] / minters.size()) * 100),
                                groups[4],(double) (((double)groups[4] / minters.size()) * 100),
                                groups[5],(double) (((double)groups[5] / minters.size()) * 100),
                                groups[6],(double) (((double)groups[6] / minters.size()) * 100),
                                groups[0],(double) (((double)groups[0] / minters.size()) * 100),  
                                groups[7],(double) (((double)groups[7] / minters.size()) * 100) ,
                                namesCount,minters.size(),((double) namesCount / minters.size()) * 100
                        ));

                       nextIteration = currentTime + mappingDelta;
                       appendText(String.format("Next iteration in %s at %s\n\n"
                                + "----------------------------------------------\n\n", Utilities.MillisToDayHrMinSec(mappingDelta),
                                Utilities.DateFormatShort(nextIteration)));

                        dbManager.ExecuteUpdate("drop table minters", connection);
                        dbManager.ExecuteUpdate("alter table minters_temp rename to minters", connection);

                        dbManager.FillJTableOrder("minters", "minted_end","desc", mintersTable, connection);
                        setMinterRank();
                        fillLevellingTable();
                        
                        double avgBph = ((double) totalBph / (minters.size() - groups[0] - groups[7]));
                        //round to 2 decimals
                        double scale = Math.pow(10, 2);
                        avgBph = Math.round(avgBph * scale) / scale;
               
                        dbManager.InsertIntoDB(new String[]{"minters_data",
                            "timestamp",String.valueOf(System.currentTimeMillis()),
                            "minters_count",String.valueOf(minters.size()),
                            "avg_bph",String.valueOf(avgBph),
                            "total_minted_network",String.valueOf(totalBlocks),
                            "names_registered",String.valueOf(namesCount),
                            "level_ups",String.valueOf(levelUps)}, connection);
                        
                        for(int i = 0; i < levelCount.length; i++)
                        {   
                            double avgBphLvl = ((double) levelBph[i] / levelCount[i]);
                             //round to 2 decimals
                            avgBphLvl = Math.round(avgBphLvl * scale) / scale;
                            
                            dbManager.InsertIntoDB(new String[]{"levels_data",
                                "timestamp",String.valueOf(System.currentTimeMillis()),
                                "level",String.valueOf(i + 1),
                                "count",String.valueOf(levelCount[i]),
                                "avg_bph",String.valueOf(avgBphLvl),
                                "names_registered",String.valueOf(levelNamesReg[i]),
                                "total_minted_level",String.valueOf(levelTotalMinted[i])}, connection);                            
                        }                   
                        
                        startCountDown();
                    }      
                }
                catch (ConnectException e)
                {
                    textArea.setText("Could not connect to Qortal core.\n\nMake sure your core is running and/or your SHH tunnel is active\n\n"
                            + "Make sure your 'customIP' and 'customPort' values are correct in the 'MintMeister/bin/settings.json' file.\n\n"
                            + "Default IP is \"localhost\"\nDefault port is \"12391\"\n\nDeleting 'settings.json' will reset all settings to default");
                    stopMapping();
                }
                catch (IOException | SQLException | TimeoutException | JSONException e)
                {
                    BackgroundService.AppendLog(e);
                }                
            }
        }, 0, mappingDelta);
    }
    
    private void startCountDown()
    {
        populateTick = 0;
        
        if (countDownTimer == null)
        {
            countDownTimer = new Timer();
        }
        else
        {
            countDownTimer.cancel();
            countDownTimer = new Timer();
        }
        
        countDownTimer.scheduleAtFixedRate(new TimerTask()
        {
            @Override
            public void run()
            {
                if(mappingHalted)
                {
                    mappingHalted = false;
                    countDownTimer.cancel();
                    stopMapping();
                    return;
                }
                
                populateTick = populateTick == 179 ? 0 : populateTick + 1;
                //don't search for new addresses if less than 3 minutes to next iteration to aviod concurrent api calls
                if(populateTick == 178 && nextIteration - System.currentTimeMillis() > 180000)
                {
                    populateAddressesList();
                    System.gc();
                }
                
                long timeLeft = nextIteration - System.currentTimeMillis();
                double percent = ((double) timeLeft / mappingDelta) * 100;
                progressBar.setValue((int)percent);   
                progressBar.setString("Next iteration in " + Utilities.MillisToDayHrMinSec(timeLeft));
            }
        }, 0, 1000);
    }

    private void populateAddressesList()
    {
        appendText(Utilities.TimeFormat(System.currentTimeMillis()) + "\nSearching for unknown minters on blockchain...\n");
        
        try(Connection connection = ConnectionDB.getConnection("minters"))
        {        
            jsonString = Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/addresses/online");
            if (jsonString == null)
            {
               appendText("Qortal API returned no online minters for this iteration\n");
               return;
            }
            
            jSONArray = new JSONArray(jsonString);

           appendText("Qortal API returned " + jSONArray.length() + " online minters.\n"
                    + "Fetching minters info from blockchain...\n");

            int lastAddressesCount = addresses.size();                
            long currentTime = System.currentTimeMillis();

            for (int i = 0; i < jSONArray.length(); i++)
            {
                if (mappingHalted)
                {
                    //don't set mappingHalted to false here, this method returns to startMonitor which will check for that
                    //flag and set it to false
                    return;
                }
                String address = jSONArray.getJSONObject(i).getString("minterAddress");

                //It seems the API sometimes returns duplicate addresses with the addresses/online call
                //Presumably these are accounts currently being sponsored which are returning the sponsor's address
                if (!addresses.contains(address))
                {
                    Minter minter = addMinter(address, currentTime);
                    String name = minter.name;
                    if(name.contains("'"))
                        name = name.replace("'", "''");


                    //ATTENTION: IF THERE ARE DUPLICATED IN MINTERS LIST -> THIS IS PROBABLY THE CULPRIT
                    dbManager.InsertIntoDB(
                          new String[]{"minters",
                                "address", Utilities.SingleQuotedString(minter.address),
                                "name", Utilities.SingleQuotedString(name),
                                "blocks_hour", "0",
                                "level", String.valueOf(minter.level),
                                "timestamp_start", String.valueOf(minter.timestampStart),
                                "timestamp_end", String.valueOf(minter.timestampStart),
                                "duration", "0",
                                "minted_session", "0",
                                "minted_start", String.valueOf(minter.blocksMintedStart),
                                "minted_end", String.valueOf(minter.blocksMintedStart),
                                "level_timestamp", "0",
                                "level_duration", "0"}, connection);
                }                  

                final int current = i;
                SwingUtilities.invokeLater(()->
                {
                    double percent = ((double) current / jSONArray.length()) * 100;
                    progressBar.setValue((int)percent);   
                    progressBar.setString((int) percent + "%");
                });
            }
            progressBar.setValue(0);

            if(addresses.size() - lastAddressesCount > 0)
            {
                fillMinterTable("MINTED_END","desc");
                //to know the session rank we need to iterate over the entire minted_session column
                //calling this function does this, but adds a little (negligible?) overhead by filling the table
                //in order and making db entries
                setMinterRank();
            }

           appendText(String.format(
                    "Added %d new minters. Total minters in list: %d\n\n", addresses.size() - lastAddressesCount, addresses.size()));
               
        }
        catch (ConnectException e)
        {
            textArea.setText("Could not connect to Qortal core.\n\nMake sure your core is running and/or your SHH tunnel is active\n\n"
                    + "Make sure your 'customIP' and 'customPort' values are correct in the 'MintMeister/bin/settings.json' file.\n\n"
                    + "Default IP is \"localhost\"\nDefault port is \"12391\"\n\nDeleting 'settings.json' will reset all settings to default");
            stopMapping();
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
    }
    
    private void createTempTable(Connection connection)
    {
        if(dbManager.TableExists("minters_temp", connection))
            dbManager.ExecuteUpdate("delete from minters_temp", connection);
        else
            dbManager.CreateTable(new String[]{"minters_temp","address","varchar(100)","name","varchar(100)",
                "blocks_hour","int","level","int","rank_session","int","rank_all_time","int", "timestamp_start","long",
                "timestamp_end","long","duration","long", "minted_session","int", "minted_start","int","minted_end","int",
                "level_timestamp","long","level_duration","long"}, connection);
    }
    
    private void collapseAll(JTree tree, TreePath parent)
    {
        TreeNode node = (TreeNode) parent.getLastPathComponent();
        if (node.getChildCount() >= 0)
        {
            for (Enumeration e = node.children(); e.hasMoreElements();)
            {
                TreeNode n = (TreeNode) e.nextElement();
                TreePath path = parent.pathByAddingChild(n);
                collapseAll(tree, path);
            }
        }
//    tree.expandPath(parent);
        tree.collapsePath(parent);
    }
    
    private Minter addMinter(String address, long currentTime) throws TimeoutException, IOException
    {
        jsonString = Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/addresses/" + address);
        jSONObject = new JSONObject(jsonString);
        int level = jSONObject.getInt("level");
        int blocksMinted = jSONObject.getInt("blocksMinted");

        String name = "";
        jsonString = Utilities.ReadStringFromURL("http://" + gui.dbManager.socket + "/names/address/" + address);
        JSONArray nameArray = new JSONArray(jsonString);
        if (nameArray.length() > 0)
        {
            jSONObject = nameArray.getJSONObject(0);
            name = jSONObject.getString("name");
        }

        addresses.add(address);
        Minter minter = new Minter(address, name, level,blocksMinted,currentTime);
        minters.add(minter);
        
        return minter;
    }
    
    //VERSION USING JTABLE SORTING
    private void setMinterRank()
    {
        if(mintersTable.getRowCount() == 0)
            return;
        
        try(Connection connection = ConnectionDB.getConnection("minters"))
        {
            //set all time rank
            for(int i = 0; i < mintersTable.getRowCount(); i++)
            {
                String address = mintersTable.getValueAt(i, 0).toString();                
                dbManager.ChangeValue("minters", "rank_all_time", String.valueOf(i + 1), "address", Utilities.SingleQuotedString(address), connection);
            }
            //set session rank
            //using the minted session variable instead of bph as rank weeds out minters that have a very high
            //bph due to just joining the list. Using dbManager fill to make sure order is descending
            dbManager.FillJTableOrder("minters", "minted_session", "desc", mintersTable, connection);
            int rank = 1;
            int lastMintedSession = (int)mintersTable.getValueAt(0, 9);
            for(int i = 0; i < mintersTable.getRowCount(); i++)
            {
                //minters share rank if same minted session value
                if(lastMintedSession > (int)mintersTable.getValueAt(i, 9))
                {
                    rank++;
                    lastMintedSession = (int)mintersTable.getValueAt(i, 9);
                }                
                
                String address = mintersTable.getValueAt(i, 0).toString();                
                dbManager.ChangeValue("minters", "rank_session", String.valueOf(rank), "address", Utilities.SingleQuotedString(address), connection);
            }
        }
        catch(Exception e)
        {
            BackgroundService.AppendLog(e);
        }
        
        //update table after setting rank in db
        fillMinterTable("MINTED_END","desc");
    }
    
    private void fillLevellingTable()
    {
        try(Connection connection = ConnectionDB.getConnection("minters"))
        {
            int maxLevel = (int)dbManager.GetColumn("minters", "level", "level", "desc", connection).get(0);  
            
            Statement statement;
            ResultSet resultSet;
            
            DefaultTableModel model = (DefaultTableModel) levellingTable.getModel();
            model.setRowCount(0);
            model.addRow(new Object[]{});
            
            int currentRow = 0;
            int mintersPerLevel = mintersPerLevelSlider.getValue() + 1;
            for(int i = 1; i <= maxLevel; i++)
            {
                statement = connection.createStatement();
                resultSet = statement.executeQuery("select * from minters where level=" + i + " order by level_duration");
                
                while(resultSet.next())
                {
                    long duration = resultSet.getLong("level_duration");
                    if(duration == 0)
                        continue;
                    
                    currentRow++;
                    
                    if(currentRow % mintersPerLevel == 0)
                    {                        
                        //add empty rows between levels
                        model.addRow(new Object[]{});
                        model.addRow(new Object[]{});
                        break;
                    }
                    
                    int level = resultSet.getInt("level");
                    
                    if(level == 10)
                        break;
                    
                    int blocksLeft = levels[level + 1] - resultSet.getInt("minted_end");
                    
                    //using strings for x allignment purposes
                    model.addRow(new Object[]
                    {
                        resultSet.getString("address"),
                        resultSet.getString("name"),
                        String.valueOf(level),
                        String.valueOf(resultSet.getInt("blocks_hour")),
                        String.valueOf(Utilities.integerFormat(blocksLeft)),
                        Utilities.MillisToDayHrMinMinter(resultSet.getLong("level_duration")),
                        Utilities.DateFormat(resultSet.getLong("level_timestamp"))
                    });
                }
            }                
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
        
        updateDataTimeLabels();
    }    
       
     //populate lists in memory from database file
    private void populateMintersFromFile(boolean newSession)
    {        
        executor.execute(()->
        {
            try(Connection connection = ConnectionDB.getConnection("minters"))
            {        
                File dbFile = new File(System.getProperty("user.dir") + "/minters/minters.mv.db");
                appendText("Fetching known minters from file: " + dbFile.getPath() + "\n");
        
                if(mintersTable.getRowCount() == 0)
                {
                    appendText("Found no minters on file.\n\n");
                }
                else
                {
                    addresses = new ArrayList<>();
                    minters = new ArrayList<>();
                    
                    if(newSession)
                        appendText("Found " + mintersTable.getRowCount() + " minters on file.\nUpdating minters info from blockchain...\n\n");
                    else
                        appendText("Found " + mintersTable.getRowCount() + " minters on file.\n\n");
                      
                    final int size = mintersTable.getRowCount();
                    
                    for(int i = 0; i < mintersTable.getRowCount(); i++)
                    {
                        long currentTime = System.currentTimeMillis();             
                           if(mappingHalted)
                           {
                               SwingUtilities.invokeLater(()->
                               {                               
                                   mappingHalted = false;
                                   addresses = new ArrayList<>();
                                   minters = new ArrayList<>();
                                   stopMapping();
                               });
                               return;
                           }

                           String address = mintersTable.getValueAt(i, 0).toString();

                           if(newSession)
                               addMinter((String)address, currentTime);
                           else
                           {         
                               String name = mintersTable.getValueAt(i, 1).toString();
                               int level = (int) mintersTable.getValueAt(i, 3);
                               int mintedStart = (int)mintersTable.getValueAt(i, 10);
                               //timestamp is stored as String in table, we need a long
                               long sessionStartTime = (long) dbManager.GetItemValue("minters", "timestamp_start", "address",
                                       Utilities.SingleQuotedString(address), connection);                               
                               
                               addresses.add(address);
                               minters.add(new Minter(address, name, level,mintedStart,sessionStartTime));
                           }

                           //local variable cannot be used in lambda expression
                           final int current = i;

                           if(newSession)
                           {
                               SwingUtilities.invokeLater(()->
                                {
                                    double percent = ((double) current / size) * 100;
                                    progressBar.setValue((int)percent);   
                                    progressBar.setString((int) percent + "%");
                                });
                           }
                           
                       progressBar.setValue(0);                       
                    }                    
                }  
                
                startTime = System.currentTimeMillis();
                startMapping();
            }
            catch (ConnectException e)
            {
                stopMapping();
                textArea.setText("Could not connect to Qortal core.\n\nMake sure your core is running and/or your SHH tunnel is active\n\n"
                        + "Make sure your 'customIP' and 'customPort' values are correct in the 'MintMeister/bin/settings.json' file.\n\n"
                        + "Default IP is \"localhost\"\nDefault port is \"12391\"\n\nDeleting 'settings.json' will reset all settings to default");
            }
            catch (Exception e)
            {
                BackgroundService.AppendLog(e);
            }
        });     
    }
    
    private void setUiComponentsEnabled(boolean isMapping)
    {    
        BackgroundService.ISMAPPING = isMapping;
        startButton.setEnabled(!isMapping);
        continueButton.setEnabled(!isMapping);
        stopButton.setEnabled(isMapping);
        hoursSlider.setEnabled(!isMapping);
        minutesSlider.setEnabled(!isMapping);
    }
    
    protected void populateChartsTree()
    {
        int maxLevel = 0;
        try(Connection connection = ConnectionDB.getConnection("minters"))
        {
            maxLevel = (int)dbManager.GetColumn("minters", "level", "level", "desc", connection).get(0);                
        }
        catch(Exception e)
        {
            BackgroundService.AppendLog(e);
        }
        
        //root node is set to not visible
        DefaultMutableTreeNode chartsNode = 
                new DefaultMutableTreeNode(new NodeInfo("Charts", ""));
        chartsTreeModel.setRoot(chartsNode);        
        
        DefaultMutableTreeNode pieChartsNode = 
                new DefaultMutableTreeNode(new NodeInfo("Pie charts", "pie-chart.png"));
        pieNode = pieChartsNode;
        chartsNode.add(pieChartsNode);
        DefaultMutableTreeNode lineChartsNode = 
                new DefaultMutableTreeNode(new NodeInfo("Line charts", "line-chart.png"));
        lineNode = lineChartsNode;
        chartsNode.add(lineChartsNode);
        
        //select bph piechart for all levels by default
        DefaultMutableTreeNode selectedNode = null;
        
        DefaultMutableTreeNode[] mainNodes = new DefaultMutableTreeNode[2];
        mainNodes[0]  = pieChartsNode;
        mainNodes[1] = lineChartsNode;
        
        for(int i = 0; i < mainNodes.length;i++)
        {
            DefaultMutableTreeNode chartTypeNode = mainNodes[i];
            
            DefaultMutableTreeNode allLevelsNode = 
                    new DefaultMutableTreeNode(new NodeInfo("All levels", ""));
            chartTypeNode.add(allLevelsNode);     
            
            //pie charts
            if(i == 0)
            {                
                DefaultMutableTreeNode bphNode = 
                        new DefaultMutableTreeNode(new NodeInfo("Blocks per hour", "arrow-right.png"));
                allLevelsNode.add(bphNode);
                DefaultMutableTreeNode bphTotalNode = 
                        new DefaultMutableTreeNode(new NodeInfo("Blocks per hour network", "arrow-right.png"));
                allLevelsNode.add(bphTotalNode);
                DefaultMutableTreeNode bphDistNode = 
                        new DefaultMutableTreeNode(new NodeInfo("Blocks per hour distribution", "arrow-right.png"));
                allLevelsNode.add(bphDistNode);
                DefaultMutableTreeNode namesNode = 
                        new DefaultMutableTreeNode(new NodeInfo("Registered names per level", "arrow-right.png"));
                allLevelsNode.add(namesNode);
                DefaultMutableTreeNode namesNetworkNode = 
                        new DefaultMutableTreeNode(new NodeInfo("Registered names network", "arrow-right.png"));
                allLevelsNode.add(namesNetworkNode);
                DefaultMutableTreeNode levelDistNode = 
                        new DefaultMutableTreeNode(new NodeInfo("Level distribution", "arrow-right.png"));
                allLevelsNode.add(levelDistNode);
                
                selectedNode = bphNode;
                
                for(int i2 = 1; i2 <= maxLevel; i2++)
                {                
                    DefaultMutableTreeNode levelNode = 
                            new DefaultMutableTreeNode(new NodeInfo("Level " + i2, ""));
                    chartTypeNode.add(levelNode);    
                                  
                    bphNode = 
                            new DefaultMutableTreeNode(new NodeInfo("Blocks per hour", "arrow-right.png"));
                    levelNode.add(bphNode);
                    namesNetworkNode = 
                            new DefaultMutableTreeNode(new NodeInfo("Registered names", "arrow-right.png"));
                    levelNode.add(namesNetworkNode);
                    
                    //no level up for level 10
                    if(i2 == 10)
                        continue;
                    
                    DefaultMutableTreeNode levelUpNode = 
                            new DefaultMutableTreeNode(new NodeInfo("Level-up duration", "arrow-right.png"));
                    levelNode.add(levelUpNode);                    
                }
            }
            //iine charts
            else
            {
                DefaultMutableTreeNode mintersCountLine = 
                        new DefaultMutableTreeNode(new NodeInfo("Minters count", "arrow-right.png"));
                allLevelsNode.add(mintersCountLine);
                DefaultMutableTreeNode regNamesCountLine = 
                        new DefaultMutableTreeNode(new NodeInfo("Registered names count", "arrow-right.png"));
                allLevelsNode.add(regNamesCountLine);
                DefaultMutableTreeNode avgBphLines = 
                        new DefaultMutableTreeNode(new NodeInfo("Average blocks/hour", "arrow-right.png"));
                allLevelsNode.add(avgBphLines);
                DefaultMutableTreeNode blocksMintedLine = 
                        new DefaultMutableTreeNode(new NodeInfo("Total blocks minted", "arrow-right.png"));
                allLevelsNode.add(blocksMintedLine);
                DefaultMutableTreeNode levelUpsLine = 
                        new DefaultMutableTreeNode(new NodeInfo("Level-ups count", "arrow-right.png"));
                allLevelsNode.add(levelUpsLine);          
                
                for(int i2 = 1; i2 <= maxLevel; i2++)
                {                
                    DefaultMutableTreeNode levelNode = 
                            new DefaultMutableTreeNode(new NodeInfo("Level " + i2, ""));
                    chartTypeNode.add(levelNode);                    
                    
                    mintersCountLine = 
                        new DefaultMutableTreeNode(new NodeInfo("Minters count", "arrow-right.png"));
                    levelNode.add(mintersCountLine);
                    regNamesCountLine = 
                            new DefaultMutableTreeNode(new NodeInfo("Registered names count", "arrow-right.png"));
                    levelNode.add(regNamesCountLine);
                    avgBphLines = 
                            new DefaultMutableTreeNode(new NodeInfo("Average blocks/hour", "arrow-right.png"));
                    levelNode.add(avgBphLines);
                    blocksMintedLine = 
                            new DefaultMutableTreeNode(new NodeInfo("Total blocks minted", "arrow-right.png"));
                    levelNode.add(blocksMintedLine);
                }
            }
        }       

        chartsTreeModel.reload();
        //select bph piechart for all levels by default
        if(selectedNode != null)
        {            
            TreePath path = new TreePath(selectedNode.getPath());
            chartsTree.setSelectionPath(path);
            chartsTreeSelected(path, false);
        }
        gui.ExpandTree(chartsTree, 1);

    }
    
    private void chartsTreeSelected(TreePath treePath, boolean doubleClicked)
    {
        if(treePath == null)
            return;
        try(Connection connection = ConnectionDB.getConnection("minters"))
        {
            String selected = treePath.getLastPathComponent().toString();
            String levelType = treePath.getPath()[treePath.getPath().length - 2].toString();
            String chartType = treePath.getPath()[treePath.getPath().length - 3].toString();
            ResultSet resultSet;
            
            switch (treePath.getPath().length)
            {            
                case 4:  
                    int dividerLocation = chartsTab.getDividerLocation();
                    switch(chartType)
                    {
                        case "Line charts":
                            resultSet = getLineChartResultSet(selected,levelType,connection);
                            if(resultSet != null)
                            {
                                chartPanel = chartMaker.createMintersLineChartPanel(selected,levelType,resultSet);
                                chartsTab.setRightComponent(chartPanel);                                     
                            }
                            break;
                        case "Pie charts":                           
                            int maxLevel = (int)dbManager.GetColumn("minters", "level", "level", "desc", connection).get(0);   
                            long tableTime = (long)dbManager.GetColumn("minters", "timestamp_end", "timestamp_end", "desc", connection).get(0);   
                            pieChart = new PieChart(selected,levelType,maxLevel,tableTime,mintersTable);
                            chartsTab.setRightComponent(pieChart.chartPanel);
                            break;
                    }   
                    chartsTab.setDividerLocation(dividerLocation);
                break;
            }    
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }      
    }
    
    private ResultSet getLineChartResultSet(String selected, String levelType, Connection connection)
    {        
        try
        {
            Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);                        
           String level  = levelType.replaceAll("[^0-9]", "");
            switch(selected)
            {
                case "Minters count":
                    if(levelType.equals("All levels"))
                        return statement.executeQuery("select timestamp,minters_count from minters_data");
                    else
                        return statement.executeQuery("select timestamp,count from levels_data where level=" + level);
                case "Registered names count":
                    if(levelType.equals("All levels"))
                        return statement.executeQuery("select timestamp,names_registered from minters_data");
                    else
                        return statement.executeQuery("select timestamp,names_registered from levels_data where level=" + level);
                case "Average blocks/hour":
                    if(levelType.equals("All levels"))
                        return statement.executeQuery("select timestamp,avg_bph from minters_data");
                    else
                        return statement.executeQuery("select timestamp,avg_bph from levels_data where level=" + level);
                case "Total blocks minted":
                    if(levelType.equals("All levels"))
                        return statement.executeQuery("select timestamp,total_minted_network from minters_data");
                    else
                        return statement.executeQuery("select timestamp,total_minted_level from levels_data where level=" + level);
                case "Level-ups count":
                    if(levelType.equals("All levels"))
                        return statement.executeQuery("select timestamp,level_ups from minters_data");
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
            BackgroundService.AppendLog(e);
        }     
        
        return null;
    }    
    
    private void stopMapping()
    {
        if(exitInitiated)
            System.exit(0);
        
        if(timer != null)
            timer.cancel();
        if(countDownTimer != null)
            countDownTimer.cancel();
        
        setUiComponentsEnabled(false);
        
        progressBar.setValue(0);
        progressBar.setStringPainted(false);        
    }
    
    private void appendText(String text)
    {
        textArea.append(text);
        if(scrollCheckbox.isSelected())
            textArea.setCaretPosition(textArea.getText().length());
    }
    
    private void searchForMinter()
    {
         if(searchInput.getText().isBlank())
            return;
         
        String address = searchInput.getText();
        int rowIndex = -1;
        
        //first search for name
        for(int i = 0; i < mintersTable.getRowCount(); i++)
        {
            String rowEntry = mintersTable.getValueAt(i, 1).toString();
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
            for(int i = 0; i < mintersTable.getRowCount(); i++)
            {
                String rowEntry = mintersTable.getValueAt(i, 0).toString();
                
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
            minterInfoLabel.setText("Minter '" + searchInput.getText() + "' not found");
            mintersTable.clearSelection();
            return;
        }
        
        mintersTable.setRowSelectionInterval(rowIndex, rowIndex);
        
        //scroll as close to middle as possible
        JViewport viewport = mintersListTab.getViewport();
        Dimension extentSize = viewport.getExtentSize();   
        int visibleRows = extentSize.height/mintersTable.getRowHeight();
        
        //first scroll all the way up (scrolling up to found name was not working properly)
        mintersTable.scrollRectToVisible(new Rectangle(mintersTable.getCellRect(0, 0, true)));   
        
        int scrollToRow = rowIndex + (visibleRows / 2);        
        if(scrollToRow >= mintersTable.getRowCount())
            scrollToRow = mintersTable.getRowCount() - 1;
        
        if(rowIndex <= visibleRows / 2)
            scrollToRow = 0;
        
        mintersTable.scrollRectToVisible(new Rectangle(mintersTable.getCellRect(scrollToRow, 0, true)));        
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

        chartsMenuPanel = new javax.swing.JPanel();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        minterMappingTab = new javax.swing.JSplitPane();
        textPanel = new javax.swing.JPanel();
        textAreaScrollPane = new javax.swing.JScrollPane();
        textArea = new javax.swing.JTextArea();
        progressBar = new javax.swing.JProgressBar();
        mapperMenuScrollPane = new javax.swing.JScrollPane();
        mapperMenuPanel = new javax.swing.JPanel();
        startButton = new javax.swing.JButton();
        scrollCheckbox = new javax.swing.JCheckBox();
        minutesSlider = new javax.swing.JSlider();
        mappingDeltaLabel = new javax.swing.JLabel();
        saveListButton = new javax.swing.JButton();
        listInfoLabel = new javax.swing.JLabel();
        stopButton = new javax.swing.JButton();
        continueButton = new javax.swing.JButton();
        loadMintersButton = new javax.swing.JButton();
        hoursSlider = new javax.swing.JSlider();
        hoursSlider.setVisible(false);
        minterListTab = new javax.swing.JSplitPane();
        minterMenuScrollPane = new javax.swing.JScrollPane();
        minterMenuScrollPane.getHorizontalScrollBar().setUnitIncrement(10);
        minterMenuPanel = new javax.swing.JPanel();
        listMenuPanel = new javax.swing.JPanel();
        searchInput = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        searchButton = new javax.swing.JButton();
        caseCheckbox = new javax.swing.JCheckBox();
        listInfoLabel1 = new javax.swing.JLabel();
        labelPanel = new javax.swing.JPanel();
        minterInfoLabel = new javax.swing.JLabel();
        dataTimeLabel1 = new javax.swing.JLabel();
        mintersListTab = new javax.swing.JScrollPane();
        mintersTable = new javax.swing.JTable();
        chartsTab = new javax.swing.JSplitPane();
        chartPanelPlaceHolder = new javax.swing.JPanel();
        chartsTreeSplitPane = new javax.swing.JSplitPane();
        chartsTreeMenu = new javax.swing.JPanel();
        expandButton = new javax.swing.JButton();
        collapseButton = new javax.swing.JButton();
        chartsTreeScrollPane = new javax.swing.JScrollPane();
        chartsTree = new javax.swing.JTree();
        chartsTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        levelUpsTab = new javax.swing.JSplitPane();
        levellingTableScrollPane = new javax.swing.JScrollPane();
        levellingTable = new javax.swing.JTable();
        levelUpsMenuMain = new javax.swing.JPanel();
        levelUpsMenuUpper = new javax.swing.JPanel();
        mintersPerLevelSlider = new javax.swing.JSlider();
        jLabel2 = new javax.swing.JLabel();
        mintersPerLevelLabel = new javax.swing.JLabel();
        levelUpsMenuLower = new javax.swing.JPanel();
        dataTimeLabel = new javax.swing.JLabel();

        chartsMenuPanel.setLayout(new java.awt.GridBagLayout());

        minterMappingTab.setDividerLocation(300);

        textPanel.setLayout(new java.awt.GridBagLayout());

        textArea.setEditable(false);
        textArea.setColumns(20);
        textArea.setFont(new java.awt.Font("Lucida Sans Unicode", 0, 12)); // NOI18N
        textArea.setLineWrap(true);
        textArea.setRows(5);
        textArea.setText("Welcome to MintMeister.\n\nThis program uses the Qortal API to query the blockchain on your Qortal node in order to find all known minters on the network. The app comes with a pre-mapped list of minters, but it will always scan for more unknown minters during mapping sessions.\n\nThe 'minters online' API call doesn't always return all known minters, but MintMeister will remember every minter that it has found on every API call (iteration) and add it to the minters list. The longer you run the minter mapper the more API calls it will make and the more minters it will eventually find.\n\nOn the first iteration of a mapping session MintMeister will note the timestamp, level and blocks minted of every minter, on every consecutive iteration it will note the current timestamp, level and blocks minted. This will enable you to gain insight into the blocks minted per hour for every minter in your list.\n\nNote that the longer you run your mapping session, the more accurate your data will be. With shorter sessions some anomalies in the data may occur, this is due to fluctuations in the short term blocks minted data returned by the API, which will not be an issue when data is collected over a longer time period.\n\nMintMeister extracts only minter account data from the blockchain, non-minter account data is not represented here.");
        textArea.setWrapStyleWord(true);
        textArea.setMargin(new java.awt.Insets(5, 10, 5, 10));
        textAreaScrollPane.setViewportView(textArea);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        textPanel.add(textAreaScrollPane, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.ipady = 5;
        textPanel.add(progressBar, gridBagConstraints);

        minterMappingTab.setRightComponent(textPanel);

        mapperMenuScrollPane.setFocusTraversalPolicyProvider(true);
        mapperMenuScrollPane.setMinimumSize(new java.awt.Dimension(250, 55));
        mapperMenuScrollPane.setPreferredSize(new java.awt.Dimension(250, 55));

        mapperMenuPanel.setMinimumSize(new java.awt.Dimension(225, 55));
        mapperMenuPanel.setOpaque(false);
        mapperMenuPanel.setLayout(new java.awt.GridBagLayout());

        startButton.setText("New Session");
        startButton.setMaximumSize(new java.awt.Dimension(128, 27));
        startButton.setMinimumSize(new java.awt.Dimension(140, 27));
        startButton.setPreferredSize(new java.awt.Dimension(140, 27));
        startButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                startButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
        mapperMenuPanel.add(startButton, gridBagConstraints);

        scrollCheckbox.setSelected(true);
        scrollCheckbox.setText("Auto scrolling");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.insets = new java.awt.Insets(14, 0, 14, 0);
        mapperMenuPanel.add(scrollCheckbox, gridBagConstraints);

        minutesSlider.setMajorTickSpacing(10);
        minutesSlider.setMaximum(60);
        minutesSlider.setMinimum(10);
        minutesSlider.setMinorTickSpacing(10);
        minutesSlider.setPaintLabels(true);
        minutesSlider.setPaintTicks(true);
        minutesSlider.setSnapToTicks(true);
        minutesSlider.setValue(30);
        minutesSlider.addChangeListener(new javax.swing.event.ChangeListener()
        {
            public void stateChanged(javax.swing.event.ChangeEvent evt)
            {
                minutesSliderStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        mapperMenuPanel.add(minutesSlider, gridBagConstraints);

        mappingDeltaLabel.setText("Iterate once every 30 minutes");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        mapperMenuPanel.add(mappingDeltaLabel, gridBagConstraints);

        saveListButton.setText("Save minters list");
        saveListButton.setToolTipText("Starting a new mapping session will clear the blocks minted data from the minter list. You can load your saved lists in the charts tab and view their results.");
        saveListButton.setMaximumSize(new java.awt.Dimension(128, 27));
        saveListButton.setMinimumSize(new java.awt.Dimension(140, 27));
        saveListButton.setPreferredSize(new java.awt.Dimension(140, 27));
        saveListButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                saveListButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
        mapperMenuPanel.add(saveListButton, gridBagConstraints);

        listInfoLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        listInfoLabel.setText("<html><div style='text-align: center;'>No minters in list</div></html>");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 15, 0);
        mapperMenuPanel.add(listInfoLabel, gridBagConstraints);

        stopButton.setText("Stop mapping");
        stopButton.setEnabled(false);
        stopButton.setMaximumSize(new java.awt.Dimension(128, 27));
        stopButton.setMinimumSize(new java.awt.Dimension(140, 27));
        stopButton.setPreferredSize(new java.awt.Dimension(140, 27));
        stopButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                stopButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 2, 0);
        mapperMenuPanel.add(stopButton, gridBagConstraints);

        continueButton.setText("Continue mapping");
        continueButton.setMaximumSize(new java.awt.Dimension(128, 27));
        continueButton.setMinimumSize(new java.awt.Dimension(140, 27));
        continueButton.setPreferredSize(new java.awt.Dimension(140, 27));
        continueButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                continueButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 2, 0);
        mapperMenuPanel.add(continueButton, gridBagConstraints);

        loadMintersButton.setText("Load minters list");
        loadMintersButton.setMinimumSize(new java.awt.Dimension(140, 27));
        loadMintersButton.setPreferredSize(new java.awt.Dimension(140, 27));
        loadMintersButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                loadMintersButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.insets = new java.awt.Insets(2, 0, 0, 0);
        mapperMenuPanel.add(loadMintersButton, gridBagConstraints);

        hoursSlider.setMajorTickSpacing(5);
        hoursSlider.setMaximum(24);
        hoursSlider.setMinimum(1);
        hoursSlider.setMinorTickSpacing(1);
        hoursSlider.setPaintLabels(true);
        hoursSlider.setPaintTicks(true);
        hoursSlider.setSnapToTicks(true);
        hoursSlider.setValue(1);
        hoursSlider.addChangeListener(new javax.swing.event.ChangeListener()
        {
            public void stateChanged(javax.swing.event.ChangeEvent evt)
            {
                hoursSliderStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        mapperMenuPanel.add(hoursSlider, gridBagConstraints);

        mapperMenuScrollPane.setViewportView(mapperMenuPanel);

        minterMappingTab.setLeftComponent(mapperMenuScrollPane);

        jTabbedPane1.addTab("Minter Mapping  ", new javax.swing.ImageIcon(getClass().getResource("/Images/status.png")), minterMappingTab); // NOI18N

        minterListTab.setDividerLocation(190);
        minterListTab.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        minterMenuPanel.setLayout(new java.awt.GridBagLayout());

        listMenuPanel.setLayout(new java.awt.GridBagLayout());

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
        listMenuPanel.add(searchInput, gridBagConstraints);

        jLabel1.setText("Search for minter");
        jLabel1.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
        listMenuPanel.add(jLabel1, gridBagConstraints);

        searchButton.setText("Search");
        searchButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                searchButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        listMenuPanel.add(searchButton, gridBagConstraints);

        caseCheckbox.setText("Case sensitive");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(0, 60, 0, 5);
        listMenuPanel.add(caseCheckbox, gridBagConstraints);

        listInfoLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        listInfoLabel1.setText("<html><div style='text-align: center;'>No minters in list</div></html>");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 12, 0);
        listMenuPanel.add(listInfoLabel1, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        minterMenuPanel.add(listMenuPanel, gridBagConstraints);

        labelPanel.setLayout(new java.awt.GridBagLayout());

        minterInfoLabel.setFont(new java.awt.Font("DialogInput", 1, 12)); // NOI18N
        minterInfoLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        minterInfoLabel.setText("No minter selected");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 10, 0);
        labelPanel.add(minterInfoLabel, gridBagConstraints);

        dataTimeLabel1.setFont(new java.awt.Font("DialogInput", 1, 12)); // NOI18N
        dataTimeLabel1.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        dataTimeLabel1.setText("data time label");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        labelPanel.add(dataTimeLabel1, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        minterMenuPanel.add(labelPanel, gridBagConstraints);

        minterMenuScrollPane.setViewportView(minterMenuPanel);

        minterListTab.setTopComponent(minterMenuScrollPane);

        mintersTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        mintersListTab.setViewportView(mintersTable);

        minterListTab.setRightComponent(mintersListTab);

        jTabbedPane1.addTab("Minters List  ", new javax.swing.ImageIcon(getClass().getResource("/Images/account.png")), minterListTab); // NOI18N

        chartsTab.setDividerLocation(250);

        javax.swing.GroupLayout chartPanelPlaceHolderLayout = new javax.swing.GroupLayout(chartPanelPlaceHolder);
        chartPanelPlaceHolder.setLayout(chartPanelPlaceHolderLayout);
        chartPanelPlaceHolderLayout.setHorizontalGroup(
            chartPanelPlaceHolderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 324, Short.MAX_VALUE)
        );
        chartPanelPlaceHolderLayout.setVerticalGroup(
            chartPanelPlaceHolderLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 520, Short.MAX_VALUE)
        );

        chartsTab.setRightComponent(chartPanelPlaceHolder);

        chartsTreeSplitPane.setDividerLocation(100);
        chartsTreeSplitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        chartsTreeMenu.setLayout(new java.awt.GridBagLayout());

        expandButton.setText("Expand all");
        expandButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                expandButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        chartsTreeMenu.add(expandButton, gridBagConstraints);

        collapseButton.setText("Collapse all");
        collapseButton.addActionListener(new java.awt.event.ActionListener()
        {
            public void actionPerformed(java.awt.event.ActionEvent evt)
            {
                collapseButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 0);
        chartsTreeMenu.add(collapseButton, gridBagConstraints);

        chartsTreeSplitPane.setLeftComponent(chartsTreeMenu);

        javax.swing.tree.DefaultMutableTreeNode treeNode1 = new javax.swing.tree.DefaultMutableTreeNode("root");
        chartsTree.setModel(new javax.swing.tree.DefaultTreeModel(treeNode1));
        chartsTree.setCellRenderer(new NodeTreeCellRenderer());
        chartsTree.setRootVisible(false);
        chartsTree.setShowsRootHandles(true);
        chartsTree.addKeyListener(new java.awt.event.KeyAdapter()
        {
            public void keyReleased(java.awt.event.KeyEvent evt)
            {
                chartsTreeKeyReleased(evt);
            }
        });
        chartsTreeScrollPane.setViewportView(chartsTree);

        chartsTreeSplitPane.setRightComponent(chartsTreeScrollPane);

        chartsTab.setLeftComponent(chartsTreeSplitPane);

        jTabbedPane1.addTab("Charts  ", new javax.swing.ImageIcon(getClass().getResource("/Images/charts.png")), chartsTab); // NOI18N

        levelUpsTab.setDividerLocation(85);
        levelUpsTab.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        levellingTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][]
            {

            },
            new String []
            {
                "Address", "Name", "Current level", "Blocks per hour", "Blocks left", "Next level in", "Next level at"
            }
        )
        {
            Class[] types = new Class []
            {
                java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
            };

            public Class getColumnClass(int columnIndex)
            {
                return types [columnIndex];
            }
        });
        levellingTableScrollPane.setViewportView(levellingTable);

        levelUpsTab.setBottomComponent(levellingTableScrollPane);

        levelUpsMenuMain.setLayout(new java.awt.GridBagLayout());

        levelUpsMenuUpper.setLayout(new java.awt.GridBagLayout());

        mintersPerLevelSlider.setMajorTickSpacing(5);
        mintersPerLevelSlider.setMaximum(50);
        mintersPerLevelSlider.setMinimum(5);
        mintersPerLevelSlider.setMinorTickSpacing(1);
        mintersPerLevelSlider.setPaintLabels(true);
        mintersPerLevelSlider.setPaintTicks(true);
        mintersPerLevelSlider.setSnapToTicks(true);
        mintersPerLevelSlider.setValue(5);
        mintersPerLevelSlider.addChangeListener(new javax.swing.event.ChangeListener()
        {
            public void stateChanged(javax.swing.event.ChangeEvent evt)
            {
                mintersPerLevelSliderStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.ipadx = 100;
        levelUpsMenuUpper.add(mintersPerLevelSlider, gridBagConstraints);

        jLabel2.setText("showing");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 10);
        levelUpsMenuUpper.add(jLabel2, gridBagConstraints);

        mintersPerLevelLabel.setText("5 minters per level");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
        levelUpsMenuUpper.add(mintersPerLevelLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        levelUpsMenuMain.add(levelUpsMenuUpper, gridBagConstraints);

        levelUpsMenuLower.setLayout(new java.awt.GridBagLayout());

        dataTimeLabel.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        dataTimeLabel.setText("data time label");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        levelUpsMenuLower.add(dataTimeLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.weighty = 0.1;
        levelUpsMenuMain.add(levelUpsMenuLower, gridBagConstraints);

        levelUpsTab.setLeftComponent(levelUpsMenuMain);

        jTabbedPane1.addTab("Upcoming level-ups  ", new javax.swing.ImageIcon(getClass().getResource("/Images/level.png")), levelUpsTab); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1, javax.swing.GroupLayout.Alignment.TRAILING)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 553, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void startButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_startButtonActionPerformed
    {//GEN-HEADEREND:event_startButtonActionPerformed
        if(mintersTable.getRowCount() > 0 && JOptionPane.showConfirmDialog(this, 
                Utilities.AllignCenterHTML("Starting a new session will clear the blocks minted data in your minters list.<br/>"
                        + "The known minters will not be deleted, just the data.<br/>"
                + "You can save your minters list to file if you want to keep<br/>"
                + "the data or continue mapping the previous session."), "Confirm", 
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
        {
            return;
        }
        
        setUiComponentsEnabled(true);
        
        progressBar.setStringPainted(true);       
        
        if(addresses.isEmpty())
            textArea.setText("");
            
        populateMintersFromFile(true);
    }//GEN-LAST:event_startButtonActionPerformed

    private void saveListButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_saveListButtonActionPerformed
    {//GEN-HEADEREND:event_saveListButtonActionPerformed
        JFileChooser jfc = new JFileChooser(System.getProperty("user.dir") + "/minters");
        long startTime = 0;
        
        try(Connection connection = ConnectionDB.getConnection("minters"))
        {
            startTime = (long)dbManager.GetColumn("minters", "timestamp_start", "timestamp_start", "asc", connection).get(0);            
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
        
        FileNameExtensionFilter filter = new FileNameExtensionFilter("database files (*.mv.db)", "db");
        //show preferred filename in filechooser
        if(startTime == 0)
            jfc.setSelectedFile(new File("minters " + Utilities.DateFormatFile(System.currentTimeMillis()) + ".mv.db")); 
        else
            jfc.setSelectedFile(new File("minters " + Utilities.DateFormatFile(startTime) + " till " + 
                    Utilities.DateFormatFile(System.currentTimeMillis()) + ".mv.db")); 
            
        // add filters
        jfc.setAcceptAllFileFilterUsed(false);
        jfc.addChoosableFileFilter(filter);
        jfc.setFileFilter(filter);
        int returnValue = jfc.showSaveDialog(null);

        if (returnValue == JFileChooser.APPROVE_OPTION)
        {            
            File selectedFile = jfc.getSelectedFile();         

            if(selectedFile.getName().equals("minters.mv.db"))
            {
                JOptionPane.showMessageDialog(this, "The name 'minters.mv.db' is reserved, please use a different name");
                return;
            }
            if(selectedFile.exists())
            {
                if(JOptionPane.showConfirmDialog(this, 
                        "File " + selectedFile.getName() + " already exists. Choose Yes to overwrite, No to cancel","Overwrite existing file?",
                        JOptionPane.YES_NO_OPTION) == JOptionPane.NO_OPTION)
                {                
                    return;
                }
            }
            File original = new File(System.getProperty("user.dir") + "/minters/minters.mv.db");
            if(!original.exists())
            {
                JOptionPane.showMessageDialog(this, Utilities.AllignCenterHTML("Could not find minter file:<br/>" + original.getPath()));
                return;
            }     
            if(!selectedFile.getName().endsWith("mv.db"))
            {
                File newFile = new File(selectedFile.getPath() + ".mv.db");
                Utilities.CopyFile(original, newFile);
            }
            else
            {
                Utilities.CopyFile(original, selectedFile);
            }            
        }
    }//GEN-LAST:event_saveListButtonActionPerformed

    private void searchButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_searchButtonActionPerformed
    {//GEN-HEADEREND:event_searchButtonActionPerformed
        searchForMinter();        
    }//GEN-LAST:event_searchButtonActionPerformed

    private void searchInputKeyReleased(java.awt.event.KeyEvent evt)//GEN-FIRST:event_searchInputKeyReleased
    {//GEN-HEADEREND:event_searchInputKeyReleased
//        if(evt.getKeyCode() == KeyEvent.VK_ENTER)
            searchForMinter();
    }//GEN-LAST:event_searchInputKeyReleased

    private void searchInputFocusGained(java.awt.event.FocusEvent evt)//GEN-FIRST:event_searchInputFocusGained
    {//GEN-HEADEREND:event_searchInputFocusGained
        searchInput.selectAll();
    }//GEN-LAST:event_searchInputFocusGained

    private void stopButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_stopButtonActionPerformed
    {//GEN-HEADEREND:event_stopButtonActionPerformed
        setUiComponentsEnabled(false);
        
        iterations = 0;         
        mappingHalted = true;          
        appendText("Mapping was ended by user at " + Utilities.DateFormat(System.currentTimeMillis()) + "\n\n");
        progressBar.setStringPainted(false);
    }//GEN-LAST:event_stopButtonActionPerformed

    private void continueButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_continueButtonActionPerformed
    {//GEN-HEADEREND:event_continueButtonActionPerformed
        if(mintersTable.getRowCount() == 0)
        {
            JOptionPane.showMessageDialog(this, Utilities.AllignCenterHTML("There are no minters in your minters list<br/>Please start a new mapping session"));
            return;
        }
        
        setUiComponentsEnabled(true);
        
        progressBar.setStringPainted(true);
        
        if(addresses.isEmpty())
            textArea.setText("");
            
        populateMintersFromFile(false);
    }//GEN-LAST:event_continueButtonActionPerformed

    private void loadMintersButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_loadMintersButtonActionPerformed
    {//GEN-HEADEREND:event_loadMintersButtonActionPerformed
        JFileChooser jfc = new JFileChooser(System.getProperty("user.dir") + "/minters");
        
        FileNameExtensionFilter filter = new FileNameExtensionFilter("database files (*.mv.db)", "db");
        //        jfc.setSelectedFile(new File("properties.mv.db")); //show preferred filename in filechooser
        // add filters
        jfc.setAcceptAllFileFilterUsed(false);
        jfc.addChoosableFileFilter(filter);
        jfc.setFileFilter(filter);
        int returnValue = jfc.showSaveDialog(null);

        if (returnValue == JFileChooser.APPROVE_OPTION)
        {            
            File selectedFile = jfc.getSelectedFile();          
            if(!selectedFile.getName().endsWith("mv.db"))
            {
                JOptionPane.showMessageDialog(this, "Invalid file, filename must end with 'mv.db'");
                return;
            }
            //check if selected file is valid
            //copy current minter file to temp file
            File minterFile = new File(System.getProperty("user.dir") + "/minters/minters.mv.db");
            if(selectedFile.getPath().equals(minterFile.getPath()))
            {
                JOptionPane.showMessageDialog(this, Utilities.AllignCenterHTML(
                        "This file is already contains your current minters list<br/><br/>") + selectedFile.getPath());
                return;
            }
            File tempFile = new File(System.getProperty("user.dir") + "/minters/temp_" + Utilities.DateFormatPath(System.currentTimeMillis()));
            Utilities.CopyFile(minterFile, tempFile);
            //overwrite minterfile with selected file
            Utilities.OverwriteFile(selectedFile, minterFile);
            try(Connection connection = ConnectionDB.getConnection("minters"))
            {
                if(!dbManager.TableExists("minters", connection))
                {
                    JOptionPane.showMessageDialog(this,Utilities.AllignCenterHTML(
                            "Could not load minters list<br/>File " + selectedFile.getName() + " does not appear to be a valid minters file"));
                    //revert to original
                    Utilities.OverwriteFile(tempFile, minterFile);
                    tempFile.delete();
                    return;
                }
            }
            catch (Exception e)
            {
                BackgroundService.AppendLog(e);
            }
            
            if(JOptionPane.showConfirmDialog(this, 
                    Utilities.AllignCenterHTML(
                           "Loading a saved minters list will overwrite your current list<br/>"
                        + "If you want to keep the data on your current list you should<br/>"
                        + "first save your current list to fle<br/><br/>"
                        + "Do you want to continue?"), "Overwrite minters file?", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
            {
                //revert to original
                Utilities.OverwriteFile(tempFile, minterFile);
                tempFile.delete();
                return;
            }
            
            tempFile.delete();
            fillMinterTable("minted_end", "desc");
            setMinterRank();
            fillLevellingTable();
            JOptionPane.showMessageDialog(this, "Minters list loaded successfully"); 
        }
    }//GEN-LAST:event_loadMintersButtonActionPerformed

    private void minutesSliderStateChanged(javax.swing.event.ChangeEvent evt)//GEN-FIRST:event_minutesSliderStateChanged
    {//GEN-HEADEREND:event_minutesSliderStateChanged
        if(minutesSlider.getValue() == 60 && !minutesSlider.getValueIsAdjusting())
        {
            hoursSlider.setValue(1);
            minutesSlider.setVisible(false);
            hoursSlider.setVisible(true);
            mappingDeltaLabel.setText("Iterate once every " + hoursSlider.getValue() + " hours");
            mappingDelta = minutesSlider.getValue() * 3600000;
            return;
        }

        mappingDeltaLabel.setText("Iterate once every " + minutesSlider.getValue() + " minutes");
        mappingDelta = minutesSlider.getValue() * 60000;
    }//GEN-LAST:event_minutesSliderStateChanged

    private void hoursSliderStateChanged(javax.swing.event.ChangeEvent evt)//GEN-FIRST:event_hoursSliderStateChanged
    {//GEN-HEADEREND:event_hoursSliderStateChanged
       if(hoursSlider.getValue() == 1 && !hoursSlider.getValueIsAdjusting())
        {
            minutesSlider.setValue(60);
            hoursSlider.setVisible(false);
            minutesSlider.setVisible(true);
            mappingDeltaLabel.setText("Iterate once every " + minutesSlider.getValue() + " minutes");
            mappingDelta = minutesSlider.getValue() * 60000;
            return;
        }

        mappingDeltaLabel.setText("Iterate once every " + hoursSlider.getValue() + " hours");
        mappingDelta = hoursSlider.getValue() * 3600000;
    }//GEN-LAST:event_hoursSliderStateChanged

    private void expandButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_expandButtonActionPerformed
    {//GEN-HEADEREND:event_expandButtonActionPerformed
        gui.ExpandTree(chartsTree, 2);
    }//GEN-LAST:event_expandButtonActionPerformed

    private void collapseButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_collapseButtonActionPerformed
    {//GEN-HEADEREND:event_collapseButtonActionPerformed
        TreePath parent = new TreePath(pieNode.getPath());
        collapseAll(chartsTree, parent);
        parent = new TreePath(lineNode.getPath());
        collapseAll(chartsTree, parent);    
    }//GEN-LAST:event_collapseButtonActionPerformed

    private void chartsTreeKeyReleased(java.awt.event.KeyEvent evt)//GEN-FIRST:event_chartsTreeKeyReleased
    {//GEN-HEADEREND:event_chartsTreeKeyReleased
        if(Utilities.isNavKeyEvent(evt))
        {
            chartsTreeSelected(chartsTree.getSelectionPath(), false);
        }
    }//GEN-LAST:event_chartsTreeKeyReleased

    private void mintersPerLevelSliderStateChanged(javax.swing.event.ChangeEvent evt)//GEN-FIRST:event_mintersPerLevelSliderStateChanged
    {//GEN-HEADEREND:event_mintersPerLevelSliderStateChanged
        mintersPerLevelLabel.setText(mintersPerLevelSlider.getValue() + " minters per level");
        if(!mintersPerLevelSlider.getValueIsAdjusting())
        {
            fillLevellingTable();
        }
    }//GEN-LAST:event_mintersPerLevelSliderStateChanged


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox caseCheckbox;
    private javax.swing.JPanel chartPanelPlaceHolder;
    private javax.swing.JPanel chartsMenuPanel;
    private javax.swing.JSplitPane chartsTab;
    private javax.swing.JTree chartsTree;
    private javax.swing.JPanel chartsTreeMenu;
    private javax.swing.JScrollPane chartsTreeScrollPane;
    private javax.swing.JSplitPane chartsTreeSplitPane;
    private javax.swing.JButton collapseButton;
    private javax.swing.JButton continueButton;
    private javax.swing.JLabel dataTimeLabel;
    private javax.swing.JLabel dataTimeLabel1;
    private javax.swing.JButton expandButton;
    private javax.swing.JSlider hoursSlider;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JPanel labelPanel;
    private javax.swing.JPanel levelUpsMenuLower;
    private javax.swing.JPanel levelUpsMenuMain;
    private javax.swing.JPanel levelUpsMenuUpper;
    private javax.swing.JSplitPane levelUpsTab;
    private javax.swing.JTable levellingTable;
    private javax.swing.JScrollPane levellingTableScrollPane;
    private javax.swing.JLabel listInfoLabel;
    private javax.swing.JLabel listInfoLabel1;
    private javax.swing.JPanel listMenuPanel;
    private javax.swing.JButton loadMintersButton;
    private javax.swing.JPanel mapperMenuPanel;
    private javax.swing.JScrollPane mapperMenuScrollPane;
    private javax.swing.JLabel mappingDeltaLabel;
    private javax.swing.JLabel minterInfoLabel;
    private javax.swing.JSplitPane minterListTab;
    private javax.swing.JSplitPane minterMappingTab;
    private javax.swing.JPanel minterMenuPanel;
    private javax.swing.JScrollPane minterMenuScrollPane;
    private javax.swing.JScrollPane mintersListTab;
    private javax.swing.JLabel mintersPerLevelLabel;
    private javax.swing.JSlider mintersPerLevelSlider;
    private javax.swing.JTable mintersTable;
    private javax.swing.JSlider minutesSlider;
    private javax.swing.JProgressBar progressBar;
    private javax.swing.JButton saveListButton;
    private javax.swing.JCheckBox scrollCheckbox;
    private javax.swing.JButton searchButton;
    private javax.swing.JTextField searchInput;
    private javax.swing.JButton startButton;
    private javax.swing.JButton stopButton;
    private javax.swing.JTextArea textArea;
    private javax.swing.JScrollPane textAreaScrollPane;
    private javax.swing.JPanel textPanel;
    // End of variables declaration//GEN-END:variables
}
