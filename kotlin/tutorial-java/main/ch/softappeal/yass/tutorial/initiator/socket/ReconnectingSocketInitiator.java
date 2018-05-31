package ch.softappeal.yass.tutorial.initiator.socket;

import ch.softappeal.yass.transport.InitiatorSetup;
import ch.softappeal.yass.tutorial.contract.Config;
import ch.softappeal.yass.tutorial.initiator.InitiatorReconnector;
import ch.softappeal.yass.tutorial.initiator.InitiatorSession;
import ch.softappeal.yass.tutorial.shared.socket.SocketSetup;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static ch.softappeal.yass.Kt.getStdErr;
import static ch.softappeal.yass.Kt.namedThreadFactory;
import static ch.softappeal.yass.transport.socket.Kt.getSyncSocketConnectionFactory;
import static ch.softappeal.yass.transport.socket.Kt.socketConnector;
import static ch.softappeal.yass.transport.socket.Kt.socketInitiate;

public final class ReconnectingSocketInitiator {

    public static void main(final String... args) throws InterruptedException {
        final Executor executor = Executors.newCachedThreadPool(namedThreadFactory("executor", getStdErr()));
        final InitiatorReconnector reconnector = new InitiatorReconnector();
        reconnector.start(
            executor,
            10,
            () -> new InitiatorSession(executor),
            0L,
            sessionFactory -> {
                socketInitiate(
                    new InitiatorSetup(Config.PACKET_SERIALIZER, sessionFactory),
                    executor,
                    getSyncSocketConnectionFactory(),
                    socketConnector(SocketSetup.ADDRESS)
                );
                return null;
            }
        );
        System.out.println("started");
        while (true) {
            TimeUnit.SECONDS.sleep(1L);
            if (reconnector.isConnected()) {
                try {
                    System.out.println(reconnector.echoService.echo("knock"));
                } catch (final Exception e) {
                    System.out.println("race condition: " + e);
                }
            } else {
                System.out.println("not connected");
            }
        }
    }

}
