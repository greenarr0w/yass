@file:JvmName("Kt")
@file:JvmMultifileClass

package ch.softappeal.yass.serialize.fast

import ch.softappeal.yass.Tag
import ch.softappeal.yass.allFields
import ch.softappeal.yass.tag
import java.io.PrintWriter
import java.lang.reflect.Field

/** This serializer assigns type and field id's automatically. Therefore, all peers must have the same version of the contract! */
@JvmOverloads
fun simpleFastSerializer(
    baseTypeHandlers: List<BaseTypeSerializer<*>>, treeConcreteClasses: List<Class<*>>, graphConcreteClasses: List<Class<*>> = emptyList()
) = object : FastSerializer() {
    init {
        var id = FirstTypeId
        for (typeHandler in baseTypeHandlers) addBaseType(TypeDesc(id++, typeHandler))
        for (type in treeConcreteClasses) if (type.isEnum) addEnum(id++, type) else addClass(id++, type, false)
        for (type in graphConcreteClasses) {
            checkClass(type)
            addClass(id++, type, true)
        }
        fixupFields()
    }

    fun addClass(typeId: Int, type: Class<*>, graph: Boolean) {
        val id2field = mutableMapOf<Int, Field>()
        var fieldId = FirstFieldId
        for (field in allFields(type)) id2field[fieldId++] = field
        addClass(typeId, type, graph, id2field)
    }
}

/** This serializer assigns type and field id's from its [Tag]. */
@JvmOverloads
fun taggedFastSerializer(
    baseTypeDescs: Collection<TypeDesc>, treeConcreteClasses: Collection<Class<*>>, graphConcreteClasses: Collection<Class<*>> = emptyList()
) = object : FastSerializer() {
    init {
        baseTypeDescs.forEach { addBaseType(it) }
        treeConcreteClasses.forEach { type -> if (type.isEnum) addEnum(tag(type), type) else addClass(type, false) }
        graphConcreteClasses.forEach { type ->
            checkClass(type)
            addClass(type, true)
        }
        fixupFields()
    }

    fun addClass(type: Class<*>, graph: Boolean) {
        val id2field = mutableMapOf<Int, Field>()
        for (field in allFields(type)) {
            val id = tag(field)
            val oldField = id2field.put(id, field)
            require(oldField == null) { "tag $id used for fields '$field' and '$oldField'" }
        }
        addClass(tag(type), type, graph, id2field)
    }
}

fun FastSerializer.print(printer: PrintWriter) = id2typeHandler().forEach { id, typeHandler ->
    if (id < FirstTypeId) return@forEach
    val type = typeHandler.type
    printer.print("$id: ${type.canonicalName}")
    if (typeHandler is BaseTypeSerializer<*>) {
        printer.println()
        if (type.isEnum) for ((i, c) in type.enumConstants.withIndex()) printer.println("    $i: $c")
    } else if (typeHandler is ClassTypeSerializer) {
        printer.println(" (graph=${typeHandler.graph})")
        for (fd in typeHandler.fieldDescs) printer.println("    ${fd.id}: ${fd.handler.field.toGenericString()}")
    }
    printer.println()
}
