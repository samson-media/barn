package com.samsonmedia.barn.cli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import picocli.CommandLine.IVersionProvider;

/**
 * Provides version information from the version.properties resource file.
 *
 * <p>The version is injected at build time via Maven resource filtering.
 */
public final class VersionProvider implements IVersionProvider {

    @Override
    public String[] getVersion() {
        String version = "unknown";
        try (InputStream is = getClass().getResourceAsStream("/version.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                version = props.getProperty("version", "unknown");
            }
        } catch (IOException e) {
            // Fall back to unknown
        }
        return new String[]{"barn " + version};
    }
}
