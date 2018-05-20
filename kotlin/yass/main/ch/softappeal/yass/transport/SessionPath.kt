@file:JvmName("Kt")
@file:JvmMultifileClass

package ch.softappeal.yass.transport

import ch.softappeal.yass.remote.session.Packet
import ch.softappeal.yass.remote.session.Session
import ch.softappeal.yass.serialize.Reader
import ch.softappeal.yass.serialize.Serializer
import ch.softappeal.yass.serialize.Writer

typealias SessionFactory = () -> Session

class SessionTransport(private val packetSerializer: Serializer, private val sessionFactory: SessionFactory) {
    fun read(reader: Reader) = packetSerializer.read(reader) as Packet
    fun write(writer: Writer, packet: Packet) = packetSerializer.write(writer, packet)
    fun session() = sessionFactory()
}

class InitiatorSetup(internal val transport: SessionTransport, private val pathSerializer: Serializer, private val path: Any) {
    constructor(packetSerializer: Serializer, sessionFactory: SessionFactory) :
        this(SessionTransport(packetSerializer, sessionFactory), IntPathSerializer, IntPathSerializer_DEFAULT_PATH)

    fun writePath(writer: Writer) = pathSerializer.write(writer, path)
}

class AcceptorSetup(private val pathSerializer: Serializer, private val pathMappings: Map<out Any, SessionTransport>) {
    constructor(packetSerializer: Serializer, sessionFactory: SessionFactory)
        : this(IntPathSerializer, mapOf(IntPathSerializer_DEFAULT_PATH to SessionTransport(packetSerializer, sessionFactory)))

    fun resolve(reader: Reader): SessionTransport = pathMappings[pathSerializer.read(reader)]!!
}
