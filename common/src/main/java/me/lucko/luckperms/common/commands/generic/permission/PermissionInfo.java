/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.common.commands.generic.permission;

import me.lucko.luckperms.api.LocalizedNode;
import me.lucko.luckperms.api.Node;
import me.lucko.luckperms.common.command.CommandManager;
import me.lucko.luckperms.common.command.CommandResult;
import me.lucko.luckperms.common.command.abstraction.SharedSubCommand;
import me.lucko.luckperms.common.command.access.ArgumentPermissions;
import me.lucko.luckperms.common.command.access.CommandPermission;
import me.lucko.luckperms.common.command.utils.ArgumentParser;
import me.lucko.luckperms.common.command.utils.MessageUtils;
import me.lucko.luckperms.common.command.utils.SortMode;
import me.lucko.luckperms.common.command.utils.SortType;
import me.lucko.luckperms.common.locale.LocaleManager;
import me.lucko.luckperms.common.locale.command.CommandSpec;
import me.lucko.luckperms.common.locale.message.Message;
import me.lucko.luckperms.common.model.PermissionHolder;
import me.lucko.luckperms.common.node.comparator.NodeWithContextComparator;
import me.lucko.luckperms.common.node.factory.NodeFactory;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import me.lucko.luckperms.common.sender.Sender;
import me.lucko.luckperms.common.utils.DurationFormatter;
import me.lucko.luckperms.common.utils.Iterators;
import me.lucko.luckperms.common.utils.Predicates;
import me.lucko.luckperms.common.utils.TextUtils;

import net.kyori.text.BuildableComponent;
import net.kyori.text.TextComponent;
import net.kyori.text.event.ClickEvent;
import net.kyori.text.event.HoverEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class PermissionInfo extends SharedSubCommand {
    public PermissionInfo(LocaleManager locale) {
        super(CommandSpec.PERMISSION_INFO.localize(locale), "info", CommandPermission.USER_PERM_INFO, CommandPermission.GROUP_PERM_INFO, Predicates.notInRange(0, 2));
    }

    @Override
    public CommandResult execute(LuckPermsPlugin plugin, Sender sender, PermissionHolder holder, List<String> args, String label, CommandPermission permission) {
        if (ArgumentPermissions.checkViewPerms(plugin, sender, permission, holder)) {
            Message.COMMAND_NO_PERMISSION.send(sender);
            return CommandResult.NO_PERMISSION;
        }

        int page = ArgumentParser.parseIntOrElse(0, args, 1);
        SortMode sortMode = SortMode.determine(args);

        // get the holders nodes
        List<LocalizedNode> nodes = new ArrayList<>(holder.enduringData().asSortedSet());

        // remove irrelevant types (these are displayed in the other info commands)
        nodes.removeIf(node -> (node.isGroupNode() && node.getValue() && plugin.getGroupManager().isLoaded(node.getGroupName())) ||
        // remove if the node is a meta node
        node.isPrefix() || node.isSuffix() || node.isMeta()
        );

        // handle empty
        if (nodes.isEmpty()) {
            Message.PERMISSION_INFO_NO_DATA.send(sender, holder.getFriendlyName());
            return CommandResult.SUCCESS;
        }

        // sort the list alphabetically instead
        if (sortMode.getType() == SortType.ALPHABETICALLY) {
            nodes.sort(ALPHABETICAL_NODE_COMPARATOR);
        }

        // reverse the order if necessary
        if (!sortMode.isAscending()) {
            Collections.reverse(nodes);
        }

        int pageIndex = page - 1;
        List<List<LocalizedNode>> pages = Iterators.divideIterable(nodes, 19);

        if (pageIndex < 0 || pageIndex >= pages.size()) {
            page = 1;
            pageIndex = 0;
        }

        List<LocalizedNode> content = pages.get(pageIndex);

        // send header
        Message.PERMISSION_INFO.send(sender, holder.getFriendlyName(), page, pages.size(), nodes.size());

        // send content
        for (LocalizedNode node : content) {
            String s = "&3> " + (node.getValue() ? "&a" : "&c") + node.getPermission() + (sender.isConsole() ? " &7(" + node.getValue() + "&7)" : "") + MessageUtils.getAppendableNodeContextString(node);
            if (node.isTemporary()) {
                s += "\n&2-    expires in " + DurationFormatter.LONG.formatDateDiff(node.getExpiryUnixTime());
            }

            TextComponent message = TextUtils.fromLegacy(s, CommandManager.AMPERSAND_CHAR).toBuilder().applyDeep(makeFancy(holder, label, node)).build();
            sender.sendMessage(message);
        }

        return CommandResult.SUCCESS;
    }

    private static final Comparator<LocalizedNode> ALPHABETICAL_NODE_COMPARATOR = (o1, o2) -> {
        int i = o1.getPermission().compareTo(o2.getPermission());
        if (i != 0) {
            return i;
        }

        // fallback to priority
        return NodeWithContextComparator.reverse().compare(o1, o2);
    };

    private static Consumer<BuildableComponent.Builder<?, ?>> makeFancy(PermissionHolder holder, String label, Node node) {
        HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextUtils.fromLegacy(TextUtils.joinNewline(
                "¥3> " + (node.getValue() ? "¥a" : "¥c") + node.getPermission(),
                " ",
                "¥7Click to remove this node from " + holder.getFriendlyName()
        ), '¥'));

        String command = "/" + label + " " + NodeFactory.nodeAsCommand(node, holder.getType().isGroup() ? holder.getObjectName() : holder.getFriendlyName(), holder.getType(), false);
        ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command);

        return component -> {
            component.hoverEvent(hoverEvent);
            component.clickEvent(clickEvent);
        };
    }
}
