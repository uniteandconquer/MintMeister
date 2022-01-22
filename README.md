# MintMeister

Watch the MintMeister Introduction video at: https://rumble.com/vt2h8h-mintmeister-introduction.html

Welcome to MintMeister.

This program uses the Qortal API to query the blockchain on your Qortal node in order to find all known minters on the network. The app comes with a pre-mapped list of minters, but it will always scan for more unknown minters during mapping sessions.

The 'minters online' API call doesn't always return all known minters, but MintMeister will remember every minter that it has found on every API call (iteration) and add it to the minters list. The longer you run the minter mapper the more API calls it will make and the more minters it will eventually find.

On the first iteration of a mapping session MintMeister will note the timestamp, level and blocks minted of every minter, on every consecutive iteration it will note the current timestamp, level and blocks minted. This will enable you to gain insight into the blocks minted per hour for every minter in your list.

Note that the longer you run your mapping session, the more accurate your data will be. With shorter sessions some anomalies in the data may occur, this is due to fluctuations in the short term blocks minted data returned by the API, which will not be an issue when data is collected over a longer time period.

MintMeister extracts only minter account data from the blockchain, non-minter account data is not represented.

---

You don't need to install MintMeister, just download the file for your platform and extract the MintMeister folder to your preferred location. You can now run the program as follows:

For Windows:

double click the 'setup.bat' file in your newly extracted folder, this will create a launcher file called 'MintMeister'. Double click this file to run the program. If you ever decide to move the MintMeister folder to a different location you'll need to run the 'setup.bat' file again, a new launcher will be created using the path variable of the new location.

For Mac and Linux:

right click the 'launch.sh' file and give it permission to run as an executable. Now open a terminal (console window) and drag and drop the 'launch.sh' file into the terminal, then press enter. 

Another option is to type the following in the terminal:

sudo chmod +x launch.sh

then press enter and type:

./launch.sh

 (don't forget the period) then enter. Make sure that you open the terminal window in the MintMeister folder.

MintMeister is written in Java (OpenJDK 11), so you'll need to have Java OpenJDK (11 or higher) installed on any machine on which you wish to run the application. If the program doesn't open, you'll probably need to install the correct java version on your machine. Oracle OpenJDK will work, but is a bit less user friendly to install, you'll need to download the JDK, extract it to your Java folder and set the environment variables for Java. 

Adopt OpenJDK is easier to install, it installs the package automatically and sets the Java environment variables for you. Follow the link, choose OpenJDK 11 (or higher) and HotSpot, then click on latest release and install it. 

MintMeister does not need an internet connection, but it does need to have a connection to the Qortal core when mapping is active, which means it can only monitor or track data if the Qortal core is running (preferably synced) on the same machine as MintMeister,  or on a different machine that is linked via SSH tunnel. 

