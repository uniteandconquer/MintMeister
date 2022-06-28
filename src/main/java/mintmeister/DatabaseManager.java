package mintmeister;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import javax.swing.JTable;
import org.json.JSONException;
import org.json.JSONObject;
import oshi.SystemInfo;

public class DatabaseManager 
{        
    private final SystemInfo systemInfo; 
    protected static String dbFolderOS;
    protected final String myOS;
    protected String customIP = "localhost";
    protected String customPort = "12391";
    protected String socket = customIP + ":" + customPort;
    
    public DatabaseManager()
    {    
        systemInfo = new SystemInfo();
        myOS = systemInfo.getOperatingSystem().getFamily();        
        createMintersFolder();     
        SetSocket();
    } 
    
    //this method only creates a new folder if it is not present
    private  void createMintersFolder()
    {        
        dbFolderOS = System.getProperty("user.dir") + "/minters";
        
        if(Files.isDirectory(Paths.get(System.getProperty("user.dir") + "/minters")))
            return;
        
        File folder = new File(System.getProperty("user.dir") + "/minters");
        folder.mkdir(); 
    }     
    
    private void SetSocket()
    {
        File propertiesFile = new File(System.getProperty("user.dir") + "/bin/settings.json");
        if(propertiesFile.exists())
        {
            try
            {
                String jsonString = Files.readString(propertiesFile.toPath());
                if(jsonString != null)
                {
                    JSONObject jsonObject = new JSONObject(jsonString);
                    
                    String ip = jsonObject.optString("customIP");
                    customIP = ip.isBlank() ? customIP : ip;
                    String port = jsonObject.optString("customPort");
                    customPort = port.isBlank() ? customPort : port;
                    socket = customIP + ":" + customPort;
                }                
            }
            catch (IOException | JSONException e)
            {
                BackgroundService.AppendLog(e);
            }
        }
    } 
    
    public boolean TableExists(String table, Connection c)
    {
        try
        {
            String sqlString = "select * from " + table + " limit 1";//limit result to 1 row
            Statement stmt = c.createStatement();
            //no return value needed, just need to know if error is thrown
            stmt.executeQuery(sqlString);
            
            //if no error was thrown, the table exists
            return  true;    
        }
        catch (SQLException e)
        {
            return false;
        }
    }
    
    //args[0] = table name, args[1..,3..,5..etc] = keys, args[2..,4..,6..etc] = type
    public void CreateTable(String[] args, Connection c)
    {
        String sqlString = "create table if not exists " + args[0];            
        if(args.length > 1)
        {
            sqlString += " (";                
            for(int i = 1; i < args.length - 1; i++)
                sqlString += i % 2 == 1 ? args[i] + " " : args[i] + ",";                
            sqlString += args[args.length - 1] + ")";                
            ExecuteUpdate(sqlString,c);
        }         
    }
    
    public void ChangeValue(String table, String item,  String itemValue, String key, String keyValue, Connection c)
    { 
        String sqlString = String.format("update %s set %s=%s where %s=%s", table,item,itemValue,key,keyValue); 
        ExecuteUpdate(sqlString,c);       
    }    
    
    public void ChangeValue(String table, String item,  String itemValue, String condition,  Connection c)
    { 
        String sqlString = String.format("update %s set %s=%s where %s", table,item,itemValue,condition); 
        ExecuteUpdate(sqlString,c);       
    }   
    
    public void ChangeValues(String table, ArrayList<KeyItemPair>pairs,Connection c)
    {
        pairs.stream().map(pair ->
        {
            return String.format("update %s set %s=%s where %s=%s", table,pair.item,pair.itemValue,pair.key,pair.keyValue);
        }).forEachOrdered(sqlString ->
        {                
            ExecuteUpdate(sqlString,c);
        }); 
    }
    
    //args[0] = table name, args[1..,3..,5..etc] = keys, values[2..,4..,6..etc] = value
    public void InsertIntoDB(String[] args,Connection c)
    {  
        String  sqlString = "insert into " + args[0];
                 
        sqlString += " (";
        for(int i = 1; i < args.length; i+=2)
            sqlString += i + 2 == args.length ? args[i] + ") values (" : args[i] + ",";
        for(int i = 2; i < args.length; i+=2)
            sqlString += i == args.length - 1 ? args[i] + ")" : args[i] + ",";   

         ExecuteUpdate(sqlString,c);                
    }
    
    public void InsertIntoColumn(String[] args,Connection c)
    {  
        String  sqlString = "insert into " + args[0] + " values ";
           
        for(int i = 1; i < args.length; i++)
            sqlString += i + 1 == args.length ? "(" + args[i] + ")" : "(" + args[i] + "),";        

         ExecuteUpdate(sqlString,c);                
    }    
    
    public ArrayList<String> GetTables(Connection c)
    {
        try 
        {      
            ArrayList tables = new ArrayList<String>();
            String sqlString = "show tables";
            Statement stmt = c.createStatement();
            ResultSet resultSet = stmt.executeQuery(sqlString);
            while (resultSet.next())
                tables.add(resultSet.getString(1));
            
            return tables;
        } 
        catch (SQLException e) 
        {
            BackgroundService.AppendLog(e);
        }
        
        return null;        
    }    
    
    //Gets the value type of the column in a table, the header for the columns so to speak
    public ArrayList<String> GetColumnHeaders(String table, Connection c)
    {
        try 
        {        
            ArrayList items = new ArrayList<String>();           
            String sqlString = "show columns from " + table;
            Statement stmt = c.createStatement();
            ResultSet resultSet = stmt.executeQuery(sqlString);
            while(resultSet.next())
                items.add(resultSet.getString(1));

            return items;
        } 
        catch (SQLException e) 
        {
            BackgroundService.AppendLog(e);
        }        
        return null;        
    }
    
    //Gets all the items in the specified column
    public ArrayList<Object> GetColumn(String table, String column, String orderKey,String order, Connection c)
    {
         try 
        {        
            String orderString = orderKey.isEmpty() ? orderKey : " order by " + orderKey + " " + order;
            ArrayList items = new ArrayList<String>();            
            String sqlString = "select " + column + "  from " + table + orderString;
            Statement stmt = c.createStatement();
            ResultSet resultSet = stmt.executeQuery(sqlString);
            while(resultSet.next())
                items.add(resultSet.getObject(1));            
            
            return items;
        } 
        catch (SQLException e) 
        {
            BackgroundService.AppendLog(e);
        }
        
        return null;
    }    
    
        
    //Gets all the items in the specified column
    public ArrayList<Object> GetColumn(String table, String column,String condition, String orderKey,String order, Connection c)
    {
         try 
        {        
            String orderString = orderKey.isEmpty() ? orderKey : " order by " + orderKey + " " + order;
            ArrayList items = new ArrayList<String>();            
            String sqlString = "select " + column + "  from " + table + condition + orderString;
            Statement stmt = c.createStatement();
            ResultSet resultSet = stmt.executeQuery(sqlString);
            while(resultSet.next())
                items.add(resultSet.getObject(1));            
            
            return items;
        } 
        catch (SQLException e) 
        {
            BackgroundService.AppendLog(e);
        }
        
        return null;
    }
    
    //Gets all the items in the specified column
    public ArrayList<Object> GetColumn(String table, String column, String orderKey,String order, int limit, Connection c)
    {
         try 
        {        
            String orderString = orderKey.isEmpty() ? orderKey : " order by " + orderKey + " " + order + " limit " + limit;
            ArrayList items = new ArrayList<String>();            
            String sqlString = "select " + column + "  from " + table + orderString;
            Statement stmt = c.createStatement();
            ResultSet resultSet = stmt.executeQuery(sqlString);
            while(resultSet.next())
                items.add(resultSet.getObject(1));            
            
            return items;
        } 
        catch (SQLException e) 
        {
            BackgroundService.AppendLog(e);
        }
        
        return null;
    }       
    
    public ArrayList<Object> GetRow(String table, String key, String keyValue,Connection c)
    {
         try 
        {        
            ArrayList items = new ArrayList<String>();           
            String sqlString = String.format("select * from %s where %s=%s", table,key,keyValue);
            Statement stmt  = c.createStatement();
            ResultSet resultSet = stmt.executeQuery(sqlString);
            int columnCount = resultSet.getMetaData().getColumnCount();
            while(resultSet.next())
            {
                for(int i=1;i <=columnCount;i++)
                    items.add(resultSet.getObject(i));
            }            
            
            return items;
        } 
        catch (SQLException e) 
        {
            BackgroundService.AppendLog(e);
        }
        
        return null;
    }  
    
    public int getRowCount(String table, Connection c)
    {
        try
        {
            Statement statement = c.createStatement();
            ResultSet resultSet = statement.executeQuery("select count(*) from " + table);
            resultSet.first(); //move cursor to first (result will only have 1 position)
            return resultSet.getInt(1);            
        }
        catch (SQLException e)
        {
            BackgroundService.AppendLog(e);
        }
        return 0;
    }   
    
    /**
     * Gets the specified item at the first row of the specified table,<br>
     * Used for tables that have only one row and do not need to change<br>
     * the value of single items.This way we don't need a key to identify a row.
     * @param table
     * @param item
     * @param c
     * @return the requested value as an Object*/
    public Object GetFirstItem(String table,String item,Connection c)
    {        
         try 
        {   
            Object value;
            String sqlString = String.format("select %s from %s limit 1", item, table);
            Statement stmt = c.createStatement();
            ResultSet resultSet = stmt.executeQuery(sqlString);
            resultSet.first();
            value = resultSet.getObject(1);      
                 
            return value;
        } 
        catch (SQLException e) 
        {
            BackgroundService.AppendLog(e);
            BackgroundService.AppendLog(String.format("Item '%s' returned null for table '%s'", item,table));
            return null;
        }
    }    
    
    public Object GetFirstItem(String table,String item,String orderKey, String order,Connection c)
    {        
         try 
        {   
            Object value;
            String sqlString = String.format("select %s from %s order by %s %s limit 1", item, table,orderKey,order);
            Statement stmt = c.createStatement();
            ResultSet resultSet = stmt.executeQuery(sqlString);
            resultSet.first();
            value = resultSet.getObject(1);      
                 
            return value;
        } 
        catch (SQLException e) 
        {
            BackgroundService.AppendLog(e);
            BackgroundService.AppendLog(String.format("Item '%s' returned null for table '%s'", item,table));
            return null;
        }
    }   
    
     public Object GetFirstItem(String table,String item,String filterKey,String orderKey, String order,Connection c)
    {        
         try 
        {   
            Object value;
            String sqlString = String.format("select %s from %s %s order by %s %s limit 1", item, table,filterKey,orderKey,order);
            Statement stmt = c.createStatement();
            ResultSet resultSet = stmt.executeQuery(sqlString);
            resultSet.first();
            value = resultSet.getObject(1);      
                 
            return value;
        } 
        catch (SQLException e) 
        {
            BackgroundService.AppendLog(e);
            BackgroundService.AppendLog(String.format("Item '%s' returned null for table '%s'", item,table));
            return null;
        }
    }  
    
    public Object GetItemValue(String table,String item,String key, String keyValue,Connection c)
    {        
         try 
        {   
            Object value;
            String sqlString = String.format("select %s from %s where %s=%s", item, table, key, keyValue);
            Statement stmt = c.createStatement();
            ResultSet resultSet = stmt.executeQuery(sqlString);
            resultSet.first();
            value = resultSet.getObject(1);            
            
            return value;
        } 
        catch (SQLException e) 
        {
            //Since the methods calling this function (get account ID by name) sometimes expect a null return value, 
            //we don't want to print the stacktrace to the log everytime this exception is thrown
            BackgroundService.AppendLog(e.toString() + " @ GetItemValue() (ignore if thrown for charttree selection)");
        }
        
        return null;
    }    
    
    public Object GetItemValue(String table,String item,String condition,Connection c)
    {        
         try 
        {   
            Object value;
            String sqlString = String.format("select %s from %s %s", item, table,condition);
            Statement stmt = c.createStatement();
            ResultSet resultSet = stmt.executeQuery(sqlString);
            resultSet.first();
            value = resultSet.getObject(1);            
            
            return value;
        } 
        catch (SQLException e) 
        {
            //Since the methods calling this function (get account ID by name) sometimes expect a null return value, 
            //we don't want to print the stacktrace to the log everytime this exception is thrown
            BackgroundService.AppendLog(e.toString() + " @ GetItemValue() (ignore if thrown for charttree selection)");
        }
        
        return null;
    }  
    
    public Object tryGetItemValue(String table,String item,String key, String keyValue,Connection c)
    {        
         try 
        {   
            Object value;
            String sqlString = String.format("select %s from %s where %s=%s", item, table, key, keyValue);
            Statement stmt = c.createStatement();
            ResultSet resultSet = stmt.executeQuery(sqlString);
            resultSet.first();
            value = resultSet.getObject(1);            
            
            return value;
        } 
        catch (SQLException e) 
        {
            return null;
        }
    }    
    
     public void FillJTable(String table,String whereKey, JTable jTable, boolean isEditable,Connection c)
    {
        try 
        {      
            String header = GetColumnHeaders(table, c).get(0); //will be "ID" or "Timestamp"  
            String query = String.format("select * from %s %s order by %s asc", table,whereKey,header);
            Statement stmt = c.createStatement();
            ResultSet resultSet = stmt.executeQuery(query);    
            jTable.setModel(Utilities.BuildTableModel(table,resultSet,isEditable));
        } 
        catch (SQLException e) 
        {
            BackgroundService.AppendLog(e);
        }        
    }  
     
    public void FillJTableOrder(String table, String whereKey, String order, JTable jTable, boolean isEditable, Connection c)
    {
        try
        {
//            String header = GetColumnHeaders(table, c).get(0); //will be "ID" or "Timestamp"  
            String query = String.format("select * from %s order by %s %s", table, whereKey, order); //,header);
            Statement stmt = c.createStatement();
            ResultSet resultSet = stmt.executeQuery(query);
            jTable.setModel(Utilities.BuildTableModel(table, resultSet,isEditable));
        }
        catch (SQLException e)
        {
            BackgroundService.AppendLog(e);
        }
    }         
    
    public void FillJTableOrder(String table, String whereKey, String order,int limit, JTable jTable, boolean isEditable,Connection c)
    {
        try
        {
//            String header = GetColumnHeaders(table, c).get(0); //will be "ID" or "Timestamp"  
            String query = String.format("select * from %s order by %s %s limit %d", table, whereKey, order,limit); //,header);
            Statement stmt = c.createStatement();
            ResultSet resultSet = stmt.executeQuery(query);
            jTable.setModel(Utilities.BuildTableModel(table, resultSet,isEditable));
        }
        catch (SQLException e)
        {
            BackgroundService.AppendLog(e);
        }
    } 
    
    public void ExecuteUpdate(String statementString, Connection c)
    {
        try
        { 
            Statement stmt = c.createStatement();
            stmt.executeUpdate(statementString);
            c.commit();    
        }
        catch (SQLException e)
        {
            BackgroundService.AppendLog(e);
        }     
    }  
    
}//end class