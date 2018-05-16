package ch.softappeal.yass.tutorial.generate;

import ch.softappeal.yass.generate.ExternalDesc;
import ch.softappeal.yass.generate.TypeScriptGenerator;
import ch.softappeal.yass.tutorial.contract.Config;

import java.util.HashMap;
import java.util.Map;

public final class GenerateTypeScript {

    public static void main(final String... args) throws Exception {
        final Map<Class<?>, ExternalDesc> externalTypes = new HashMap<>();
        externalTypes.put(Integer.class, new ExternalDesc("Integer", "IntegerHandler")); // shows how to use a contract external base type
        new TypeScriptGenerator(
            Config.class.getPackage().getName(),
            Config.CONTRACT_SERIALIZER,
            Config.INITIATOR,
            Config.ACCEPTOR,
            "../../ts/tutorial/contract-include.txt",
            externalTypes,
            "build/generated/ts/contract.ts"
            // , "protected readonly __TYPE_KEY__!: never;"
        );
    }

}
