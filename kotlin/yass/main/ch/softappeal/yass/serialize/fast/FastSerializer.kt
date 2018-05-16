package ch.softappeal.yass.serialize.fast

import ch.softappeal.yass.ALLOCATE
import ch.softappeal.yass.serialize.Reader
import ch.softappeal.yass.serialize.Serializer
import ch.softappeal.yass.serialize.Writer
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.ArrayList
import java.util.Collections
import java.util.Comparator
import java.util.HashMap
import java.util.IdentityHashMap
import java.util.SortedMap
import java.util.TreeMap

internal class Input(val reader: Reader, private val id2typeHandler: Map<Int, TypeHandler>) {
    var graphObjects: MutableList<Any>? = null
    fun read(): Any? {
        val id = reader.readVarInt()
        return (id2typeHandler[id] ?: error("no type with id $id")).read(this)
    }
}

internal class Output(val writer: Writer, private val class2typeDesc: Map<Class<*>, TypeDesc>) {
    var object2reference: MutableMap<Any, Int>? = null
    fun write(value: Any?): Unit = when (value) {
        null -> TD_NULL.write(this, null)
        is List<*> -> TD_LIST.write(this, value)
        else -> (class2typeDesc[value.javaClass] ?: error("missing type '${value.javaClass.canonicalName}'")).write(this, value)
    }
}

abstract class TypeHandler internal constructor(val type: Class<*>) {
    internal abstract fun read(input: Input): Any?
    internal abstract fun write(output: Output, value: Any?)
    internal open fun write(output: Output, id: Int, value: Any?) {
        output.writer.writeVarInt(id)
        write(output, value)
    }
}

class TypeDesc(val id: Int, val handler: TypeHandler) {
    init {
        require(id >= 0) { "id $id for type '${handler.type.canonicalName}' must be >= 0" }
    }

    internal fun write(output: Output, value: Any?) = handler.write(output, id, value)
}

private class VoidType

val TD_NULL = TypeDesc(0, object : TypeHandler(VoidType::class.java) {
    override fun read(input: Input): Any? = null
    override fun write(output: Output, value: Any?) {}
})

private class ReferenceType

val TD_REFERENCE = TypeDesc(1, object : TypeHandler(ReferenceType::class.java) {
    override fun read(input: Input) = input.graphObjects!![input.reader.readVarInt()]
    override fun write(output: Output, value: Any?) = output.writer.writeVarInt(value as Int)
})

val TD_LIST = TypeDesc(2, object : TypeHandler(List::class.java) {
    override fun read(input: Input): List<*> {
        var length = input.reader.readVarInt()
        val list = mutableListOf<Any?>()
        while (length-- > 0) list.add(input.read())
        return list
    }

    override fun write(output: Output, value: Any?) {
        val list = value as List<*>
        output.writer.writeVarInt(list.size)
        for (e in list) output.write(e)
    }
})

const val FIRST_TYPE_ID = 3

abstract class BaseTypeHandler<V : Any> protected constructor(type: Class<V>) : TypeHandler(type) {
    final override fun read(input: Input) = read(input.reader)
    @Suppress("UNCHECKED_CAST")
    final override fun write(output: Output, value: Any?) = write(output.writer, value as V)

    abstract fun write(writer: Writer, value: V)
    abstract fun read(reader: Reader): V
}

fun primitiveWrapperType(type: Class<*>): Class<*> = when (type) {
    Boolean::class.javaPrimitiveType -> Boolean::class.javaObjectType
    Byte::class.javaPrimitiveType -> Byte::class.javaObjectType
    Short::class.javaPrimitiveType -> Short::class.javaObjectType
    Int::class.javaPrimitiveType -> Int::class.javaObjectType
    Long::class.javaPrimitiveType -> Long::class.javaObjectType
    Char::class.javaPrimitiveType -> Char::class.javaObjectType
    Float::class.javaPrimitiveType -> Float::class.javaObjectType
    Double::class.javaPrimitiveType -> Double::class.javaObjectType
    else -> type
}

class FieldHandler internal constructor(val field: Field) {
    private var typeHandler: TypeHandler? = null
    fun typeHandler() = typeHandler

    internal fun fixup(class2typeDesc: Map<Class<*>, TypeDesc>) {
        val typeDesc = class2typeDesc[primitiveWrapperType(field.type)]
        typeHandler = typeDesc?.handler
        if (typeHandler is ClassTypeHandler) typeHandler = null
    }

    internal fun read(input: Input, value: Any) =
        field.set(value, if (typeHandler == null) input.read() else typeHandler!!.read(input))

    internal fun write(output: Output, id: Int, value: Any) {
        val f = field.get(value)
        if (f != null) {
            output.writer.writeVarInt(id)
            if (typeHandler == null) output.write(f) else typeHandler!!.write(output, f)
        }
    }
}

private const val END_FIELD_ID = 0
const val FIRST_FIELD_ID = END_FIELD_ID + 1

class FieldDesc internal constructor(val id: Int, val handler: FieldHandler)

class ClassTypeHandler internal constructor(
    type: Class<*>, val graph: Boolean, private val id2fieldHandler: Map<Int, FieldHandler>
) : TypeHandler(type) {
    private val allocator = ALLOCATE(type)
    val fieldDescs: List<FieldDesc>

    init {
        fieldDescs = mutableListOf()
        for ((id, handler) in id2fieldHandler) {
            require(id >= FIRST_FIELD_ID) { "id $id for field '${handler.field}' must be >= $FIRST_FIELD_ID" }
            fieldDescs.add(FieldDesc(id, handler))
        }
        Collections.sort(fieldDescs, Comparator.comparingInt(FieldDesc::id))
    }

    internal fun fixupFields(class2typeDesc: Map<Class<*>, TypeDesc>) = id2fieldHandler.values.forEach { it.fixup(class2typeDesc) }

    override fun read(input: Input): Any {
        val value = allocator()
        if (graph) {
            if (input.graphObjects == null) input.graphObjects = mutableListOf()
            input.graphObjects!!.add(value)
        }
        while (true) {
            val id = input.reader.readVarInt()
            if (id == END_FIELD_ID) return value
            (id2fieldHandler[id] ?: error("class '${type.canonicalName}' doesn't have a field with id $id")).read(input, value)
        }
    }

    override fun write(output: Output, id: Int, value: Any?) {
        if (graph) {
            if (output.object2reference == null) output.object2reference = IdentityHashMap(16)
            val object2reference = output.object2reference
            val reference = object2reference!![value]
            if (reference != null) {
                TD_REFERENCE.write(output, reference)
                return
            }
            object2reference[value!!] = object2reference.size
        }
        super.write(output, id, value)
    }

    override fun write(output: Output, value: Any?) {
        for (fieldDesc in fieldDescs) fieldDesc.handler.write(output, fieldDesc.id, value!!)
        output.writer.writeVarInt(END_FIELD_ID)
    }
}

/**
 * This fast and compact serializer supports the following types (type id's must be &gt;= [FIRST_TYPE_ID]):
 *  - `null`
 *  - Subclasses of [BaseTypeHandler]
 *  - [List] (deserialize creates an [ArrayList])
 *  - enumeration types (an enumeration constant is serialized with its ordinal number)
 *  - class hierarchies with all non-static and non-transient fields
 *    (field names and id's must be unique in the path to its super classes and id's must be &gt;= [FIRST_FIELD_ID])
 *  - exceptions (but without fields of [Throwable]; therefore, you should implement [Throwable.message])
 *  - graphs with cycles
 *
 * There is some support for contract versioning:
 * - Deserialization of old classes to new classes with new nullable fields is allowed.
 *   These fields will be set to `null` (ignoring constructors).
 *   Default values for these fields could be implemented with a getter method checking for `null`.
 * - Serialization of new classes with new nullable fields to old classes is allowed if the new values are `null`.
 * - Deserialization of old enumerations to new enumerations with new constants at the end is allowed.
 */
abstract class FastSerializer protected constructor() : Serializer {
    private val class2typeDesc = HashMap<Class<*>, TypeDesc>(64)
    private val id2typeHandler = HashMap<Int, TypeHandler>(64)

    init {
        addType(TD_NULL)
        addType(TD_REFERENCE)
        addType(TD_LIST)
    }

    fun id2typeHandler(): SortedMap<Int, TypeHandler> = TreeMap(id2typeHandler)

    private fun addType(typeDesc: TypeDesc) {
        require(class2typeDesc.put(typeDesc.handler.type, typeDesc) == null) {
            "type '${typeDesc.handler.type.canonicalName}' already added"
        }
        val oldTypeHandler = id2typeHandler.put(typeDesc.id, typeDesc.handler)
        if (oldTypeHandler != null)
            error("type id ${typeDesc.id} used for '${typeDesc.handler.type.canonicalName}' and '${oldTypeHandler.type.canonicalName}'")
    }

    protected fun addEnum(id: Int, type: Class<*>) {
        require(type.isEnum) { "type '${type.canonicalName}' is not an enumeration" }
        @Suppress("UNCHECKED_CAST") val enumeration = type as Class<Enum<*>>
        val constants = enumeration.enumConstants
        addType(TypeDesc(id, object : BaseTypeHandler<Enum<*>>(enumeration) {
            override fun read(reader: Reader) = constants[reader.readVarInt()]
            override fun write(writer: Writer, value: Enum<*>) = writer.writeVarInt(value.ordinal)
        }))
    }

    protected fun checkClass(type: Class<*>) = require(!type.isEnum) { "type '${type.canonicalName}' is an enumeration" }

    protected fun addClass(id: Int, type: Class<*>, graph: Boolean, id2field: Map<Int, Field>) {
        require(!Modifier.isAbstract(type.modifiers)) { "type '${type.canonicalName}' is abstract" }
        val id2fieldHandler = mutableMapOf<Int, FieldHandler>()
        val name2field = mutableMapOf<String, Field>()
        for ((fieldId, field) in id2field) {
            val oldField = name2field.put(field.name, field)
            require(oldField == null) { "duplicated field name '$field' and '$oldField' not allowed in class hierarchy" }
            id2fieldHandler[fieldId] = FieldHandler(field)
        }
        addType(TypeDesc(id, ClassTypeHandler(type, graph, id2fieldHandler)))
    }

    protected fun addBaseType(typeDesc: TypeDesc) {
        require(!typeDesc.handler.type.isEnum) { "base type '${typeDesc.handler.type.canonicalName}' is an enumeration" }
        addType(typeDesc)
    }

    protected fun fixupFields() {
        for (typeDesc in class2typeDesc.values) (typeDesc.handler as? ClassTypeHandler)?.fixupFields(class2typeDesc)
    }

    override fun read(reader: Reader) = Input(reader, id2typeHandler).read()
    override fun write(writer: Writer, value: Any?) = Output(writer, class2typeDesc).write(value)
}
