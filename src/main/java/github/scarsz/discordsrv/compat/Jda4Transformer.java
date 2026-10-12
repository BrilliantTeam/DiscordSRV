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

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponent;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.entities.sticker.Sticker;
import net.dv8tion.jda.api.entities.sticker.StickerItem;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectInteraction;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.internal.interactions.CommandDataImpl;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class Jda4Transformer {

    private static final String JDA = internal(JDA.class).substring(0, internal(JDA.class).lastIndexOf("/api/"));
    private static final byte[] MARKER = "github/scarsz/discordsrv".getBytes(StandardCharsets.UTF_8);

    private static final Map<String, String> CLASSES = new HashMap<>();
    private static final Map<String, String> FIELDS = new HashMap<>();
    private static final Map<String, String> LISTENER_METHODS = new HashMap<>();
    private static final Map<String, Method> BRIDGES = new HashMap<>();

    static {
        old("api/entities/TextChannel", TextChannel.class);
        old("api/entities/PrivateChannel", PrivateChannel.class);
        old("api/entities/VoiceChannel", VoiceChannel.class);
        old("api/entities/GuildChannel", GuildChannel.class);
        old("api/entities/MessageChannel", MessageChannel.class);
        old("api/entities/ChannelType", ChannelType.class);
        old("api/entities/Emote", RichCustomEmoji.class);
        old("api/entities/MessageSticker", StickerItem.class);
        old("api/entities/MessageSticker$StickerFormat", Sticker.StickerFormat.class);
        old("api/events/message/guild/GuildMessageReceivedEvent", MessageReceivedEvent.class);
        old("api/events/message/priv/PrivateMessageReceivedEvent", MessageReceivedEvent.class);
        old("api/events/interaction/SlashCommandEvent", SlashCommandInteractionEvent.class);
        old("api/events/interaction/ButtonClickEvent", ButtonInteractionEvent.class);
        old("api/events/interaction/SelectionMenuEvent", StringSelectInteractionEvent.class);
        old("api/events/interaction/GenericComponentInteractionCreateEvent", GenericComponentInteractionCreateEvent.class);
        old("api/interactions/commands/build/CommandData", SlashCommandData.class);
        old("api/interactions/commands/privileges/CommandPrivilege", Jda4Bridge.CommandPrivilege.class);
        old("api/interactions/components/ActionRow", ActionRow.class);
        old("api/interactions/components/Component", ActionRowChildComponent.class);
        old("api/interactions/components/Button", Button.class);
        old("api/interactions/components/ButtonStyle", ButtonStyle.class);
        old("api/interactions/components/selections/SelectOption", SelectOption.class);
        old("api/interactions/components/selections/SelectionMenu", StringSelectMenu.class);
        old("api/interactions/components/selections/SelectionMenu$Builder", StringSelectMenu.Builder.class);
        old("api/interactions/components/selections/SelectionMenuInteraction", StringSelectInteraction.class);
        old("api/requests/restaction/MessageAction", RestAction.class);
        old("api/requests/restaction/WebhookMessageUpdateAction", WebhookMessageEditAction.class);
        old("api/requests/restaction/interactions/ReplyAction", ReplyCallbackAction.class);
        old("api/requests/restaction/interactions/UpdateInteractionAction", MessageEditCallbackAction.class);
        old("api/utils/AttachmentOption", Jda4Bridge.AttachmentOption.class);

        field(Permission.class, "MESSAGE_READ", "VIEW_CHANNEL");
        field(Permission.class, "MESSAGE_WRITE", "MESSAGE_SEND");
        field(Permission.class, "USE_SLASH_COMMANDS", "USE_APPLICATION_COMMANDS");
        field(GatewayIntent.class, "GUILD_EMOJIS", "GUILD_EXPRESSIONS");
        field(GatewayIntent.class, "GUILD_BANS", "GUILD_MODERATION");
        field(CacheFlag.class, "EMOTE", "EMOJI");

        listener("onSlashCommand", SlashCommandInteractionEvent.class, "onSlashCommandInteraction");
        listener("onButtonClick", ButtonInteractionEvent.class, "onButtonInteraction");
        listener("onSelectionMenu", StringSelectInteractionEvent.class, "onStringSelectInteraction");

        for (Method method : Jda4Bridge.class.getDeclaredMethods()) {
            Jda4Bridge.Redirect redirect = method.getAnnotation(Jda4Bridge.Redirect.class);
            if (redirect == null) continue;
            Type[] params = Type.getArgumentTypes(method);
            if (!redirect.isStatic()) params = Arrays.copyOfRange(params, 1, params.length);
            BRIDGES.put(internal(redirect.owner()) + "." + redirect.name() + Type.getMethodDescriptor(Type.getReturnType(method), params), method);
        }
    }

    private static String internal(Class<?> c) {
        return c.getName().replace('.', '/');
    }

    private static void old(String oldName, Class<?> replacement) {
        CLASSES.put(JDA + "/" + oldName, internal(replacement));
    }

    private static void field(Class<?> owner, String oldName, String newName) {
        FIELDS.put(internal(owner) + "." + oldName, newName);
    }

    private static void listener(String oldName, Class<?> event, String newName) {
        LISTENER_METHODS.put(oldName + "(" + Type.getDescriptor(event) + ")V", newName);
    }

    private Jda4Transformer() {}

    public static byte[] transform(byte[] classBytes, Consumer<String> warnings) {
        if (!contains(classBytes, MARKER)) return classBytes;

        ClassReader reader = new ClassReader(classBytes);
        ClassNode node = new ClassNode();
        reader.accept(new ClassRemapper(node, REMAPPER), 0);

        boolean listenerAdapter = internal(ListenerAdapter.class).equals(node.superName);
        for (MethodNode method : node.methods) {
            if (listenerAdapter) method.name = LISTENER_METHODS.getOrDefault(method.name + method.desc, method.name);
            if (method.instructions != null) fixInstructions(node.name, method, warnings);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static final Remapper REMAPPER = new Remapper() {
        @Override
        public String map(String internalName) {
            return CLASSES.getOrDefault(internalName, internalName);
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            return FIELDS.getOrDefault(map(owner) + "." + name, name);
        }
    };

    private static void fixInstructions(String className, MethodNode method, Consumer<String> warnings) {
        String slashCommandData = internal(SlashCommandData.class);
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof TypeInsnNode && insn.getOpcode() == Opcodes.NEW && ((TypeInsnNode) insn).desc.equals(slashCommandData)) {
                ((TypeInsnNode) insn).desc = internal(CommandDataImpl.class);
                continue;
            }
            if (!(insn instanceof MethodInsnNode)) continue;

            MethodInsnNode call = (MethodInsnNode) insn;
            if (!call.owner.startsWith("github/scarsz/discordsrv/")) continue;
            if (call.getOpcode() == Opcodes.INVOKESPECIAL && call.name.equals("<init>") && call.owner.equals(slashCommandData)) {
                call.owner = internal(CommandDataImpl.class);
            }

            Method bridge = BRIDGES.get(call.owner + "." + call.name + call.desc);
            if (bridge != null) {
                call.setOpcode(Opcodes.INVOKESTATIC);
                call.owner = internal(Jda4Bridge.class);
                call.name = bridge.getName();
                call.desc = Type.getMethodDescriptor(bridge);
                call.itf = false;
                continue;
            }

            Resolution resolution = resolve(call);
            if (resolution == null) {
                warnings.accept(className + "." + method.name + " -> " + call.owner + "." + call.name + call.desc);
                continue;
            }
            if (resolution.desc != null) {
                if (resolution.castTo != null) method.instructions.insert(call, new TypeInsnNode(Opcodes.CHECKCAST, resolution.castTo));
                call.desc = resolution.desc;
            }
            if (call.getOpcode() == Opcodes.INVOKEVIRTUAL || call.getOpcode() == Opcodes.INVOKEINTERFACE) {
                call.setOpcode(resolution.ownerIsInterface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL);
            }
            call.itf = resolution.ownerIsInterface;
        }
    }

    private static final class Resolution {
        private final boolean ownerIsInterface;
        private final String desc;
        private final String castTo;

        private Resolution(boolean ownerIsInterface, String desc, String castTo) {
            this.ownerIsInterface = ownerIsInterface;
            this.desc = desc;
            this.castTo = castTo;
        }
    }

    private static final Map<String, Optional<Resolution>> RESOLUTIONS = new ConcurrentHashMap<>();

    private static Resolution resolve(MethodInsnNode call) {
        return RESOLUTIONS.computeIfAbsent(call.owner + "." + call.name + call.desc, key -> {
            Class<?> owner = load(call.owner);
            if (owner == null) return Optional.empty();
            boolean isInterface = owner.isInterface();

            List<Executable> candidates = new ArrayList<>();
            try {
                if (call.name.equals("<init>")) {
                    candidates.addAll(Arrays.asList(owner.getDeclaredConstructors()));
                } else {
                    collectMethods(owner, call.name, candidates, new HashSet<>());
                }
            } catch (LinkageError e) {
                return Optional.of(new Resolution(isInterface, null, null));
            }

            Type[] wantedParams = Type.getArgumentTypes(call.desc);
            Type wantedReturn = Type.getReturnType(call.desc);
            for (Executable candidate : candidates) {
                if (descriptor(candidate).equals(call.desc)) {
                    return Optional.of(new Resolution(isInterface, null, null));
                }
            }
            for (Executable candidate : candidates) {
                if (candidate.getParameterCount() != wantedParams.length) continue;
                if (Modifier.isStatic(candidate.getModifiers()) != (call.getOpcode() == Opcodes.INVOKESTATIC)) continue;

                boolean paramsFit = true;
                Class<?>[] params = candidate.getParameterTypes();
                for (int i = 0; i < params.length && paramsFit; i++) {
                    Class<?> wanted = load(wantedParams[i]);
                    paramsFit = wanted != null ? params[i].isAssignableFrom(wanted) : params[i].getName().equals(wantedParams[i].getClassName());
                }
                if (!paramsFit) continue;

                String castTo = null;
                if (candidate instanceof Method) {
                    Class<?> actual = ((Method) candidate).getReturnType();
                    Class<?> wanted = load(wantedReturn);
                    if (wanted == null || actual.isPrimitive() != wanted.isPrimitive() || (actual.isPrimitive() && actual != wanted)) continue;
                    if (!wanted.isAssignableFrom(actual)) castTo = wantedReturn.getInternalName();
                }
                return Optional.of(new Resolution(isInterface, descriptor(candidate), castTo));
            }
            return Optional.empty();
        }).orElse(null);
    }

    private static void collectMethods(Class<?> type, String name, List<Executable> into, Set<Class<?>> seen) {
        if (type == null || !seen.add(type)) return;
        for (Method method : type.getDeclaredMethods()) if (method.getName().equals(name) && !method.isSynthetic()) into.add(method);
        collectMethods(type.getSuperclass(), name, into, seen);
        for (Class<?> superInterface : type.getInterfaces()) collectMethods(superInterface, name, into, seen);
        if (type.isInterface()) collectMethods(Object.class, name, into, seen);
    }

    private static String descriptor(Executable executable) {
        return executable instanceof Method
                ? Type.getMethodDescriptor((Method) executable)
                : Type.getConstructorDescriptor((Constructor<?>) executable);
    }

    private static Class<?> load(String internalName) {
        try {
            return Class.forName(internalName.replace('/', '.'), false, Jda4Transformer.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    private static Class<?> load(Type type) {
        switch (type.getSort()) {
            case Type.VOID: return void.class;
            case Type.BOOLEAN: return boolean.class;
            case Type.BYTE: return byte.class;
            case Type.CHAR: return char.class;
            case Type.SHORT: return short.class;
            case Type.INT: return int.class;
            case Type.LONG: return long.class;
            case Type.FLOAT: return float.class;
            case Type.DOUBLE: return double.class;
            case Type.ARRAY:
                Class<?> element = load(type.getElementType());
                return element == null ? null : java.lang.reflect.Array.newInstance(element, new int[type.getDimensions()]).getClass();
            default: return load(type.getInternalName());
        }
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (haystack[i + j] != needle[j]) continue outer;
            return true;
        }
        return false;
    }

}
