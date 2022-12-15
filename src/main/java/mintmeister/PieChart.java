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
            case "Inactive minters distribution":
                dataset = createInactiveDistDataset(maxLevel,tableTime);
                break;
            case "Active minters distribution":
                dataset = createActiveDistDataset(maxLevel,tableTime);
                break;
            case "Active/inactive ratio":
                if(levelType.equals("All levels"))
                    dataset = createActiveRatioAllDataset(maxLevel,tableTime);
                else   
                    dataset = createActiveRatioDataset(levelType,tableTime);                 
                break;
            case "Active/inactive ratio network":
                    dataset = createActiveRatioDataset(levelType,tableTime);
                break;
            case "Penalty distribution":
                dataset = createPenaltyDistDataset(maxLevel, tableTime, mintersTable);
                break;
        }
        
        if(dataset == null)
            return;
        
        JFreeChart chart = createChart(dataType,dataset);
        chartPanel = new ChartPanel(chart, true, true, true, false,true);
    }

    private CategoryDataset createBphDataset(String levelType,int maxLevel,long tableTime,JTable mintersTable)
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
                levels[i] = "Level " + i;
        
        double[][] data = new double[7][levelCount]; //[tier][level]
        int minterCount = 0;
        
        for(int i = 0; i < mintersTable.getRowCount();i++)
        {
            int rowLevel = (int) mintersTable.getValueAt(i, 3);
            
            if(levelCount == 1 && rowLevel != chartLevel)
                continue;
            
            minterCount++;        
            
            int bph = (int) mintersTable.getValueAt(i, 2);  
            int mintedSession = (int) mintersTable.getValueAt(i, 9);  
            
            if(bph == 0 && mintedSession >= 10)
            {                
                if(levelCount == 1)
                    data[0][0]++;
                else
                    data[0][rowLevel]++;
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
                    data[tier][rowLevel]++;
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
    
    private CategoryDataset createLevelDistDataset(int maxLevel,long tableTime,JTable mintersTable)
    {        
        String[] levels = new String[maxLevel];
        for(int i = 0; i < maxLevel;i++)
            levels[i] = "Level " + i;
        
        double[][] data = new double[maxLevel][1]; //[tier][level]
        
        for(int i = 0; i < mintersTable.getRowCount();i++)
        {    
            int level = (int) mintersTable.getValueAt(i, 3); 
            data[level][0]++;           
        }
        
        CategoryDataset dataset = DatasetUtils.createCategoryDataset(levels,new String[]{"All levels"},data);

        subtitle = "Total of " + mintersTable.getRowCount() + " minters found in network";
        subtitle += " on " + Utilities.DateFormatShort(tableTime);
        
        return dataset;
    }
    
    
    
    private CategoryDataset createPenaltyDistDataset(int maxLevel,long tableTime,JTable exMintersTable)
    {        
        String[] levels = new String[maxLevel];
        for(int i = 0; i < maxLevel;i++)
            levels[i] = "Level " + i + " penalties";
        
        double[][] data = new double[maxLevel][1]; //[tier][level]
        
        int totalPenalized = 0;
        
        for(int i = 0; i < exMintersTable.getRowCount();i++)
        {    
            if((int) exMintersTable.getValueAt(i, 6) >= 0 )
                continue;
            
            totalPenalized++;
            
            int level = (int) exMintersTable.getValueAt(i, 2); 
            data[level][0]++;           
        }
        
        CategoryDataset dataset = DatasetUtils.createCategoryDataset(levels,new String[]{"Penalty distribution per level"},data);

        subtitle = "Total of " + totalPenalized + " penalized minters found in your minters list";
        subtitle += " on " + Utilities.DateFormatShort(tableTime);
        
        return dataset;
    }
    
    private CategoryDataset createInactiveDistDataset(int maxLevel,long tableTime)
    {
        try(Connection connection = ConnectionDB.getConnection("minters"))
        {                
            String[] tiers = new String[maxLevel];
            for(int i = 0; i < tiers.length;i++)
            {
                tiers[i] = "Level " + i + " inactives";
            }

            double[][] data = new double[maxLevel][1]; //[tier][level]
            
            long lastEntryTime = (long) BackgroundService.GUI.dbManager.GetColumn(
                    "levels_data", "timestamp", "timestamp", "desc", connection).get(0);
             
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(
                    "select level,inactive from levels_data where timestamp=" + String.valueOf(lastEntryTime));       
            
            while(resultSet.next())
            {
                int rowLevel = resultSet.getInt("level");                
                if(rowLevel + 1 > maxLevel)//+1 accounts for level 0's
                    continue;
                
                Object obj = resultSet.getObject("inactive");                    
                
                if(obj != null)
                {
                    data[rowLevel][0] += (int)obj;
                }
            }
            
            statement = connection.createStatement();
            resultSet = statement.executeQuery(
                "select inactive,minters_count from minters_data where timestamp=" + String.valueOf(lastEntryTime));  
            resultSet.next();
            int totalInactives = resultSet.getInt("inactive");
            int minterCount = resultSet.getInt("minters_count");

            String[] levels = new String[]{String.format("On %s\nTotal inactives for all levels was %s",
                    Utilities.DateFormatShort(lastEntryTime),Utilities.numberFormat(totalInactives))};            

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
    
    private CategoryDataset createActiveDistDataset(int maxLevel,long tableTime)
    {
        try(Connection connection = ConnectionDB.getConnection("minters"))
        {                
            String[] tiers = new String[maxLevel];
            for(int i = 0; i < tiers.length;i++)
            {
                tiers[i] = "Level " + i + " actives";
            }

            double[][] data = new double[maxLevel][1]; //[tier][level]
            
            long lastEntryTime = (long) BackgroundService.GUI.dbManager.GetColumn(
                    "levels_data", "timestamp", "timestamp", "desc", connection).get(0);
             
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(
                    "select level,inactive,count from levels_data where timestamp=" + String.valueOf(lastEntryTime));       
            
            while(resultSet.next())
            {
                int rowLevel = resultSet.getInt("level");                
                if(rowLevel + 1 > maxLevel)//+1 accounts for level 0's
                    continue;
                
                Object inactive = resultSet.getObject("inactive");    
                Object count = resultSet.getObject("count");
                
                if(inactive != null)
                {
                    data[rowLevel][0] += (int)count - (int)inactive;
                }
            }
            
            statement = connection.createStatement();
            resultSet = statement.executeQuery(
                "select inactive,minters_count from minters_data where timestamp=" + String.valueOf(lastEntryTime));  
            resultSet.next();
            int totalInactives = resultSet.getInt("inactive");
            int minterCount = resultSet.getInt("minters_count");

            String[] levels = new String[]{String.format("On %s\nTotal actives for all levels was %s",
                    Utilities.DateFormatShort(lastEntryTime),Utilities.numberFormat(minterCount - totalInactives))};            

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
    
    private CategoryDataset createNamesDataset(String levelType,int maxLevel,long tableTime,JTable mintersTable)
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
                levels[i] = "Level " + i;
        
        double[][] data = new double[2][levelCount]; //[tier][level]
        int minterCount = 0;
        
        for(int i = 0; i < mintersTable.getRowCount();i++)
        {
            int rowLevel = (int) mintersTable.getValueAt(i, 3);
            
            if(levelCount == 1 && rowLevel != chartLevel)
                continue;
            
            minterCount++;        
                      
            String name = mintersTable.getValueAt(i, 1).toString();
            int tier = name.isBlank() ? 1 : 0;
                
            if(levelCount == 1)
                data[tier][0]++;
            else
                data[tier][rowLevel]++;
        }
        
        CategoryDataset dataset = DatasetUtils.createCategoryDataset(tiers,levels,data);

        subtitle = "Total of " + minterCount + " minters found in ";
        subtitle += levelCount == 1 ? "level " + chartLevel : "network";
        subtitle += " on " + Utilities.DateFormatShort(tableTime);
        return dataset;
    }
    
    private CategoryDataset createActiveRatioDataset(String levelType,long tableTime)
    {
        try(Connection connection = ConnectionDB.getConnection("minters"))
        {
            String[] tiers = new String[]{"Active","Inactive"};

            double[][] data = new double[2][1]; //[tier][level]
            
            long lastEntryTime = (long) BackgroundService.GUI.dbManager.GetColumn(
                    "minters_data", "timestamp", "timestamp", "desc", connection).get(0);
            
            Statement statement = connection.createStatement();
            ResultSet resultSet;
            
            double activesCount = 0;
            double inactivesCount = 0;
            
            if(levelType.equals("All levels"))
            {
                resultSet = statement.executeQuery(
                        "select minters_count,inactive from minters_data where timestamp=" + String.valueOf(lastEntryTime));
                
                while(resultSet.next())
                {
                    data[0][0] += resultSet.getInt("minters_count") - resultSet.getInt("inactive");
                    data[1][0] += resultSet.getInt("inactive");
                    activesCount = data[0][0];
                    inactivesCount = data[1][0];
                }
            }
            else
            { 
                resultSet = statement.executeQuery(
                        "select count,inactive,level from levels_data where timestamp=" + String.valueOf(lastEntryTime));
                
                int chartLevel = Integer.parseInt(levelType.replaceAll("[^0-9]", "")); 
                
                while(resultSet.next())
                {
                    if(resultSet.getInt("level") != chartLevel)
                        continue;
                    
                    data[0][0] += resultSet.getInt("count") - resultSet.getInt("inactive");
                    data[1][0] += resultSet.getInt("inactive");
                    activesCount = data[0][0];
                    inactivesCount = data[1][0];
                }
            }
            
            statement = connection.createStatement();
            resultSet = statement.executeQuery(
                "select minters_count from minters_data where timestamp=" + String.valueOf(lastEntryTime));  
            resultSet.next();
            int minterCount = resultSet.getInt("minters_count");
            
            String[] levels = new String[]{String.format("On %s\n%s actives %s, inactives %s",
                   Utilities.DateFormatShort(lastEntryTime),levelType,Utilities.numberFormat((int)activesCount),
                   Utilities.numberFormat((int)inactivesCount))};            

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
    
    private CategoryDataset createActiveRatioAllDataset(int maxLevel,long tableTime)
    {        
        int chartLevel = 0; 
        
        String[] tiers = new String[]{"Active","Inactive"};
        String[] levels = new String[maxLevel];
            for(int i = 0; i < maxLevel;i++)
                levels[i] = "Level " + i;
        
        double[][] data = new double[2][maxLevel]; //[tier][level]  
        int minterCount = 0;
        
        try(Connection connection = ConnectionDB.getConnection("minters"))
        {
            long lastEntryTime = (long) BackgroundService.GUI.dbManager.GetColumn(
                    "levels_data", "timestamp", "timestamp", "desc", connection).get(0);
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("select count,inactive,level from levels_data where timestamp=" + lastEntryTime);
            
            while(resultSet.next())
            {
                int count = resultSet.getInt("count");
                int inactive = resultSet.getInt("inactive");
                int level = resultSet.getInt("level");
                
                minterCount += count;
                
                data[0][level] = count - inactive;
                data[1][level] = inactive;
            }
        }
        catch (Exception e)
        {
            BackgroundService.AppendLog(e);
        }
        
        CategoryDataset dataset = DatasetUtils.createCategoryDataset(tiers,levels,data);

        subtitle = "Total of " + minterCount + " minters found in ";
        subtitle += maxLevel == 1 ? "level " + chartLevel : "network";
        subtitle += " on " + Utilities.DateFormatShort(tableTime);
        return dataset;
    }
    
    private CategoryDataset createNamesNetworkDataset(long tableTime,JTable mintersTable)
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
    
    private CategoryDataset createLevelUpDataset(String levelType,long tableTime)
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
    
    private CategoryDataset createBalanceDistDataset(int maxLevel,long tableTime)
    {
        try(Connection connection = ConnectionDB.getConnection("minters"))
        {                
            String[] tiers = new String[maxLevel];
            for(int i = 0; i < tiers.length;i++)
            {
                tiers[i] = "Level " + i + " balance";
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
                if(rowLevel + 1 > maxLevel)//+1 accounts for level 0's
                    continue;
                
                Object obj = resultSet.getObject("balance");                    
                
                if(obj != null)
                {
                    data[rowLevel][0] += (double)obj;
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
    
    private CategoryDataset createBphDistDataset(int maxLevel,long tableTime,JTable mintersTable)
    {      
        //creates 6 piecharts for every bph tier and displays the percentage of each level that is represented in those charts
        String[] charts = new String[]{"1-10 blocks","11-20 blocks","21-30 blocks","31-40 blocks","41-50 blocks","51-60 blocks"};
        String[] tiers = new String[maxLevel];
        for(int i = 0; i < maxLevel;i++)
            tiers[i] = "Level " + i;
        
        double[][] data = new double[maxLevel][6]; //[tier][level]
        
        for(int i = 0; i < mintersTable.getRowCount();i++)
        {
            int level = (int) mintersTable.getValueAt(i, 3);    
            
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
    
    private CategoryDataset createBphNetworkDataset(long tableTime,JTable mintersTable)
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

    private JFreeChart createChart(String title,CategoryDataset dataset)
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
                new DecimalFormat("0.0%")));
        p.setMaximumLabelWidth(0.20);
        return chart;
    }

}
