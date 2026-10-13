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

package github.scarsz.discordsrv.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.requests.Route;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.internal.requests.RestActionImpl;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DiscordFormatUtil {

    private static final Pattern TIMESTAMP = Pattern.compile("<t:(-?\\d{1,13})(?::([tTdDfFRsS]))?>");
    private static final Pattern SLASH_COMMAND_MENTION = Pattern.compile("</([-_\\p{L}\\p{N}]+(?: [-_\\p{L}\\p{N}]+){0,2}):\\d+>");
    private static final Pattern GUILD_NAVIGATION = Pattern.compile("<id:(customize|browse|guide|linked-roles)(?::\\d+)?>");
    private static final Pattern DISCORD_MASKED_LINK = Pattern.compile("\\[([^\\[\\]\\n]+)]\\(<?(https?://[^\\s<>()]+)>?(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern SUPPRESSED_EMBED_LINK = Pattern.compile("<(https?://[^\\s<>]+)>");
    private static final Pattern HEADING_OR_SUBTEXT = Pattern.compile("^(?:#{1,3}|-#) +", Pattern.MULTILINE);

    private static final Pattern MASKED_LINK = Pattern.compile("\\[([^\\[\\]\\n]+)]\\((https?://[^\\s()]+)\\)");
    private static final Pattern CHANNEL_LINK = Pattern.compile("https?://(?:(?:ptb|canary)\\.)?discord(?:app)?\\.com/channels/(?:\\d+|@me)/(\\d+)(/\\d+)?");
    private static final Pattern URL = Pattern.compile("(?:(https?)://)?([-\\w_.]+\\.\\w{2,})(/\\S*)?");

    private static final String CHANNEL_PREFIX = "#";
    private static final String NEWS_PREFIX = "📢";
    private static final String VOICE_PREFIX = "🔊";
    private static final String THREAD_PREFIX = "🧵";
    private static final String POST_PREFIX = "💬";
    private static final String MESSAGE_SUFFIX = " › 💬";

    private static final class ChannelInfo {
        private final String prefix;
        private final String name;
        private final String guildId;

        private ChannelInfo(String prefix, String name, String guildId) {
            this.prefix = prefix;
            this.name = name;
            this.guildId = guildId;
        }
    }

    private static final Pattern CHANNEL_MENTION = Pattern.compile("<#(\\d+)>");

    public static String channelUrl(String id) {
        ChannelInfo channel = resolveChannel(id);
        return channel != null && channel.guildId != null ? "https://discord.com/channels/" + channel.guildId + "/" + id : null;
    }

    private static final Cache<String, Optional<ChannelInfo>> CHANNELS = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build();

    private static final char TOKEN_START = '';
    private static final char TOKEN_END = '';
    private static final Pattern LINK_TOKEN = Pattern.compile(TOKEN_START + "(\\d+)" + TOKEN_END);
    private static final Pattern LEFTOVER_TOKEN_CHARS = Pattern.compile(TOKEN_START + "\\d*|" + TOKEN_END);
    private static final AtomicLong LINK_IDS = new AtomicLong();
    private static final Cache<String, String[]> LINKS = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    private static String linkToken(String label, String url) {
        String id = Long.toString(LINK_IDS.incrementAndGet());
        LINKS.put(id, new String[] {label, url});
        return TOKEN_START + id + TOKEN_END;
    }

    public static final Pattern MINECRAFT_CHAT_SYNTAX = Pattern.compile(String.join("|",
            DISCORD_MASKED_LINK.pattern(), CHANNEL_LINK.pattern(), GUILD_NAVIGATION.pattern(),
            SLASH_COMMAND_MENTION.pattern(), TIMESTAMP.pattern(), SUPPRESSED_EMBED_LINK.pattern(), CHANNEL_MENTION.pattern()));

    public static String[] renderSyntax(String syntax) {
        Matcher matcher = DISCORD_MASKED_LINK.matcher(syntax);
        if (matcher.matches()) return new String[] {matcher.group(1), matcher.group(2)};
        matcher = CHANNEL_MENTION.matcher(syntax);
        if (matcher.matches()) {
            ChannelInfo channel = resolveChannel(matcher.group(1));
            return channel != null ? new String[] {channel.prefix + channel.name, channelUrl(matcher.group(1))} : new String[] {syntax, null};
        }
        matcher = CHANNEL_LINK.matcher(syntax);
        if (matcher.matches()) {
            ChannelInfo channel = resolveChannel(matcher.group(1));
            String label = channel != null ? channel.prefix + channel.name + (matcher.group(2) != null ? MESSAGE_SUFFIX : "") : syntax;
            return new String[] {label, syntax};
        }
        matcher = GUILD_NAVIGATION.matcher(syntax);
        if (matcher.matches()) return new String[] {guildNavigationName(matcher.group(1)), null};
        matcher = SUPPRESSED_EMBED_LINK.matcher(syntax);
        if (matcher.matches()) return new String[] {matcher.group(1), matcher.group(1)};
        return new String[] {convert(syntax, ZoneId.systemDefault(), Instant.now()), null};
    }

    public static String untokenize(String message) {
        return message == null ? null : replace(LINK_TOKEN, message, m -> {
            String[] link = LINKS.getIfPresent(m.group(1));
            return link != null ? link[0] + " (" + link[1] + ")" : "";
        });
    }

    private DiscordFormatUtil() {}

    public static String convert(String message) {
        message = convert(message, ZoneId.systemDefault(), Instant.now());
        if (message != null) {
            Matcher matcher = CHANNEL_LINK.matcher(message);
            while (matcher.find()) resolveChannel(matcher.group(1));
        }
        return message;
    }

    public static String convert(String message, ZoneId zone, Instant now) {
        if (message == null || message.isEmpty()) return message;

        message = replace(TIMESTAMP, message, m -> formatTimestamp(m, zone, now));
        message = replace(SLASH_COMMAND_MENTION, message, m -> "/" + m.group(1));
        message = replace(DISCORD_MASKED_LINK, message, m -> m.group(1).equals(m.group(2)) ? m.group(2) : linkToken(m.group(1), m.group(2)));
        message = replace(SUPPRESSED_EMBED_LINK, message, m -> m.group(1));
        return HEADING_OR_SUBTEXT.matcher(message).replaceAll("");
    }

    public static String resolveChannelName(String id) {
        ChannelInfo channel = resolveChannel(id);
        return channel != null ? channel.name : null;
    }

    private static ChannelInfo resolveChannel(String id) {
        ChannelInfo cached = cachedChannel(id);
        if (cached != null) return cached;

        JDA jda = DiscordUtil.getJda();
        if (jda == null) return null;
        Optional<ChannelInfo> channel;
        try {
            channel = Optional.ofNullable(new RestActionImpl<ChannelInfo>(jda, Route.Channels.GET_CHANNEL.compile(id), (response, request) -> {
                DataObject json = response.getObject();
                String name = json.getString("name", null);
                if (name == null) return null;
                ChannelType type = ChannelType.fromId(json.getInt("type", -1));
                return new ChannelInfo(type.isThread() ? threadPrefix(jda.getGuildChannelById(json.getString("parent_id", "0"))) : channelPrefix(type), name, json.getString("guild_id", null));
            }).submit().get(3, TimeUnit.SECONDS));
        } catch (Exception e) {
            channel = Optional.empty();
        }
        CHANNELS.put(id, channel);
        return channel.orElse(null);
    }

    private static ChannelInfo cachedChannel(String id) {
        JDA jda = DiscordUtil.getJda();
        GuildChannel channel = null;
        try {
            channel = jda != null ? jda.getGuildChannelById(id) : null;
        } catch (NumberFormatException ignored) {}
        if (channel != null) {
            String prefix = channel instanceof ThreadChannel ? threadPrefix(((ThreadChannel) channel).getParentChannel()) : channelPrefix(channel.getType());
            return new ChannelInfo(prefix, channel.getName(), channel.getGuild().getId());
        }
        Optional<ChannelInfo> info = CHANNELS.getIfPresent(id);
        return info != null ? info.orElse(null) : null;
    }

    private static String threadPrefix(GuildChannel parent) {
        return parent != null && (parent.getType() == ChannelType.FORUM || parent.getType() == ChannelType.MEDIA) ? POST_PREFIX : THREAD_PREFIX;
    }

    private static String channelPrefix(ChannelType type) {
        switch (type) {
            case NEWS: return NEWS_PREFIX;
            case VOICE: return VOICE_PREFIX;
            default: return CHANNEL_PREFIX;
        }
    }

    public static Component linkify(Component component) {
        return linkify(component, id -> {
            ChannelInfo channel = cachedChannel(id);
            return channel != null ? channel.prefix + channel.name : null;
        });
    }

    public static Component linkify(Component component, Function<String, String> channelLabelById) {
        component = component.replaceText(TextReplacementConfig.builder().match(LINK_TOKEN)
                .replacement((match, builder) -> {
                    String[] link = LINKS.getIfPresent(match.group(1));
                    return link != null ? link(link[0], link[1]) : builder.content("");
                }).build());
        component = component.replaceText(TextReplacementConfig.builder().match(LEFTOVER_TOKEN_CHARS)
                .replacement((match, builder) -> builder.content("")).build());
        component = component.replaceText(TextReplacementConfig.builder().match(GUILD_NAVIGATION)
                .replacement((match, builder) -> builder.content(guildNavigationName(match.group(1)))).build());
        component = component.replaceText(TextReplacementConfig.builder().match(MASKED_LINK)
                .replacement((match, builder) -> link(match.group(1), match.group(2))).build());
        component = component.replaceText(TextReplacementConfig.builder().match(CHANNEL_LINK)
                .replacement((match, builder) -> {
                    String label = channelLabelById.apply(match.group(1));
                    return label != null ? link(label + (match.group(2) != null ? MESSAGE_SUFFIX : ""), match.group()) : builder;
                }).build());
        component = component.replaceText(TextReplacementConfig.builder().match(URL)
                .replacement((match, builder) -> builder.clickEvent(ClickEvent.openUrl(withScheme(match.group())))).build());
        return showLinkTargets(component);
    }

    private static Component link(String text, String url) {
        return Component.text(text)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url));
    }

    private static Component showLinkTargets(Component component) {
        ClickEvent click = component.clickEvent();
        if (click != null && click.action() == ClickEvent.Action.OPEN_URL && component.hoverEvent() == null
                && !(component instanceof TextComponent && ((TextComponent) component).content().equals(click.value()))) {
            component = component.hoverEvent(HoverEvent.showText(Component.text(click.value())));
        }
        return component.children(component.children().stream().map(DiscordFormatUtil::showLinkTargets).collect(Collectors.toList()));
    }

    private static String withScheme(String url) {
        return url.startsWith("http://") || url.startsWith("https://") ? url : "https://" + url;
    }

    private static String replace(Pattern pattern, String input, Function<Matcher, String> replacer) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacer.apply(matcher)));
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String guildNavigationName(String type) {
        switch (type) {
            case "customize": return "📝Channels & Roles";
            case "browse": return "📝Browse Channels";
            case "guide": return "🪧Server Guide";
            default: return "🔗Linked Roles";
        }
    }

    private static String formatTimestamp(Matcher matcher, ZoneId zone, Instant now) {
        char style = matcher.group(2) != null ? matcher.group(2).charAt(0) : 'f';
        try {
            Instant instant = Instant.ofEpochSecond(Long.parseLong(matcher.group(1)));
            if (style == 'R') return relative(instant, now);

            DateTimeFormatter formatter;
            switch (style) {
                case 't': formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT); break;
                case 'T': formatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM); break;
                case 'd': formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT); break;
                case 'D': formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG); break;
                case 'F': formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT); break;
                case 's': formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT); break;
                case 'S': formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.MEDIUM); break;
                default: formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT); break;
            }
            return ZonedDateTime.ofInstant(instant, zone).format(formatter).replace(' ', ' ');
        } catch (NumberFormatException | DateTimeException e) {
            return matcher.group();
        }
    }

    private static String relative(Instant instant, Instant now) {
        long seconds = instant.getEpochSecond() - now.getEpochSecond();
        boolean future = seconds > 0;
        long abs = Math.abs(seconds);

        String[] units = {"second", "minute", "hour", "day", "month", "year"};
        long[] sizes = {1, 60, 3600, 86400, 2592000, 31536000};
        int unit = 0;
        while (unit + 1 < sizes.length && abs >= sizes[unit + 1]) unit++;
        long amount = abs / sizes[unit];

        String text = amount + " " + units[unit] + (amount == 1 ? "" : "s");
        return future ? "in " + text : text + " ago";
    }

}
