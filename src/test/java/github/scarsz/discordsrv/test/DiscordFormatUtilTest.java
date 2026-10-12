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

package github.scarsz.discordsrv.test;

import com.vdurmont.emoji.EmojiParser;
import github.scarsz.discordsrv.util.DiscordFormatUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class DiscordFormatUtilTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);

    private static String convert(String message) {
        return DiscordFormatUtil.convert(message, ZoneOffset.UTC, NOW);
    }

    private static Component linkify(String message) {
        return DiscordFormatUtil.linkify(Component.text(message), id -> id.equals("1554731651068731434") ? "🧵thread-name" : null);
    }

    /** text of each part, with the URL it opens (if any) */
    private static String links(Component component) {
        StringBuilder out = new StringBuilder();
        String content = ((TextComponent) component).content();
        if (!content.isEmpty()) {
            out.append('[').append(content);
            if (component.clickEvent() != null) out.append(" -> ").append(component.clickEvent().value());
            out.append(']');
        }
        for (Component child : component.children()) out.append(links(child));
        return out.toString();
    }

    @Test
    public void linksStayLinksUntilRendered() {
        assertEquals("[see ][docs -> https://example.com/a?b=c]", links(linkify(convert("see [docs](https://example.com/a?b=c)"))));
        assertEquals("[see ][docs -> https://example.com][!]", links(linkify(convert("see [docs](<https://example.com> \"title\")!"))));
        assertEquals("see docs (https://example.com) ok", DiscordFormatUtil.untokenize(convert("see [docs](https://example.com) ok")));
        assertEquals("https://example.com", convert("[https://example.com](https://example.com)"));
        assertEquals("no embed https://example.com ok", convert("no embed <https://example.com> ok"));
        assertEquals("https://discord.com/channels/1/2", convert("https://discord.com/channels/1/2"));
    }

    @Test
    public void linksAreClickable() {
        assertEquals("[see ][docs -> https://example.com/a?b=c][!]", links(linkify("see [docs](https://example.com/a?b=c)!")));
        assertEquals("[🧵thread-name -> https://discord.com/channels/762627112867725403/1554731651068731434]",
                links(linkify("https://discord.com/channels/762627112867725403/1554731651068731434")));
        assertEquals("[🧵thread-name › 💬 -> https://ptb.discord.com/channels/7/1554731651068731434/9]",
                links(linkify("https://ptb.discord.com/channels/7/1554731651068731434/9")));
        assertEquals("[https://discord.com/channels/7/8 -> https://discord.com/channels/7/8]", links(linkify("https://discord.com/channels/7/8")));
        assertEquals("[go ][example.com/x -> https://example.com/x]", links(linkify("go example.com/x")));
    }

    @Test
    public void survivesMinecraftPipeline() {
        String escaped = github.scarsz.discordsrv.util.MessageUtil.escapeMiniTokens("<id:guide> [docs](https://example.com) <b>x</b>");
        Component component = DiscordFormatUtil.linkify(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(escaped), id -> null);
        assertEquals("[🪧Server Guide][ ][docs -> https://example.com][ <b>x</b>]", links(component));
    }

    @Test
    public void maskedLinkFromDiscordToMinecraft() {
        String message = DiscordFormatUtil.convert("[測試超連結](<https://www.brilliantw.net>)", ZoneOffset.UTC, NOW);
        message = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(github.scarsz.discordsrv.util.MessageUtil.LIMITED_MINECRAFT_SERIALIZER.serialize(message));
        message = github.scarsz.discordsrv.util.MessageUtil.escapeMiniTokens(message);
        Component component = DiscordFormatUtil.linkify(github.scarsz.discordsrv.util.MessageUtil.toComponent(message));

        Component link = component instanceof TextComponent && ((TextComponent) component).content().isEmpty() && component.children().size() == 1
                ? component.children().get(0) : component;
        assertEquals("測試超連結", ((TextComponent) link).content());
        assertEquals("https://www.brilliantw.net", link.clickEvent().value());
        assertEquals(net.kyori.adventure.text.event.ClickEvent.Action.OPEN_URL, link.clickEvent().action());
        assertEquals(Component.text("https://www.brilliantw.net"), link.hoverEvent().value());
    }

    @Test
    public void maskedLinkWithFullReserializerAndColorPermission() {
        String message = DiscordFormatUtil.convert("看 [測試超連結](<https://www.brilliantw.net>) **粗體**", ZoneOffset.UTC, NOW);
        message = github.scarsz.discordsrv.util.MessageUtil.toMiniMessage(github.scarsz.discordsrv.util.MessageUtil.MINECRAFT_SERIALIZER.serialize(message));
        Component component = DiscordFormatUtil.linkify(github.scarsz.discordsrv.util.MessageUtil.toComponent(message));

        String rendered = links(component);
        assertEquals(true, rendered.contains("[測試超連結 -> https://www.brilliantw.net]"), rendered);
        assertEquals(false, rendered.contains("]("), rendered);
    }

    @Test
    public void renderSyntaxForGameChat() {
        String text = "看 [測試超連結](<https://www.brilliantw.net>) <id:guide> </pog signin:1355199760960851978> <https://a.b> 完";
        java.util.List<String> found = new java.util.ArrayList<>();
        java.util.regex.Matcher matcher = DiscordFormatUtil.MINECRAFT_CHAT_SYNTAX.matcher(text);
        while (matcher.find()) found.add(String.join("|", java.util.Arrays.asList(DiscordFormatUtil.renderSyntax(matcher.group())).toString()));
        assertEquals(java.util.Arrays.asList("[測試超連結, https://www.brilliantw.net]", "[🪧Server Guide, null]", "[/pog signin, null]", "[https://a.b, https://a.b]"), found);
    }

    @Test
    public void gameToDiscordTextIsUntouched() {
        String text = "<id:linked-roles> https://discord.com/channels/7/1554731651068731434 [a](https://example.com)";
        Component component = github.scarsz.discordsrv.util.MessageUtil.toComponent(text);
        assertEquals(text, net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component));
    }

    @Test
    public void mentionsAndNavigation() {
        assertEquals("use /link code", convert("use </link code:123456789012345678>"));
        assertEquals("go to <id:customize>", convert("go to <id:customize>"));
        assertEquals("[go to ][📝Channels & Roles]", links(linkify("go to <id:customize>")));
        assertEquals("[📝Browse Channels]", links(linkify("<id:browse>")));
        assertEquals("[🪧Server Guide]", links(linkify("<id:guide>")));
        assertEquals("[🔗Linked Roles]", links(linkify("<id:linked-roles:1234567890>")));
        assertEquals("<@123456789012345678> <#123456789012345678>", convert("<@123456789012345678> <#123456789012345678>"));
    }

    @Test
    public void headingsAndSubtext() {
        assertEquals("Title\nbody\nsmall", convert("# Title\nbody\n-# small"));
        assertEquals("Small heading", convert("### Small heading"));
        assertEquals("#### not a heading", convert("#### not a heading"));
        assertEquals("#hashtag stays", convert("#hashtag stays"));
        assertEquals("a -# b", convert("a -# b"));
    }

    @Test
    public void timestamps() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.US);
        try {
            assertEquals("in 2 hours", convert("<t:" + (NOW.getEpochSecond() + 7200) + ":R>"));
            assertEquals("3 days ago", convert("<t:" + (NOW.getEpochSecond() - 3 * 86400) + ":R>"));
            assertEquals("1 minute ago", convert("<t:" + (NOW.getEpochSecond() - 60) + ":R>"));
            assertEquals("11/14/23", convert("<t:1700000000:d>"));
            assertEquals("November 14, 2023", convert("<t:1700000000:D>"));
            assertEquals("10:13 PM", convert("<t:1700000000:t>"));
            assertEquals("November 14, 2023, 10:13 PM", convert("<t:1700000000>"));
            assertEquals("<t:99999999999999:F>", convert("<t:99999999999999:F>"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void forwardMarkerIsNotAnEmoji() {
        assertEquals("⤷ hi", EmojiParser.parseToAliases("⤷ hi"));
        assertFalse(EmojiParser.removeAllEmojis("⤷").isEmpty());
    }

}
