@file:JvmName("Kt")
@file:JvmMultifileClass

package ch.softappeal.yass.generate

import ch.softappeal.yass.remote.ContractId
import ch.softappeal.yass.remote.MethodMapper
import ch.softappeal.yass.remote.Services
import ch.softappeal.yass.serialize.fast.FastSerializer
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Comparator
import java.util.SortedSet
import java.util.TreeSet
import java.util.stream.Collectors

class ServiceDesc(val name: String, val contractId: ContractId<*>)

private val ROOT_CLASSES = setOf(
    Any::class.java,
    Exception::class.java,
    RuntimeException::class.java,
    Error::class.java,
    Throwable::class.java
)

fun isRootClass(type: Class<*>) = ROOT_CLASSES.contains(type)

fun getServiceDescs(services: Services): List<ServiceDesc> {
    val serviceDescs = mutableListOf<ServiceDesc>()
    for (field in services.javaClass.fields) {
        if (!Modifier.isStatic(field.modifiers) && (field.type === ContractId::class.java))
            serviceDescs.add(ServiceDesc(field.name, field.get(services) as ContractId<*>))
    }
    return serviceDescs.sortedBy { it.contractId.id }
}

private fun getInterfaces(services: Services?): Set<Class<*>> = if (services == null) {
    setOf()
} else getServiceDescs(services).stream().map { it.contractId.contract }.collect(Collectors.toSet())

fun getMethods(type: Class<*>): List<Method> = type.methods.asList().sortedBy { it.name }

fun <E> iterate(iterable: Iterable<E>, notFirstAction: () -> Unit, alwaysAction: (element: E) -> Unit) {
    var first = true
    for (element in iterable) {
        if (!first) notFirstAction()
        first = false
        alwaysAction(element)
    }
}

abstract class Generator(
    rootPackage: String, serializer: FastSerializer, protected val initiator: Services?, protected val acceptor: Services?
) {
    private val rootPackage: String = if (rootPackage.isEmpty()) "" else "$rootPackage."
    protected val id2typeHandler = serializer.id2typeHandler()
    private var methodMapperFactory: Function1<Class<*>, MethodMapper>? = null
    protected val interfaces: SortedSet<Class<*>> = TreeSet(Comparator.comparing<Class<*>, String>({ it.canonicalName }))

    init {
        check((initiator == null) || (acceptor == null) || (initiator.methodMapperFactory === acceptor.methodMapperFactory)) {
            "initiator and acceptor must have same methodMapperFactory"
        }
        if (initiator != null) methodMapperFactory = initiator.methodMapperFactory
        if (acceptor != null) methodMapperFactory = acceptor.methodMapperFactory
        interfaces.addAll(getInterfaces(initiator))
        interfaces.addAll(getInterfaces(acceptor))
        interfaces.forEach { this.checkType(it) }
    }

    protected fun checkType(type: Class<*>) {
        check(type.canonicalName.startsWith(rootPackage)) { "type '${type.canonicalName}' doesn't have root package '$rootPackage'" }
    }

    protected fun qualifiedName(type: Class<*>) = type.canonicalName.substring(rootPackage.length)
    protected fun methodMapper(type: Class<*>) = methodMapperFactory!!.invoke(type)
}
