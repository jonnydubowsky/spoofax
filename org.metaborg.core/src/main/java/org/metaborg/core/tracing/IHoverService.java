package org.metaborg.core.tracing;

import javax.annotation.Nullable;

import org.metaborg.core.MetaborgException;
import org.metaborg.core.analysis.AnalysisFileResult;

public interface IHoverService<P, A> {
    public abstract @Nullable Hover hover(int offset, AnalysisFileResult<P, A> result) throws MetaborgException;
}