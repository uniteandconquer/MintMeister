package mintmeister;

import java.awt.BasicStroke;
import java.awt.Color;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import javax.swing.JTable;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.plot.MultiplePiePlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.general.DatasetUtils;
import org.jfree.chart.util.TableOrder;

public class PieChart 
{
    protected ChartPanel chartPanel;
    private static String subtitle = "";

    public PieChart(String dataType, String levelType,int maxLevel,long tableTime, JTable mintersTable)
    {
        CategoryDataset dataset = null;
        
        switch(dataType)
        {
            case "Blocks per hour":
                dataset = createBphDataset(levelType,maxLevel,tableTime,mintersTable);
                break;
            case "Level distribution":
                dataset = createLevelDistDataset(maxLevel, tableTime, mintersTable);
                break;
            case "Registered names per level":
            case "Registered names":
                dataset = createNamesDataset(levelType, maxLevel, tableTime, mintersTable);
                break;
            case "Registered names network":
                dataset = createNamesNetworkDataset(tableTime, mintersTable);
                break;
            case "Level-up duration":
                dataset = createLevelUpDataset(levelType, tableTime);
                break;
            case "Blocks per hour distribution":
                dataset = createBphDistDataset(maxLevel, tableTime, mintersTable);
                break;
            case "Blocks per hour network":
                dataset = createBphNetworkDataset(tableTime, mintersTable);
                break;
            case "Balance distribution":
                dataset = createBalanceDistDataset(maxLevel, tableTime);
                break;
        }
        
        if(dataset == null)
            return;
        
        JFreeChart chart = createChart(dataType,dataset);
        chartPanel = new ChartPanel(chart, true, true, true, false,true);
    }

    private static CategoryDataset createBphDataset(String levelType,int maxLevel,long tableTime,JTable mintersTable)
    {
        int levelCount = levelType.equals("All levels") ? maxLevel : 1;
        int chartLevel = 0;
        
        //cannot parse 'All levels' to int
        if(levelCount == 1)
            chartLevel = Integer.parseInt(levelType.replaceAll("[^0-9]", ""));   
        
        String[] tiers = new String[]{"0 blocks","1-10 blocks","11-20 blocks","21-30 blocks","31-40 blocks","41-50 blocks","51-60 blocks"};
        String[] levels = levelCount == 1 ? new String[]{"Level " + chartLevel} : new String[levelCount];
        if(levelCount > 1)
            for(int i = 0; i < levelCount;i++)
                levels[i] = "Level " + (i+1);
        
        double[][] data = new double[7][levelCount]; //[tier][level]
        int minterCount = 0;
        
        for(int i = 0; i < mintersTable.getRowCount();i++)
        {
            int rowLevel = (int) mintersTable.getValueAt(i, 3);
            
            if(levelCount == 1 && rowLevel != chartLevel)
                continue;
            
            minterCount++;        
            
            int level = rowLevel - 1;
            int bph = (int) mintersTable.getValueAt(i, 2);  
            int mintedSession = (int) mintersTable.getValueAt(i, 9);  
            
            if(bph == 0 && mintedSession >= 10)
            {                
                if(levelCount == 1)
                    data[0][0]++;
                else
                    data[0][level]++;
            }
            
            if(bph > 0 && bph < 61)
            {
                //add all round decimal integers to group below
                if(bph % 10 == 0)
                    bph -= 1;                
                
                int tier = bph /= 10;
                tier+=1;//account for 0 blocks tier
                
                if(levelCount == 1)
                    data[tier][0]++;
                else
                    data[tier][level]++;
            }  
        }
        
        CategoryDataset dataset = DatasetUtils.createCategoryDataset(tiers,levels,data);

        subtitle = "Total of " + minterCount + " minters found in ";
        subtitle += levelCount == 1 ? "level " + chartLevel : "network";
        subtitle += " on " + Utilities.DateFormatShort(tableTime);
        return dataset;
        
        //keep as reference
//        double[][] data = new double[][]
//        {
//            {
//                3.0, 4.0, 3.0, 5.0,1.0,1.5
//            },
//            {
//                5.0, 7.0, 6.0, 8.0,1.0,1.5
//            },
//            {
//                5.0, 7.0, 3.0, 8.0,1.0,1.5
//            },
//            {
//                1.0, 2.0, 3.0, 4.0,1.0,1.5
//            },
//            {
//                1.0, 2.0, 3.0, 4.0,1.0,1.5
//            },
//            {
//                2.0, 3.0, 2.0, 3.0,1.0,1.5
//            }
//        };
    }
    
    private static CategoryDataset createLevelDistDataset(int maxLevel,long tableTime,JTable mintersTable)
    {        
        String[] levels = new String[maxLevel];
        for(int i = 0; i < maxLevel;i++)
            levels[i] = "Level " + (i+1);
        
        double[][] data = new double[6][1]; //[tier][level]
        
        for(int i = 0; i < mintersTable.getRowCount();i++)
        {    
            int level = (int) mintersTable.getValueAt(i, 3) - 1; 
            data[level][0]++;           
        }
        
        CategoryDataset dataset = DatasetUtils.createCategoryDataset(levels,new String[]{"All levels"},data);

        subtitle = "Total of " + mintersTable.getRowCount() + " minters found in network";
        subtitle += " on " + Utilities.DateFormatShort(tableTime);
        
        return dataset;
    }
    
    private static CategoryDataset createNamesDataset(String levelType,int maxLevel,long tableTime,JTable mintersTable)
    {
        int levelCount = levelType.equals("All levels") ? maxLevel : 1;
        int chartLevel = 0;
        
        //cannot parse 'All levels' to int
        if(levelCount == 1)
            chartLevel = Integer.parseInt(levelType.replaceAll("[^0-9]", ""));   
        
        String[] tiers = new String[]{"Registered a name","No name registered"};
        String[] levels = levelCount == 1 ? new String[]{"Level " + chartLevel} : new String[levelCount];
        if(levelCount > 1)
            for(int i = 0; i < levelCount;i++)
                levels[i] = "Level " + (i+1);
        
        double[][] data = new double[2][levelCount]; //[tier][level]
        int minterCount = 0;
        
        for(int i = 0; i < mintersTable.getRowCount();i++)
        {
            int rowLevel = (int) mintersTable.getValueAt(i, 3);
            
            if(levelCount == 1 && rowLevel != chartLevel)
                continue;
            
            minterCount++;        
            
            int level = rowLevel - 1;            
            String name = mintersTable.getValueAt(i, 1).toString();
            int tier = name.isBlank() ? 1 : 0;
                
            if(levelCount == 1)
                data[tier][0]++;
            else
                data[tier][level]++;
        }
        
        CategoryDataset dataset = DatasetUtils.createCategoryDataset(tiers,levels,data);

        subtitle = "Total of " + minterCount + " minters found in ";
        subtitle += levelCount == 1 ? "level " + chartLevel : "network";
        subtitle += " on " + Utilities.DateFormatShort(tableTime);
        return dataset;
    }
    
    private static CategoryDataset createNamesNetworkDataset(long tableTime,JTable mintersTable)
    {                
        String[] tiers = new String[]{"Registered a name","No name registered"};
        String[] levels = new String[]{"All levels"};
        
        double[][] data = new double[2][1]; //[tier][level]
        
        for(int i = 0; i < mintersTable.getRowCount();i++)
        {                   
            String name = mintersTable.getValueAt(i, 1).toString();
            int tier = name.isBlank() ? 1 : 0;
            
            data[tier][0]++;
        }
        
        CategoryDataset dataset = DatasetUtils.createCategoryDataset(tiers,levels,data);

        subtitle = "Total of " + mintersTable.getRowCount() + " minters found in network";
        subtitle += " on " + Utilities.DateFormatShort(tableTime);
        return dataset;
    }
    
    
    private static CategoryDataset createLevelUpDataset(String levelType,long tableTime)
    {
        try(Connection connection = ConnectionDB.getConnection("minters"))
        {
            int chartLevel =  Integer.parseInt(levelType.replaceAll("[^0-9]", ""));              

            String[] tiers = new String[12];
            for(int i = 0; i < tiers.length - 1;i++)
                tiers[i] = "Level up in " + (i + 1) + " months";
            tiers[tiers.length - 1] = "Level up in 12 or more months";

            String[] levels = new String[]{"Level "+ chartLevel};

            double[][] data = new double[12][1]; //[tier][level]
            int minterCount = 0;
             
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("select level,level_duration from minters");
            
            while(resultSet.next())
            {
                int rowLevel = resultSet.getInt("level");
                long levelDuration = resultSet.getLong("level_duration");

                if(rowLevel != chartLevel)
                    continue;

                minterCount++;         
                
                long tier = levelDuration / 2592000000l; //month in millisec
                tier = tier > 11 ? 11 : tier;
                
                data[(int)tier][0]++;
            }

            CategoryDataset dataset = DatasetUtils.createCategoryDataset(tiers,levels,data);

            subtitle = "Total of " + minterCount + " minters found in level " + chartLevel;
            subtitle += " on " + Utilities.DateFormatShort(tableTime);
            return dataset;
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
        
        return null;           
    }
    
    private static CategoryDataset createBalanceDistDataset(int maxLevel,long tableTime)
    {
        try(Connection connection = ConnectionDB.getConnection("minters"))
        {                
            String[] tiers = new String[maxLevel];
            for(int i = 0; i < tiers.length;i++)
            {
                tiers[i] = "Level " + (i + 1) + " balance";
            }

            double[][] data = new double[maxLevel][1]; //[tier][level]
            
            long lastEntryTime = (long) BackgroundService.GUI.dbManager.GetColumn(
                    "levels_data", "timestamp", "timestamp", "desc", connection).get(0);
             
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(
                    "select level,balance from levels_data where timestamp=" + String.valueOf(lastEntryTime));       
            
            while(resultSet.next())
            {
                int rowLevel = resultSet.getInt("level");                
                if(rowLevel > maxLevel)
                    continue;
                
                Object obj = resultSet.getObject("balance");                    
                
                if(obj != null)
                {
                    data[rowLevel - 1][0] += (double)obj;
                }
            }
            
            statement = connection.createStatement();
            resultSet = statement.executeQuery(
                "select total_balance,minters_count from minters_data where timestamp=" + String.valueOf(lastEntryTime));  
            resultSet.next();
            double totalMintersBalance = resultSet.getDouble("total_balance");
            int minterCount = resultSet.getInt("minters_count");

            String[] levels = new String[]{String.format("On %s\nTotal balance for all levels was %,.0f",
                    Utilities.DateFormatShort(lastEntryTime),totalMintersBalance)};            

            CategoryDataset dataset = DatasetUtils.createCategoryDataset(tiers,levels,data);

            subtitle = "Total of " + minterCount + " minters found in network";
            subtitle += " on " + Utilities.DateFormatShort(tableTime);
            return dataset;
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
        
        return null;           
    }
    
    private static CategoryDataset createBphDistDataset(int maxLevel,long tableTime,JTable mintersTable)
    {      
        //creates 6 piecharts for every bph tier and displays the percentage of each level that is represented in those charts
        String[] charts = new String[]{"1-10 blocks","11-20 blocks","21-30 blocks","31-40 blocks","41-50 blocks","51-60 blocks"};
        String[] tiers = new String[maxLevel];
        for(int i = 0; i < maxLevel;i++)
            tiers[i] = "Level " + (i+1);
        
        double[][] data = new double[maxLevel][6]; //[tier][level]
        
        for(int i = 0; i < mintersTable.getRowCount();i++)
        {
            int level = (int) mintersTable.getValueAt(i, 3) - 1;    
            
            int bph = (int) mintersTable.getValueAt(i, 2);            
            
            if(bph > 0 && bph < 61)
            {
                //add all round decimal integers to group below
                if(bph % 10 == 0)
                    bph -= 1;                
                
                int chart = bph /= 10;
                
                data[level][chart]++;
            }  
        }
        
        CategoryDataset dataset = DatasetUtils.createCategoryDataset(tiers,charts,data);

        subtitle = "Total of " + mintersTable.getRowCount() + " minters found in network";
        subtitle += " on " + Utilities.DateFormatShort(tableTime);
        return dataset;        
    }
    
    private static CategoryDataset createBphNetworkDataset(long tableTime,JTable mintersTable)
    {     
        String[] tiers = new String[]{"0 blocks","1-10 blocks","11-20 blocks","21-30 blocks","31-40 blocks","41-50 blocks","51-60 blocks"};
        String[] levels = new String[]{"All levels"};
        
        double[][] data = new double[7][1]; //[tier][level]
        
        for(int i = 0; i < mintersTable.getRowCount();i++)
        {            
            int bph = (int) mintersTable.getValueAt(i, 2);            
            int mintedSession = (int) mintersTable.getValueAt(i, 9);
            
            if(bph == 0 && mintedSession > 10)
            {
                data[0][0]++;
                continue;
            }
            
            if(bph > 0 && bph < 61)
            {
                //add all round decimal integers to group below
                if(bph % 10 == 0)
                    bph -= 1;                
                
                int tier = bph /= 10;
                tier+=1;//account for 0 blocks tier
                
                data[tier][0]++;
            }  
        }
        
        CategoryDataset dataset = DatasetUtils.createCategoryDataset(tiers,levels,data);

        subtitle = "Total of " + mintersTable.getRowCount() + " minters found in network";
        subtitle += " on " + Utilities.DateFormatShort(tableTime);
        return dataset;
    }

    private static JFreeChart createChart(String title,CategoryDataset dataset)
    {
        JFreeChart chart = ChartFactory.createMultiplePieChart(
                title, // chart title
                dataset, // dataset
                TableOrder.BY_COLUMN,
                true, // include legend
                true,
                false
        );
        chart.addSubtitle(new TextTitle(subtitle));
        MultiplePiePlot plot = (MultiplePiePlot) chart.getPlot();
        plot.setBackgroundPaint(Color.white);
        plot.setOutlineStroke(new BasicStroke(1.0f));
        JFreeChart subchart = plot.getPieChart();
        PiePlot p = (PiePlot) subchart.getPlot();
        p.setIgnoreZeroValues(true);
        p.setBackgroundPaint(null);
        p.setOutlineStroke(null);
        p.setLabelGenerator(new StandardPieSectionLabelGenerator("{0} ({2})",
                NumberFormat.getNumberInstance(),
                new DecimalFormat("0.00%")));
        p.setMaximumLabelWidth(0.20);
        return chart;
    }

}
