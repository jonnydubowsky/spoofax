package org.metaborg.spoofax.core.analysis.constraint;

import static mb.nabl2.terms.build.TermBuild.B;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.metaborg.core.MetaborgException;
import org.metaborg.core.analysis.AnalysisException;
import org.metaborg.core.messages.IMessage;
import org.metaborg.core.messages.MessageFactory;
import org.metaborg.core.resource.IResourceService;
import org.metaborg.spoofax.core.analysis.AnalysisCommon;
import org.metaborg.spoofax.core.analysis.ISpoofaxAnalyzeResults;
import org.metaborg.spoofax.core.analysis.ISpoofaxAnalyzer;
import org.metaborg.spoofax.core.analysis.SpoofaxAnalyzeResults;
import org.metaborg.spoofax.core.context.scopegraph.ISingleFileScopeGraphContext;
import org.metaborg.spoofax.core.context.scopegraph.ISingleFileScopeGraphUnit;
import org.metaborg.spoofax.core.stratego.IStrategoCommon;
import org.metaborg.spoofax.core.stratego.IStrategoRuntimeService;
import org.metaborg.spoofax.core.terms.ITermFactoryService;
import org.metaborg.spoofax.core.tracing.ISpoofaxTracingService;
import org.metaborg.spoofax.core.unit.AnalyzeContrib;
import org.metaborg.spoofax.core.unit.ISpoofaxAnalyzeUnit;
import org.metaborg.spoofax.core.unit.ISpoofaxParseUnit;
import org.metaborg.spoofax.core.unit.ISpoofaxUnitService;
import org.metaborg.util.functions.Function1;
import org.metaborg.util.iterators.Iterables2;
import org.metaborg.util.log.ILogger;
import org.metaborg.util.log.LoggerUtils;
import org.metaborg.util.task.ICancel;
import org.metaborg.util.task.IProgress;
import org.metaborg.util.time.AggregateTimer;
import org.metaborg.util.time.Timer;
import org.spoofax.interpreter.terms.IStrategoTerm;
import org.strategoxt.HybridInterpreter;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

import mb.flowspec.runtime.solver.FixedPoint;
import mb.nabl2.config.NaBL2DebugConfig;
import mb.nabl2.constraints.IConstraint;
import mb.nabl2.solver.ISolution;
import mb.nabl2.solver.SolverException;
import mb.nabl2.solver.messages.IMessages;
import mb.nabl2.solver.messages.Messages;
import mb.nabl2.solver.solvers.BaseSolver.GraphSolution;
import mb.nabl2.solver.solvers.ImmutableBaseSolution;
import mb.nabl2.solver.solvers.SingleFileSolver;
import mb.nabl2.spoofax.analysis.Actions;
import mb.nabl2.spoofax.analysis.CustomSolution;
import mb.nabl2.spoofax.analysis.FinalResult;
import mb.nabl2.spoofax.analysis.InitialResult;
import mb.nabl2.spoofax.analysis.UnitResult;
import mb.nabl2.terms.ITerm;
import mb.nabl2.terms.unification.PersistentUnifier;

public class ConstraintSingleFileAnalyzer extends AbstractConstraintAnalyzer<ISingleFileScopeGraphContext>
        implements ISpoofaxAnalyzer {

    public static final ILogger logger = LoggerUtils.logger(ConstraintSingleFileAnalyzer.class);

    public static final String name = "constraint-singlefile";

    private final ISpoofaxUnitService unitService;

    @Inject public ConstraintSingleFileAnalyzer(final AnalysisCommon analysisCommon,
            final ISpoofaxUnitService unitService, final IResourceService resourceService,
            final IStrategoRuntimeService runtimeService, final IStrategoCommon strategoCommon,
            final ITermFactoryService termFactoryService, final ISpoofaxTracingService tracingService) {
        super(analysisCommon, resourceService, runtimeService, strategoCommon, termFactoryService, tracingService);
        this.unitService = unitService;
    }

    @Override protected ISpoofaxAnalyzeResults analyzeAll(Map<String, ISpoofaxParseUnit> changed, Set<String> removed,
            ISingleFileScopeGraphContext context, HybridInterpreter runtime, String strategy, IProgress progress,
            ICancel cancel) throws AnalysisException {
        final NaBL2DebugConfig debugConfig = context.config().debug();

        for(String input : removed) {
            context.removeUnit(input);
        }

        final int n = changed.size();
        progress.setWorkRemaining(n + 1);

        if(debugConfig.analysis() || debugConfig.files()) {
            logger.info("Analyzing {} files in {}.", n, context.location());
        }
        final Collection<ISpoofaxAnalyzeUnit> results = Lists.newArrayList();
        try {
            for(Map.Entry<String, ISpoofaxParseUnit> input : changed.entrySet()) {
                final String source = input.getKey();
                final ISpoofaxParseUnit parseUnit = input.getValue();
                final ITerm ast = strategoTerms.fromStratego(parseUnit.ast());

                final Timer totalTimer = new Timer(true);
                final AggregateTimer collectionTimer = new AggregateTimer();
                final AggregateTimer solverTimer = new AggregateTimer();
                final AggregateTimer finalizeTimer = new AggregateTimer();

                if(debugConfig.files()) {
                    logger.info("Analyzing {}.", source);
                }
                final ISingleFileScopeGraphUnit unit = context.unit(source);
                unit.clear();

                try {
                    // initial
                    InitialResult initialResult;
                    final Optional<ITerm> customInitial;
                    try {
                        collectionTimer.start();
                        if(debugConfig.collection()) {
                            logger.info("Collecting initial constraints of {}.", source);
                        }
                        ITerm initialResultTerm =
                                doAction(strategy, Actions.analyzeInitial(source, ast), context, runtime)
                                        .orElseThrow(() -> new AnalysisException(context, "No initial result."));
                        initialResult = InitialResult.matcher().match(initialResultTerm)
                                .orElseThrow(() -> new MetaborgException("Invalid initial results."));
                        customInitial = doCustomAction(strategy, Actions.customInitial(source, ast), context, runtime);
                        initialResult = initialResult.withCustomResult(customInitial);
                        if(debugConfig.collection()) {
                            logger.info("Collected {} initial constraints of {}.",
                                    initialResult.getConstraints().size(), source);
                        }
                    } finally {
                        collectionTimer.stop();
                    }

                    // unit
                    UnitResult unitResult;
                    final Optional<ITerm> customUnit;
                    try {
                        collectionTimer.start();
                        if(debugConfig.collection()) {
                            logger.info("Collecting constraints of {}.", source);
                        }
                        final ITerm unitResultTerm =
                                doAction(strategy, Actions.analyzeUnit(source, ast, initialResult.getArgs()), context,
                                        runtime).orElseThrow(() -> new AnalysisException(context, "No unit result."));
                        unitResult = UnitResult.matcher().match(unitResultTerm)
                                .orElseThrow(() -> new MetaborgException("Invalid unit results."));
                        final ITerm desugaredAST = unitResult.getAST();

                        customUnit = doCustomAction(strategy,
                                Actions.customUnit(source, desugaredAST, customInitial.orElse(B.EMPTY_TUPLE)), context,
                                runtime);
                        unitResult = unitResult.withCustomResult(customUnit);
                        unit.setUnitResult(unitResult);
                        if(debugConfig.collection()) {
                            logger.info("Collected {} constraints of {}.", unitResult.getConstraints().size(), source);
                        }
                    } finally {
                        collectionTimer.stop();
                    }

                    // solve
                    ISolution solution;
                    try {
                        solverTimer.start();
                        Set<IConstraint> constraints =
                                Sets.union(initialResult.getConstraints(), unitResult.getConstraints());
                        if(debugConfig.resolution()) {
                            logger.info("Solving {} constraints of {}.", constraints.size(), source);
                        }
                        Function1<String, String> fresh = base -> context.unit(source).fresh().fresh(base);
                        final IProgress subprogress = progress.subProgress(1);
                        final SingleFileSolver solver =
                                new SingleFileSolver(context.config().debug(), callExternal(runtime));
                        GraphSolution preSolution =
                                solver.solveGraph(ImmutableBaseSolution.of(initialResult.getConfig(), constraints,
                                        PersistentUnifier.Immutable.of()), fresh, cancel, subprogress);
                        preSolution = solver.reportUnsolvedGraphConstraints(preSolution);
                        solution = solver.solve(preSolution, fresh, cancel, subprogress);
                        solution = solver.reportUnsolvedConstraints(solution);
                        if(!solution.flowSpecSolution().controlFlowGraph().isEmpty()) {
                            logger.debug("CFG is not empty: calling FlowSpec dataflow solver");
                            solution = new FixedPoint().entryPoint(solution,
                                    getFlowSpecTransferFunctions(context.language()));
                        }
                        unit.setSolution(solution);
                        if(debugConfig.resolution()) {
                            logger.info("Solved constraints of {}.", source);
                        }
                    } finally {
                        solverTimer.stop();
                    }

                    // final
                    FinalResult finalResult;
                    final Optional<ITerm> customFinal;
                    try {
                        finalizeTimer.start();
                        if(debugConfig.files()) {
                            logger.info("Finalizing analysis of {}.", source);
                        }
                        ITerm finalResultTerm = doAction(strategy, Actions.analyzeFinal(source), context, runtime)
                                .orElseThrow(() -> new AnalysisException(context, "No final result."));
                        finalResult = FinalResult.matcher().match(finalResultTerm)
                                .orElseThrow(() -> new MetaborgException("Invalid final results."));
                        customFinal = doCustomAction(strategy,
                                Actions.customFinal(source, customInitial.orElse(B.EMPTY_TUPLE), customUnit
                                        .map(cu -> Collections.singletonList(cu)).orElse(Collections.emptyList())),
                                context, runtime);
                        finalResult = finalResult.withCustomResult(customFinal);
                        unit.setFinalResult(finalResult);
                        if(debugConfig.files()) {
                            logger.info("Finalized analysis of {}.", source);
                        }
                    } finally {
                        finalizeTimer.stop();
                    }
                    final IStrategoTerm analyzedAST = strategoTerms.toStratego(unitResult.getAST());

                    Optional<CustomSolution> customSolution = customFinal.flatMap(CustomSolution.matcher()::match);
                    customSolution.ifPresent(cs -> unit.setCustomSolution(cs));

                    // errors
                    final boolean success;
                    {
                        if(debugConfig.files()) {
                            logger.info("Processing messages of {}.", source);
                        }
                        Messages.Transient messageBuilder = Messages.Transient.of();
                        messageBuilder.addAll(Messages.unsolvedErrors(solution.constraints()));
                        messageBuilder.addAll(solution.messages().getAll());
                        customSolution.map(CustomSolution::getMessages).map(IMessages::getAll)
                                .ifPresent(messageBuilder::addAll);
                        IMessages messages = messageBuilder.freeze();

                        success = messages.getErrors().isEmpty();

                        Iterable<IMessage> fileMessages =
                                Iterables.concat(analysisCommon.ambiguityMessages(parseUnit.source(), parseUnit.ast()),
                                        messages(messages.getAll(), solution.unifier(), context, context.location()));

                        // result
                        results.add(unitService.analyzeUnit(parseUnit,
                                new AnalyzeContrib(true, success, true, analyzedAST, fileMessages, -1), context));

                        if(debugConfig.analysis() || debugConfig.files()) {
                            logger.info("Analyzed {}: {} errors, {} warnings, {} notes.", source,
                                    messages.getErrors().size(), messages.getWarnings().size(),
                                    messages.getNotes().size());
                        }
                    }
                } catch(MetaborgException | SolverException e) {
                    logger.warn("Analysis of " + source + " failed.", e);
                    Iterable<IMessage> messages = Iterables2.singleton(
                            MessageFactory.newAnalysisErrorAtTop(parseUnit.source(), "File analysis failed.", e));
                    results.add(unitService.analyzeUnit(parseUnit,
                            new AnalyzeContrib(false, false, true, parseUnit.ast(), messages, -1), context));
                } finally {
                    totalTimer.stop();
                }

                final ConstraintDebugData debugData = new ConstraintDebugData(totalTimer.stop(),
                        collectionTimer.total(), solverTimer.total(), finalizeTimer.total());
                if(debugConfig.timing()) {
                    logger.info("{}", debugData);
                }
            }
        } catch(InterruptedException e) {
            logger.debug("Analysis was interrupted.");
        }

        if(debugConfig.analysis()) {
            logger.info("Analyzed {} files.", n);
        }
        return new SpoofaxAnalyzeResults(results, Collections.emptyList(), context);
    }

}
