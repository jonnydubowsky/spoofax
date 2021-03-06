package org.metaborg.spoofax.meta.core.pluto.build;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.metaborg.sdf2table.io.ParseTableGenerator;
import org.metaborg.spoofax.meta.core.pluto.SpoofaxBuilder;
import org.metaborg.spoofax.meta.core.pluto.SpoofaxBuilderFactory;
import org.metaborg.spoofax.meta.core.pluto.SpoofaxBuilderFactoryFactory;
import org.metaborg.spoofax.meta.core.pluto.SpoofaxContext;
import org.metaborg.spoofax.meta.core.pluto.SpoofaxInput;

import build.pluto.BuildUnit.State;
import build.pluto.builder.BuildRequest;
import build.pluto.dependency.Origin;
import build.pluto.output.OutputPersisted;

public class Sdf2Table extends SpoofaxBuilder<Sdf2Table.Input, OutputPersisted<File>> {
    public static class Input extends SpoofaxInput {
        private static final long serialVersionUID = -2379365089609792204L;

        public final File inputFile;
        public final File outputFile;
        public final File outputContextGrammarFile;
        public final File outputNormGrammarFile;
        public final List<String> paths;
        public final boolean dynamic;
        public final boolean dataDependent;
        public final boolean layoutSensitive;


        public Input(SpoofaxContext context, File inputFile, File outputFile, File outputNormGrammarFile,
            File outputContextGrammarFile, List<String> paths, boolean dynamic, boolean dataDependent,
            boolean layoutSensitive) {
            super(context);
            this.inputFile = inputFile;
            this.outputFile = outputFile;
            this.outputNormGrammarFile = outputNormGrammarFile;
            this.outputContextGrammarFile = outputContextGrammarFile;
            this.paths = paths;
            this.dynamic = dynamic;
            this.dataDependent = dataDependent;
            this.layoutSensitive = layoutSensitive;
        }
    }

    public static SpoofaxBuilderFactory<Input, OutputPersisted<File>, Sdf2Table> factory =
        SpoofaxBuilderFactoryFactory.of(Sdf2Table.class, Input.class);

    public Sdf2Table(Input input) {
        super(input);
    }

    public static
        BuildRequest<Input, OutputPersisted<File>, Sdf2Table, SpoofaxBuilderFactory<Input, OutputPersisted<File>, Sdf2Table>>
        request(Input input) {
        return new BuildRequest<>(factory, input);
    }

    public static Origin origin(Input input) {
        return Origin.from(request(input));
    }


    @Override protected String description(Input input) {
        return "Compile grammar to parse table using the Java implementation";
    }

    @Override public File persistentPath(Input input) {
        String fileName = input.inputFile.getName();
        return context.depPath("sdf2table-java." + fileName + ".dep");
    }

    @Override public OutputPersisted<File> build(Input input) throws IOException {
        require(input.inputFile);
        boolean status = true;

        try {
            ParseTableGenerator pt_gen = new ParseTableGenerator(input.inputFile, input.outputFile,
                input.outputNormGrammarFile, input.outputContextGrammarFile, input.paths);
            
            boolean solveDeepConflicts = true;
            // TODO add option to generate the contextual grammar in the Yaml file
            if(input.layoutSensitive) {
                solveDeepConflicts = false;
            }
            
            pt_gen.outputTable(input.dynamic, input.dataDependent, solveDeepConflicts);
            for(File required : pt_gen.getParseTable().normalizedGrammar().getFilesRead()) {
                require(required);
            }
        } catch(Exception e) {
            System.out.println("Failed to generate parse table");
            e.printStackTrace();
            status = false;
        }
        provide(input.outputFile);

        setState(State.finished(status));
        return OutputPersisted.of(input.outputFile);
    }
}
