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

import org.apache.commons.io.IOUtils;
import org.bukkit.plugin.PluginDescriptionFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;

public final class Jda4Compat {

    private static volatile boolean closed = false;

    private Jda4Compat() {}

    public static void close() {
        closed = true;
    }

    public static void install(Logger logger) {
        try {
            Class<?> entrypoint = Class.forName("io.papermc.paper.plugin.entrypoint.Entrypoint");
            Class<?> handlerClass = Class.forName("io.papermc.paper.plugin.entrypoint.LaunchEntryPointHandler");
            Object handler = handlerClass.getField("INSTANCE").get(null);
            Object storage = handlerClass.getMethod("get", entrypoint).invoke(handler, entrypoint.getField("PLUGIN").get(null));
            Iterable<?> providers = (Iterable<?>) storage.getClass().getMethod("getRegisteredProviders").invoke(storage);

            for (Object provider : providers) {
                if (!provider.getClass().getSimpleName().equals("SpigotPluginProvider")) continue;
                PluginDescriptionFile description = (PluginDescriptionFile) provider.getClass().getMethod("getMeta").invoke(provider);
                if (!description.getDepend().contains("DiscordSRV") && !description.getSoftDepend().contains("DiscordSRV")) continue;

                Field jarField = provider.getClass().getDeclaredField("jarFile");
                jarField.setAccessible(true);
                JarFile original = (JarFile) jarField.get(provider);
                if (original instanceof TransformingJarFile) continue;

                jarField.set(provider, new TransformingJarFile(new File(original.getName()), description.getName(), logger));
                original.close();
                logger.info("Enabled JDA 4 compatibility for " + description.getName());
            }
        } catch (ReflectiveOperationException | IOException | RuntimeException e) {
            logger.warning("Couldn't enable JDA 4 compatibility for dependent plugins: " + e);
        }
    }

    static final class TransformingJarFile extends JarFile {

        private final String pluginName;
        private final Logger logger;
        private final Set<String> warned = ConcurrentHashMap.newKeySet();

        TransformingJarFile(File file, String pluginName, Logger logger) throws IOException {
            super(file);
            this.pluginName = pluginName;
            this.logger = logger;
        }

        @Override
        public synchronized InputStream getInputStream(ZipEntry entry) throws IOException {
            InputStream in = super.getInputStream(entry);
            if (closed || !entry.getName().endsWith(".class")) return in;

            byte[] bytes;
            try (InputStream stream = in) {
                bytes = IOUtils.toByteArray(stream);
            }
            try {
                bytes = Jda4Transformer.transform(bytes, warning -> {
                    if (warned.add(warning)) logger.warning("[JDA 4 compatibility] " + pluginName + " uses an API with no JDA 6 equivalent: " + warning);
                });
            } catch (RuntimeException | LinkageError e) {
                logger.warning("[JDA 4 compatibility] Failed to convert " + entry.getName() + " of " + pluginName + ": " + e);
            }
            return new ByteArrayInputStream(bytes);
        }
    }

}
