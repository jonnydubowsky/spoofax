package org.metaborg.spoofax.generator.project;

import org.apache.commons.vfs2.FileObject;
import org.metaborg.core.language.LanguageContributionIdentifier;
import org.metaborg.core.language.LanguageIdentifier;
import org.metaborg.core.language.LanguageVersion;
import org.metaborg.core.project.ProjectException;
import org.metaborg.spoofax.core.SpoofaxConstants;
import org.metaborg.spoofax.core.project.settings.Format;
import org.metaborg.spoofax.core.project.settings.ISpoofaxLanguageSpecConfig;

import javax.annotation.Nullable;

public class NewGeneratorProjectSettings {
    private final FileObject location;
    private final ISpoofaxLanguageSpecConfig config;


    public NewGeneratorProjectSettings(FileObject location, ISpoofaxLanguageSpecConfig config) throws ProjectException {
//        final IProjectSettings metaborgSettings = settings.settings();
        if(!config.identifier().valid()) {
            throw new ProjectException("Invalid language identifier: " + config.identifier());
        }
        if(!LanguageIdentifier.validId(config.name())) {
            throw new ProjectException("Invalid name: " + name());
        }
        for(LanguageIdentifier compileIdentifier : config.compileDependencies()) {
            if(!compileIdentifier.valid()) {
                throw new ProjectException("Invalid compile dependency identifier: " + compileIdentifier);
            }
        }
        for(LanguageIdentifier runtimeIdentifier : config.runtimeDependencies()) {
            if(!runtimeIdentifier.valid()) {
                throw new ProjectException("Invalid runtime dependency identifier: " + runtimeIdentifier);
            }
        }
        for(LanguageContributionIdentifier contributionIdentifier : config.languageContributions()) {
            if(!contributionIdentifier.identifier.valid()) {
                throw new ProjectException("Invalid language contribution identifier: "
                    + contributionIdentifier.identifier);
            }
            if(!LanguageIdentifier.validId(contributionIdentifier.name)) {
                throw new ProjectException("Invalid language contribution name: " + config.name());
            }
        }
        this.location = location;
        this.config = config;
    }


    private @Nullable String metaborgVersion;

    public void setMetaborgVersion(String metaborgVersion) throws ProjectException {
        if(metaborgVersion != null && !LanguageVersion.valid(metaborgVersion)) {
            throw new ProjectException("Invalid metaborg version: " + metaborgVersion);
        }
        this.metaborgVersion = metaborgVersion;
    }

    public String metaborgVersion() {
        return metaborgVersion != null && !metaborgVersion.isEmpty() ? metaborgVersion
            : SpoofaxConstants.METABORG_VERSION;
    }

    public String eclipseMetaborgVersion() {
        return metaborgVersion().replace("-SNAPSHOT", ".qualifier");
    }


    public String groupId() {
        return this.config.identifier().groupId;
    }

    public boolean generateGroupId() {
        return !groupId().equals(SpoofaxConstants.METABORG_GROUP_ID);
    }

    public String id() {
        return this.config.identifier().id;
    }

    public String version() {
        return this.config.identifier().version.toString();
    }

    public boolean generateVersion() {
        return !version().equals(metaborgVersion());
    }

    public String eclipseVersion() {
        return version().replace("-SNAPSHOT", ".qualifier");
    }

    public String name() {
        return this.config.name();
    }

    public FileObject location() {
        return this.location();
    }


    public Format format() {
        final Format format = this.config.format();
        return format != null ? format : Format.ctree;
    }


    public String strategoName() {
        return this.config.strategoName();
    }

    public String javaName() {
        return this.config.javaName();
    }

    public String packageName() {
        return this.config.packageName();
    }

    public String packagePath() {
        return this.config.packagePath();
    }
}
