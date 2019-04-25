package think.rpgitems;

import cat.nyaa.nyaacore.LanguageRepository;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import think.rpgitems.item.ItemManager;
import think.rpgitems.item.RPGItem;
import think.rpgitems.power.Attribute;
import think.rpgitems.power.RPGCommandReceiver;

import java.util.Objects;
import java.util.Optional;

import static think.rpgitems.item.RPGItem.*;
import static think.rpgitems.utils.ItemTagUtils.*;

public class UserHandler extends RPGCommandReceiver {
    private final RPGItems plugin;

    UserHandler(RPGItems plugin, LanguageRepository i18n) {
        super(plugin, i18n);
        this.plugin = plugin;
    }

    @Override
    public String getHelpPrefix() {
        return "";
    }

    @SubCommand(value = "info", permission = "info")
    @Attribute("command")
    public void info(CommandSender sender, Arguments args) {
        Player p = asPlayer(sender);
        RPGItem item = getItem(sender, false);
        ItemStack itemStack = p.getInventory().getItemInMainHand();
        item.print(sender, false);
        PersistentDataContainer tagContainer = Objects.requireNonNull(itemStack.getItemMeta()).getPersistentDataContainer();
        PersistentDataContainer metaTag = getTag(tagContainer, TAG_META);
        Optional<Boolean> optIsModel = optBoolean(metaTag, TAG_IS_MODEL);
        if (optIsModel.orElse(false)){
            msg(p, "message.model.is");
        }
    }

    @SubCommand(value = "tomodel", permission = "tomodel")
    @Attribute("command")
    public void toModel(CommandSender sender, Arguments args) {
        Player p = asPlayer(sender);
        RPGItem item = getItem(sender);
        ItemStack itemStack = p.getInventory().getItemInMainHand();
        item.toModel(itemStack);
        p.getInventory().setItemInMainHand(itemStack);
        msg(p, "message.model.to");
    }

    private RPGItem getItem(CommandSender sender) {
        return getItem(sender, true);
    }

    private RPGItem getItem(CommandSender sender, boolean ignoreModel) {
        Player p = (Player) sender;
        Optional<RPGItem> item = ItemManager.toRPGItem(p.getInventory().getItemInMainHand(), ignoreModel);
        if (item.isPresent()) {
            return item.get();
        } else {
            throw new BadCommandException("message.error.iteminhand");
        }
    }
}
