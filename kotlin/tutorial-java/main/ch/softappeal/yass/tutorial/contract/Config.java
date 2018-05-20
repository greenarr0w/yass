package ch.softappeal.yass.tutorial.contract;

import ch.softappeal.yass.remote.ContractId;
import ch.softappeal.yass.remote.Services;
import ch.softappeal.yass.serialize.Serializer;
import ch.softappeal.yass.serialize.fast.FastSerializer;
import ch.softappeal.yass.tutorial.contract.generic.GenericEchoService;
import ch.softappeal.yass.tutorial.contract.generic.Pair;
import ch.softappeal.yass.tutorial.contract.generic.PairBoolBool;
import ch.softappeal.yass.tutorial.contract.generic.Triple;
import ch.softappeal.yass.tutorial.contract.generic.TripleWrapper;
import ch.softappeal.yass.tutorial.contract.instrument.Bond;
import ch.softappeal.yass.tutorial.contract.instrument.InstrumentService;
import ch.softappeal.yass.tutorial.contract.instrument.stock.Stock;

import java.util.Arrays;

import static ch.softappeal.yass.remote.Kt.getSimpleMethodMapperFactory;
import static ch.softappeal.yass.serialize.fast.Kt.getBTH_INTEGER;
import static ch.softappeal.yass.serialize.fast.Kt.SimpleFastSerializer;
import static ch.softappeal.yass.transport.Kt.MessageSerializer;
import static ch.softappeal.yass.transport.Kt.PacketSerializer;

public final class Config {

    public static final FastSerializer CONTRACT_SERIALIZER = SimpleFastSerializer(
        ch.softappeal.yass.generate.ts.Kt.baseTypeHandlers(
            getBTH_INTEGER(),
            Expiration.TYPE_HANDLER
        ),
        Arrays.asList(
            PriceKind.class,
            Price.class,
            Stock.class,
            Bond.class,
            SystemException.class,
            UnknownInstrumentsException.class,
            Pair.class,
            PairBoolBool.class,
            Triple.class,
            TripleWrapper.class
        )
    );

    public static final Serializer MESSAGE_SERIALIZER = MessageSerializer(CONTRACT_SERIALIZER);
    public static final Serializer PACKET_SERIALIZER = PacketSerializer(MESSAGE_SERIALIZER);

    private abstract static class Role extends Services {
        Role() {
            super(getSimpleMethodMapperFactory());
        }
    }

    public static final class Initiator extends Role { // to be implemented by initiator
        public final ContractId<PriceListener> priceListener = contractId(PriceListener.class, 0);
        public final ContractId<EchoService> echoService = contractId(EchoService.class, 1);
    }

    public static final class Acceptor extends Role { // to be implemented by acceptor
        public final ContractId<PriceEngine> priceEngine = contractId(PriceEngine.class, 0);
        public final ContractId<InstrumentService> instrumentService = contractId(InstrumentService.class, 1);
        public final ContractId<EchoService> echoService = contractId(EchoService.class, 2);
        public final ContractId<GenericEchoService> genericEchoService = contractId(GenericEchoService.class, 3);
    }

    public static final Initiator INITIATOR = new Initiator();
    public static final Acceptor ACCEPTOR = new Acceptor();

    public static final FastSerializer PY_CONTRACT_SERIALIZER = SimpleFastSerializer(
        ch.softappeal.yass.generate.py.Kt.baseTypeHandlers(
            getBTH_INTEGER(),
            Expiration.TYPE_HANDLER
        ),
        Arrays.asList(
            PriceKind.class,
            Price.class,
            Stock.class,
            Bond.class,
            SystemException.class,
            UnknownInstrumentsException.class
        ),
        Arrays.asList(
            Node.class
        )
    );

    public static final class PyAcceptor extends Role {
        public final ContractId<PriceEngine> priceEngine = contractId(PriceEngine.class, 0);
        public final ContractId<InstrumentService> instrumentService = contractId(InstrumentService.class, 1);
        public final ContractId<EchoService> echoService = contractId(EchoService.class, 2);
    }

    public static final PyAcceptor PY_ACCEPTOR = new PyAcceptor();

}
