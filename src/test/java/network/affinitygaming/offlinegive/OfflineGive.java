package network.affinitygaming.offlinegive;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class OfflineGive extends JavaPlugin implements Listener
{

    /** All the PendingItem objects in memory */
    private List<PendingItem> pending = new ArrayList<PendingItem>();

    @Override
    public void onLoad()
    {
        getDataFolder().mkdirs();
        if (!new File(getDataFolder(), "config.yml").exists())
            saveDefaultConfig();
    }

    @Override
    public void onEnable()
    {
        load();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Enabled");
    }

    /**
     * Load all data from the configuration file
     */
    private void load()
    {
        reloadConfig();
        pending.clear();
        List<String> names = new ArrayList<String>();
        if (!getConfig().isSet("all"))
            return;
        names = getConfig().getStringList("all");
        if (names.isEmpty())
            return;
        for (String name : names)
            addPending(name, getConfig().getString("pending." + name));
        getLogger().info("Settings loaded from configuration");
    }

    @Override
    public void onDisable()
    {
        save();
        getLogger().info("Disabled");
    }

    /**
     * Save all data to the configuration file
     */
    private void save()
    {
        List<String> ret = new ArrayList<String>();
        getConfig().set("pending", null);
        for (PendingItem p : pending)
        {
            getConfig().set("pending." + p.player, p.toString());
            ret.add(p.player);
        }
        getConfig().set("all", ret);
        saveConfig();
        getLogger().info("Settings saved to configuration");
    }

    private void sendMessage(CommandSender sender, String message)
    {
        if (!(sender instanceof Player))
            message = ChatColor.stripColor(message);
        sender.sendMessage(message);
    }

    @SuppressWarnings("boxing")
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        boolean hasPerms = false;
        if (!(sender instanceof Player))
            hasPerms = true;
        else if (sender instanceof Player)
        {
            Player player = (Player) sender;
            if (!player.isOp() && !player.hasPermission("offlinegive.use"))
                hasPerms = false;
            else
                hasPerms = true;
        }
        if (!hasPerms)
        {
            sendMessage(sender, "�cYou cannot use this command");
            return true;
        }
        if (args != null && args.length == 1
                && args[0].equalsIgnoreCase("-save"))
        {
            save();
            sendMessage(sender, "�aSaved to configuration");
            return true;
        }
        if (args != null && args.length == 1
                && args[0].equalsIgnoreCase("-reload"))
        {
            load();
            sendMessage(sender, "�aReloaded from configuration");
            return true;
        }
        if (args != null && args.length == 1
                && args[0].equalsIgnoreCase("-read"))
        {
            if (pending.isEmpty())
            {
                sendMessage(sender, "�7No pending items found in memory");
                return true;
            }
            sendMessage(sender, "�7Current pending items for offline players:");
            for (PendingItem p : pending)
                for (ItemStack i : p.items)
                    sendMessage(sender, String.format("�ePending for �a%s�e: �a%d �eof �a%d �e(�a%d�e)", p.player, i.getAmount(), i.getTypeId(), i.getDurability()));
            return true;
        }
        // offlinegive [name] [item] [amount] (durability)
        if (args == null || args.length < 3)
            return false;
        if (!isInt(args[2]))
            return false;
        int amount = i(args[2]);
        if (amount < 0)
        {
            sendMessage(sender, "�cThe amount cannot be negative.");
            return true;
        }
        ItemStack add;
        Material mat;
        if (isInt(args[1]))
            mat = Material.getMaterial(i(args[1]));
        else
        {
            mat = Material.getMaterial(args[1].toUpperCase());
            if (mat == null)
            {
                sendMessage(sender, "�cUnrecognized material name: " + args[1]);
                return true;
            }
        }
        add = new ItemStack(mat, amount);
        if (args.length == 4)
        {
            if (!isInt(args[3]))
                return false;
            add.setDurability((short) i(args[3]));
        }
        Player online = getServer().getPlayer(args[0]);
        if (online != null)
        {
            if (PendingItem.getSpace(online.getInventory().getContents(), add.getTypeId()) >= add.getAmount())
            {
                online.getInventory().addItem(add);
                sendMessage(sender, "�eItems given directly to �a"
                        + online.getName() + "�e, as they are online now");
            }
            else
            {
                addPending(args[0], add);
                sendMessage(sender, String.format("�a%s �edoes not have inventory room, but is online. They will recieve �a%s �eof �a%s �enext login.", args[0], args[2], args[1]));
                online.sendMessage("");
            }
        }
        else
        {
            addPending(args[0], add);
            sendMessage(sender, String.format("�a%s �ewill recieve �a%s �eof �a%s �enext login.", args[0], args[2], args[1]));
        }
        return true;
    }

    /**
     *
     * @param string
     *            String
     * @return True if Integer.valueOf(string) can be called without error
     */
    public static boolean isInt(String string)
    {
        try
        {
            Integer.valueOf(string);
            return true;
        }
        catch (Exception e)
        {}
        return false;
    }

    /**
     *
     * @param string
     *            String
     * @return int value of the String, or 0 if the String is not a number
     */
    public static int i(String string)
    {
        if (!isInt(string))
            return 0;
        return Integer.valueOf(string).intValue();
    }

    public void removePendingFor(String name)
    {
        for (PendingItem p : pending)
            if (p.player.equals(name))
            {
                pending.remove(p);
                return;
            }
    }

    public void addPending(String name, ItemStack item)
    {
        PendingItem p = getPending(name);
        if (p == null)
            pending.add(new PendingItem(this, name, item));
        else
            p.addItem(item);
    }

    public void addPending(String name, String data)
    {
        PendingItem p = getPending(name);
        if (p == null)
            pending.add(new PendingItem(this, name, data));
        else
            p.addItems(data);
    }

    public boolean hasPending(String name)
    {
        return getPending(name) != null;
    }

    public PendingItem getPending(String name)
    {
        for (PendingItem p : pending)
            if (p.player.equals(name))
                return p;
        return null;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        Player player = event.getPlayer();
        if (hasPending(player.getName()))
            getPending(player.getName()).transfer(player);
    }

}