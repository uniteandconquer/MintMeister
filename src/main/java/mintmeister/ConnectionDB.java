package mintmeister;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import javax.swing.JOptionPane;

public class ConnectionDB 
{    
    private static final String DB_FOLDER_H2 = "jdbc:h2:./minters/";
    
    /** * This version of H2 automatically creates a database if it doesn't exist. <br>
     * IF_EXISTS argument provided by H2 was causing connection errors.<br>
     * Create all databases with this method, only connect to databases that are known to exist.
     * @param database*/
    public static void CreateDatabase(String database)
    {
        try 
        {
            Class.forName("org.h2.Driver");
            Connection c = DriverManager.getConnection(DB_FOLDER_H2 + database + ";","reqorder","");   
            c.close();
        } 
        catch (ClassNotFoundException | SQLException e) 
        {
            BackgroundService.AppendLog(e);
        }
    }      
    
    /**this method is called when db is not encrypted
     * @param database
     * @return */
    public static Connection getConnection(String database) throws NullPointerException
    {
        try 
        {
            Class.forName("org.h2.Driver");
            Connection cn = DriverManager.getConnection(DB_FOLDER_H2 + database +
                    ";TRACE_LEVEL_FILE=0;TRACE_LEVEL_SYSTEM_OUT=0","reqorder","");
            return cn;     
        } 
        catch (ClassNotFoundException | SQLException e) 
        {
            BackgroundService.AppendLog(e);
            String[] split = Main.BUNDLE.getString("connectWarning").split("%%");
            JOptionPane.showMessageDialog(null,
                    Utilities.AllignCenterHTML(String.format(split[0] + "%s" + split[1], database)));
            throw new NullPointerException();
        }
    }      
}
