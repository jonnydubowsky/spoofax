package org.metaborg.meta.core.config;

import java.io.IOException;

import javax.annotation.Nullable;

import org.apache.commons.vfs2.FileObject;
import org.metaborg.core.project.settings.ILegacyProjectSettings;
import org.metaborg.core.project.settings.ILegacyProjectSettingsService;

import com.google.inject.Inject;

/**
 * This class is only used for the configuration system migration.
 */
@Deprecated
@SuppressWarnings("deprecation")
public class LegacyLanguageSpecConfigService implements ILanguageSpecConfigService {
    private final LanguageSpecConfigService languageSpecConfigService;
    private final ILegacyProjectSettingsService settingsService;


    @Inject public LegacyLanguageSpecConfigService(LanguageSpecConfigService languageSpecConfigService,
        ILegacyProjectSettingsService settingsService) {
        this.languageSpecConfigService = languageSpecConfigService;
        this.settingsService = settingsService;
    }

    @Override public boolean available(FileObject rootFolder) throws IOException {
        return languageSpecConfigService.available(rootFolder) || settingsService.get(rootFolder) != null;
    }

    @Override public @Nullable ILanguageSpecConfig get(FileObject rootFolder) throws IOException {
        final ILanguageSpecConfig config = this.languageSpecConfigService.get(rootFolder);
        if(config == null) {
            final ILegacyProjectSettings settings = settingsService.get(rootFolder);
            if(settings != null) {
                return new LegacyLanguageSpecConfig(settings);
            }
        }
        return config;
    }
}
