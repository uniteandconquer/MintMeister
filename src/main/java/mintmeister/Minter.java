package mintmeister;

public class Minter
{
    protected String address;
    protected String name;
    protected int level;
    protected int blocksMintedStart;
    protected long timestampStart;
    protected int blocksPerHour;
    
    public Minter(String address, String name, int level,int blocksMintedStart,long timestampStart)
    {
        this.address = address;
        this.name = name;
        this.level = level;
        this.blocksMintedStart = blocksMintedStart;
        this.timestampStart = timestampStart;
    }
}
