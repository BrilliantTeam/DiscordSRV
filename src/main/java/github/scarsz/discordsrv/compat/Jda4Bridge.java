/*
 * DiscordSRV - https://github.com/DiscordSRV/DiscordSRV
 *
 * Copyright (C) 2016 - 2024 Austin "Scarsz" Shapiro
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

package github.scarsz.discordsrv.compat;

import github.scarsz.discordsrv.api.events.AchievementMessagePostProcessEvent;
import github.scarsz.discordsrv.api.events.DeathMessagePostProcessEvent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponent;
import net.dv8tion.jda.api.components.attribute.ICustomId;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.utils.AttachedFile;
import net.dv8tion.jda.api.utils.AttachmentUpdate;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageCreateRequest;
import net.dv8tion.jda.api.utils.messages.MessageEditRequest;
import net.dv8tion.jda.api.utils.messages.MessageRequest;

import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings({"unused", "unchecked", "rawtypes"})
public final class Jda4Bridge {

    private Jda4Bridge() {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Redirect {
        Class<?> owner();
        String name();
        boolean isStatic() default false;
    }

    public enum AttachmentOption { SPOILER }

    public static final class CommandPrivilege {
        private final long id;
        private CommandPrivilege(long id) { this.id = id; }
        public static CommandPrivilege enableRole(long id) { return new CommandPrivilege(id); }
        public static CommandPrivilege enableRole(String id) { return new CommandPrivilege(Long.parseLong(id)); }
        public static CommandPrivilege enableUser(long id) { return new CommandPrivilege(id); }
        public static CommandPrivilege enableUser(String id) { return new CommandPrivilege(Long.parseLong(id)); }
        public long getIdLong() { return id; }
        public String getId() { return Long.toString(id); }
    }


    @Redirect(owner = Message.class, name = "getTextChannel")
    public static TextChannel getTextChannel(Message message) {
        return message.getChannel().asTextChannel();
    }

    @Redirect(owner = MessageReceivedEvent.class, name = "getTextChannel")
    public static TextChannel getTextChannel(MessageReceivedEvent event) {
        return event.getChannel().asTextChannel();
    }

    @Redirect(owner = Message.class, name = "getMentionedUsers")
    public static List getMentionedUsers(Message message) {
        return message.getMentions().getUsers();
    }

    @Redirect(owner = Message.class, name = "getMentionedMembers")
    public static List getMentionedMembers(Message message) {
        return message.getMentions().getMembers();
    }

    @Redirect(owner = Message.class, name = "getMentionedRoles")
    public static List getMentionedRoles(Message message) {
        return message.getMentions().getRoles();
    }

    @Redirect(owner = Message.class, name = "getMentionedChannels")
    public static List getMentionedChannels(Message message) {
        return message.getMentions().getChannels();
    }

    @Redirect(owner = Message.class, name = "mentionsEveryone")
    public static boolean mentionsEveryone(Message message) {
        return message.getMentions().mentionsEveryone();
    }

    @Redirect(owner = Message.Attachment.class, name = "retrieveInputStream")
    public static CompletableFuture retrieveInputStream(Message.Attachment attachment) {
        return attachment.getProxy().download();
    }

    @Redirect(owner = TextChannel.class, name = "sendMessage")
    public static RestAction sendMessage(TextChannel channel, MessageEmbed embed) {
        return channel.sendMessageEmbeds(embed);
    }


    private static final Map<Object, List<AttachedFile>> EDIT_ATTACHMENTS = Collections.synchronizedMap(new WeakHashMap<>());

    private static RestAction addAttachment(RestAction action, AttachedFile file) {
        if (action instanceof MessageCreateRequest) return (RestAction) ((MessageCreateRequest) action).addFiles((FileUpload) file);
        List<AttachedFile> files = EDIT_ATTACHMENTS.computeIfAbsent(action, k -> new ArrayList<>());
        files.add(file);
        return (RestAction) ((MessageEditRequest) action).setAttachments(new ArrayList<>(files));
    }

    private static FileUpload fileUpload(byte[] data, String name, AttachmentOption[] options) {
        FileUpload upload = FileUpload.fromData(data, name);
        return options != null && Arrays.asList(options).contains(AttachmentOption.SPOILER) ? upload.asSpoiler() : upload;
    }

    @Redirect(owner = RestAction.class, name = "addFile")
    public static RestAction addFile(RestAction action, byte[] data, String name, AttachmentOption[] options) {
        return addAttachment(action, fileUpload(data, name, options));
    }

    @Redirect(owner = RestAction.class, name = "addFile")
    public static RestAction addFile(RestAction action, InputStream data, String name, AttachmentOption[] options) {
        return addAttachment(action, FileUpload.fromData(data, name));
    }

    @Redirect(owner = RestAction.class, name = "retainFiles")
    public static RestAction retainFiles(RestAction action, Collection attachments) {
        if (!(action instanceof MessageEditRequest)) return action;
        List<AttachedFile> files = EDIT_ATTACHMENTS.computeIfAbsent(action, k -> new ArrayList<>());
        for (Object attachment : attachments) files.add(AttachmentUpdate.fromAttachment((Message.Attachment) attachment));
        return (RestAction) ((MessageEditRequest) action).setAttachments(new ArrayList<>(files));
    }

    @Redirect(owner = RestAction.class, name = "setActionRows")
    public static RestAction setActionRows(RestAction action, Collection rows) {
        return (RestAction) ((MessageRequest) action).setComponents(rows);
    }

    @Redirect(owner = RestAction.class, name = "setActionRows")
    public static RestAction setActionRows(RestAction action, ActionRow[] rows) {
        return (RestAction) ((MessageRequest) action).setComponents(rows);
    }

    @Redirect(owner = RestAction.class, name = "setEmbeds")
    public static RestAction setEmbeds(RestAction action, Collection embeds) {
        return (RestAction) ((MessageRequest) action).setEmbeds(embeds);
    }

    @Redirect(owner = RestAction.class, name = "setEmbeds")
    public static RestAction setEmbeds(RestAction action, MessageEmbed[] embeds) {
        return (RestAction) ((MessageRequest) action).setEmbeds(embeds);
    }

    @Redirect(owner = RestAction.class, name = "content")
    public static RestAction content(RestAction action, String content) {
        return (RestAction) ((MessageRequest) action).setContent(content);
    }

    @Redirect(owner = RestAction.class, name = "append")
    public static RestAction append(RestAction action, CharSequence content) {
        return (RestAction) ((MessageCreateAction) action).addContent(content.toString());
    }


    @Redirect(owner = WebhookMessageEditAction.class, name = "addFile")
    public static WebhookMessageEditAction addFile(WebhookMessageEditAction action, byte[] data, String name, AttachmentOption[] options) {
        return (WebhookMessageEditAction) addAttachment(action, fileUpload(data, name, options));
    }

    @Redirect(owner = WebhookMessageEditAction.class, name = "retainFiles")
    public static WebhookMessageEditAction retainFiles(WebhookMessageEditAction action, Collection attachments) {
        return (WebhookMessageEditAction) retainFiles((RestAction) action, attachments);
    }

    @Redirect(owner = WebhookMessageEditAction.class, name = "setActionRows")
    public static WebhookMessageEditAction setActionRows(WebhookMessageEditAction action, Collection rows) {
        return (WebhookMessageEditAction) action.setComponents(rows);
    }

    @Redirect(owner = WebhookMessageEditAction.class, name = "setActionRows")
    public static WebhookMessageEditAction setActionRows(WebhookMessageEditAction action, ActionRow[] rows) {
        return (WebhookMessageEditAction) action.setComponents(rows);
    }

    @Redirect(owner = WebhookMessageEditAction.class, name = "setEmbeds")
    public static WebhookMessageEditAction setEmbeds(WebhookMessageEditAction action, Collection embeds) {
        return (WebhookMessageEditAction) action.setEmbeds(embeds);
    }


    @Redirect(owner = ReplyCallbackAction.class, name = "addActionRows")
    public static ReplyCallbackAction addActionRows(ReplyCallbackAction action, ActionRow[] rows) {
        return action.addComponents(rows);
    }

    @Redirect(owner = ReplyCallbackAction.class, name = "addActionRows")
    public static ReplyCallbackAction addActionRows(ReplyCallbackAction action, Collection rows) {
        return action.addComponents(rows);
    }

    @Redirect(owner = ReplyCallbackAction.class, name = "addActionRow")
    public static ReplyCallbackAction addActionRow(ReplyCallbackAction action, ActionRowChildComponent[] components) {
        return action.addComponents(ActionRow.of(Arrays.asList(components)));
    }

    @Redirect(owner = ReplyCallbackAction.class, name = "addFile")
    public static ReplyCallbackAction addFile(ReplyCallbackAction action, byte[] data, String name, AttachmentOption[] options) {
        return action.addFiles(fileUpload(data, name, options));
    }


    @Redirect(owner = ActionRow.class, name = "of", isStatic = true)
    public static ActionRow actionRowOf(ActionRowChildComponent[] components) {
        return ActionRow.of(Arrays.asList(components));
    }

    @Redirect(owner = ActionRowChildComponent.class, name = "getId")
    public static String getId(ActionRowChildComponent component) {
        return component instanceof ICustomId ? ((ICustomId) component).getCustomId() : null;
    }


    @Redirect(owner = SlashCommandData.class, name = "setDefaultEnabled")
    public static SlashCommandData setDefaultEnabled(SlashCommandData data, boolean enabled) {
        return data.setDefaultPermissions(enabled ? DefaultMemberPermissions.ENABLED : DefaultMemberPermissions.DISABLED);
    }

    @Redirect(owner = SlashCommandData.class, name = "isDefaultEnabled")
    public static boolean isDefaultEnabled(SlashCommandData data) {
        return !DefaultMemberPermissions.DISABLED.equals(data.getDefaultPermissions());
    }

    @Redirect(owner = Command.class, name = "isDefaultEnabled")
    public static boolean isDefaultEnabled(Command command) {
        return !DefaultMemberPermissions.DISABLED.equals(command.getDefaultPermissions());
    }


    @Redirect(owner = AchievementMessagePostProcessEvent.class, name = "getDiscordMessage")
    public static Message getDiscordMessage(AchievementMessagePostProcessEvent event) {
        return asMessage(event.getDiscordMessage());
    }

    @Redirect(owner = AchievementMessagePostProcessEvent.class, name = "setDiscordMessage")
    public static void setDiscordMessage(AchievementMessagePostProcessEvent event, Message message) {
        event.setDiscordMessage(toData(message));
    }

    @Redirect(owner = DeathMessagePostProcessEvent.class, name = "getDiscordMessage")
    public static Message getDiscordMessage(DeathMessagePostProcessEvent event) {
        return asMessage(event.getDiscordMessage());
    }

    @Redirect(owner = DeathMessagePostProcessEvent.class, name = "setDiscordMessage")
    public static void setDiscordMessage(DeathMessagePostProcessEvent event, Message message) {
        event.setDiscordMessage(toData(message));
    }

    private static final class DataMessage implements java.lang.reflect.InvocationHandler {
        private final MessageCreateData data;
        private DataMessage(MessageCreateData data) { this.data = data; }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            switch (method.getName()) {
                case "getContentRaw":
                case "getContentDisplay":
                case "getContentStripped":
                    return data.getContent();
                case "getEmbeds": return data.getEmbeds();
                case "getAttachments":
                case "getStickers":
                case "getReactions":
                    return Collections.emptyList();
                case "isTTS": return data.isTTS();
                case "toString": return "DataMessage(" + data.getContent() + ")";
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy == args[0];
                default: throw new UnsupportedOperationException(method.getName() + " is not available on an unsent message");
            }
        }
    }

    private static Message asMessage(MessageCreateData data) {
        if (data == null) return null;
        return (Message) Proxy.newProxyInstance(Message.class.getClassLoader(), new Class<?>[] {Message.class}, new DataMessage(data));
    }

    private static MessageCreateData toData(Message message) {
        if (message == null) return null;
        if (Proxy.isProxyClass(message.getClass()) && Proxy.getInvocationHandler(message) instanceof DataMessage) {
            return ((DataMessage) Proxy.getInvocationHandler(message)).data;
        }
        return MessageCreateData.fromMessage(message);
    }

}
