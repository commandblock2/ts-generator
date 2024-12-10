/*
 * Copyright 2017 Alicia Boya García
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.ntrrgc.tsGenerator

import java.beans.Introspector
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.*
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.javaType

/**
 * TypeScript definition generator.
 *
 * Generates the content of a TypeScript definition file (.d.ts) that
 * covers a set of Kotlin and Java classes.
 *
 * This is useful when data classes are serialized to JSON and
 * handled in a JS or TypeScript web frontend.
 *
 * Supports:
 *  * Primitive types, with explicit int
 *  * Kotlin and Java classes
 *  * Data classes
 *  * Enums
 *  * Any type
 *  * Generic classes, without type erasure
 *  * Generic constraints
 *  * Class inheritance
 *  * Abstract classes
 *  * Lists as JS arrays
 *  * Maps as JS objects
 *  * Null safety, even inside composite types
 *  * Java beans
 *  * Mapping types
 *  * Customizing class definitions via transformers
 *  * Parenthesis are placed only when they are needed to disambiguate
 *
 * @constructor
 *
 * @param rootClasses Initial classes to traverse. Enough definitions
 * will be created to cover them and the types of their properties
 * (and so on recursively).
 *
 * @param mappings Allows to map some JVM types with JS/TS types. This
 * can be used e.g. to map LocalDateTime to JS Date.
 *
 * @param classTransformers Special transformers for certain subclasses.
 * They allow to filter out some classes, customize what methods are
 * exported, how they names are generated and what types are generated.
 *
 * @param ignoreSuperclasses Classes and interfaces specified here will
 * not be emitted when they are used as superclasses or implemented
 * interfaces of a class.
 *
 * @param intTypeName Defines the name integer numbers will be emitted as.
 * By default it's number, but can be changed to int if the TypeScript
 * version used supports it or the user wants to be extra explicit.
 */
class TypeScriptGenerator(
    rootClasses: Iterable<KClass<*>>,
    private val mappings: Map<KClass<*>, String> = mapOf(),
    classTransformers: List<ClassTransformer> = listOf(),
    ignoreSuperclasses: Set<KClass<*>> = setOf(),
    private val intTypeName: String = "number",
    private val voidType: VoidType = VoidType.NULL
) {


    // We make an assumption of the Modules WILL contain only 1 class
    // because there is no reliable way of dumping information at runtime
    // source: claude-3-5-sonnet

    private val visitedClasses: MutableSet<KClass<*>> = java.util.HashSet()

    private val generatedDefinitions = mutableMapOf<String, List<String>>()
    private val generatedImports = mutableMapOf<String, List<String>>()
    private val pipeline = ClassTransformerPipeline(classTransformers)

    private val ignoredSuperclasses = setOf(
        Any::class,
        java.io.Serializable::class,
        Comparable::class
    ).plus(ignoreSuperclasses)

    init {
        rootClasses.forEach { visitClass(it) }
    }

    companion object {
        private val KotlinAnyOrNull = Any::class.createType(nullable = true)

        fun isJavaBeanProperty(kProperty: KProperty<*>, klass: KClass<*>): Boolean {
            val beanInfo = Introspector.getBeanInfo(klass.java)
            return beanInfo.propertyDescriptors
                .any { bean -> bean.name == kProperty.name }
        }
    }

    private fun getFilePathForClass(klass: KClass<*>): String {
        val packagePath = klass.java.`package`?.name?.replace('.', '/') ?: ""
        val className = klass.simpleName
        return if (packagePath.isEmpty()) {
            "$className.d.ts"
        } else {
            "$packagePath/$className.d.ts"
        }
    }

    private fun visitClass(klass: KClass<*>) {
        if (klass in visitedClasses)
            return

        visitedClasses.add(klass)

        val generatedFilePath = getFilePathForClass(klass)

        // now extract all dependent types

        (klass
            // Supertypes
            .supertypes.map { it.classifier }.asSequence() +
                // Properties
                klass.declaredMemberProperties.asSequence() +
                // Functions
                // Types of function parameters and return value
                klass.declaredMembers.flatMap {
                    it.parameters.map { param -> param.type.classifier }.asSequence() +
                            listOf(it.returnType.classifier).asSequence()
                    // now we need to deal with generics
                    it.typeParameters.flatMap { typeParameter ->
                        listOf(typeParameter).asSequence() +
                                typeParameter.upperBounds.map { bound -> bound.classifier }
                    }
                } +
                // see continue-sessions/what-is-type-upper-bounds.md for why we need to include the type arguments of functions here.
                klass.typeParameters.flatMap {
                    listOf(it).asSequence() +
                            it.upperBounds.map { bound -> bound.classifier }
                }


                )
            .filter { it is KClass<*> }
            .also {
                val importList = it.mapNotNull { (it as KClass<*>).simpleName }.toSet().toList()
                generatedImports.put(
                    generatedFilePath,
                    generatedImports.getOrDefault(generatedFilePath, listOf()) + importList
                )
            }.forEach {
                visitClass(it as KClass<*>)
            }
    }

    private fun formatClassType(type: KClass<*>): String {
        visitClass(type)
        return type.simpleName!!
    }

    private fun formatKType(kType: KType): TypeScriptType {
        val classifier = kType.classifier
        if (classifier is KClass<*>) {
            val existingMapping = mappings[classifier]
            if (existingMapping != null) {
                return TypeScriptType.single(mappings[classifier]!!, kType.isMarkedNullable, voidType)
            }
        }

        val classifierTsType = when (classifier) {
            Boolean::class -> "boolean"
            String::class, Char::class -> "string"
            Int::class,
            Long::class,
            Short::class,
            Byte::class -> intTypeName

            Float::class, Double::class -> "number"
            Any::class -> "any"
            else -> {
                @Suppress("IfThenToElvis")
                if (classifier is KClass<*>) {
                    if (classifier.isSubclassOf(Iterable::class)
                        || classifier.javaObjectType.isArray
                    ) {
                        arrayFromKType(kType)
                    } else if (classifier.isSubclassOf(Map::class)) {
                        mapFromKType(kType)
                    } else {
                        nonPrimitiveFromKType(kType)
                    }
                } else if (classifier is KTypeParameter) {
                    classifier.name
                } else {
                    "UNKNOWN" // giving up
                }
            }
        }

        return TypeScriptType.single(classifierTsType, kType.isMarkedNullable, voidType)
    }

    private fun nonPrimitiveFromKType(kType: KType): String =
        // Use class name, with or without template parameters
        formatClassType(kType.classifier as KClass<*>) + if (kType.arguments.isNotEmpty()) {
            "<" + kType.arguments
                .map { arg -> formatKType(arg.type ?: KotlinAnyOrNull).formatWithoutParenthesis() }
                .joinToString(", ") + ">"
        } else ""

    private fun arrayFromKType(kType: KType): String {
        // Use native JS array
        // Parenthesis are needed to disambiguate complex cases,
        // e.g. (Pair<string|null, int>|null)[]|null
        val itemType = when (kType.classifier) {
            // Native Java arrays... unfortunately simple array types like these
            // are not mapped automatically into kotlin.Array<T> by kotlin-reflect :(
            IntArray::class -> Int::class.createType(nullable = false)
            ShortArray::class -> Short::class.createType(nullable = false)
            ByteArray::class -> Byte::class.createType(nullable = false)
            CharArray::class -> Char::class.createType(nullable = false)
            LongArray::class -> Long::class.createType(nullable = false)
            FloatArray::class -> Float::class.createType(nullable = false)
            DoubleArray::class -> Double::class.createType(nullable = false)

            // Class container types (they use generics)
            else -> kType.arguments.single().type ?: KotlinAnyOrNull
        }
        return "${formatKType(itemType).formatWithParenthesis()}[]"
    }

    private fun mapFromKType(kType: KType): String {
        // Use native JS associative object
        val rawKeyType = kType.arguments[0].type ?: KotlinAnyOrNull
        val keyType = formatKType(rawKeyType)
        val valueType = formatKType(kType.arguments[1].type ?: KotlinAnyOrNull)
        return if ((rawKeyType.classifier as? KClass<*>)?.java?.isEnum == true)
            "{ [key in ${keyType.formatWithoutParenthesis()}]: ${valueType.formatWithoutParenthesis()} }"
        else
            "{ [key: ${keyType.formatWithoutParenthesis()}]: ${valueType.formatWithoutParenthesis()} }"
    }

    private fun generateEnum(klass: KClass<*>): String {
        return "type ${klass.simpleName} = ${
            klass.java.enumConstants
                .map { constant: Any ->
                    constant.toString().toJSString()
                }
                .joinToString(" | ")
        };"
    }

    private fun generateInterface(klass: KClass<*>): String {
        val supertypes = klass.supertypes
            .filterNot { it.classifier in ignoredSuperclasses }
        val extendsString = if (supertypes.isNotEmpty()) {
            " extends " + supertypes
                .map { formatKType(it).formatWithoutParenthesis() }
                .joinToString(", ")
        } else ""

        val templateParameters = if (klass.typeParameters.isNotEmpty()) {
            "<" + klass.typeParameters
                .map { typeParameter ->
                    val bounds = typeParameter.upperBounds
                        .filter { it.classifier != Any::class }
                    typeParameter.name + if (bounds.isNotEmpty()) {
                        " extends " + bounds
                            .map { bound ->
                                formatKType(bound).formatWithoutParenthesis()
                            }
                            .joinToString(" & ")
                    } else {
                        ""
                    }
                }
                .joinToString(", ") + ">"
        } else {
            ""
        }

        return "interface ${klass.simpleName}$templateParameters$extendsString {\n" +
                klass.declaredMemberProperties
                    .filter { !isFunctionType(it.returnType.javaType) }
                    .filter {
                        it.visibility == KVisibility.PUBLIC || isJavaBeanProperty(it, klass)
                    }
                    .let { propertyList ->
                        pipeline.transformPropertyList(propertyList, klass)
                    }
                    .map { property ->
                        val propertyName = pipeline.transformPropertyName(property.name, property, klass)
                        val propertyType = pipeline.transformPropertyType(property.returnType, property, klass)

                        val formattedPropertyType = formatKType(propertyType).formatWithoutParenthesis()
                        "    $propertyName: $formattedPropertyType;\n"
                    }
                    .joinToString("") +
                "}"
    }

    private fun isFunctionType(javaType: Type): Boolean {
        return javaType is KCallable<*>
                || javaType.typeName.startsWith("kotlin.jvm.functions.")
                || (javaType is ParameterizedType && isFunctionType(javaType.rawType))
    }

    private fun generateDefinition(klass: KClass<*>): String {
        return if (klass.java.isEnum) {
            generateEnum(klass)
        } else {
            generateInterface(klass)
        }
    }

    // Public API:
    @Suppress("unused")
    val definitionsMap: Map<String, String>
        get() = generatedDefinitions.map {
            it.key to generatedImports[it.key]?.joinToString("\n") + it.value.joinToString(separator = "\n\n")
        }.toMap()

    @Suppress("unused")
    val definitionsText: String
        get() = generatedDefinitions.values.flatten().joinToString("\n\n")

    @Suppress("unused")
    val individualDefinitions: Set<String>
        get() = generatedDefinitions.values.flatten().toSet()
}