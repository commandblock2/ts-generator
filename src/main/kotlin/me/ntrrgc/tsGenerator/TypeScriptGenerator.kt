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
    mappings: Map<KClass<*>, String> = mapOf(),
    classTransformers: List<ClassTransformer> = listOf(),
    ignoreSuperclasses: Set<KClass<*>> = setOf(),
    private val intTypeName: String = "number",
    private val voidType: VoidType = VoidType.NULL
) {


    // We make an assumption of the Modules WILL contain only 1 class
    // because there is no reliable way of dumping information at runtime
    // source: claude-3-5-sonnet
    inner class TypeScriptModule(
        val klass: KClass<*>
    ) {
        val path: String

        val dependentTypes = mutableSetOf<KClass<*>>()

        var definition: String

        val moduleText: String by lazy {
            dependentTypes.map {
                val path = modules[modules.keys.find { key -> isSameClass(key, it) }]!!.path
                "import { ${it.simpleName} } from './$path'"
            }.joinToString("\n", postfix = "\n") + "export " + definition
        }


        init {
            path = getFilePathForClass(klass)

            definition = generateDefinition()
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

        private fun generateDefinition(): String {
            return if (klass.java.isEnum) {
                generateEnum(klass)
            } else {
                generateInterface(klass)
            }
        }


        private fun formatKType(kType: KType): TypeScriptType {
            val classifier = kType.classifier
            if (classifier is KClass<*>) {
                val existingMapping = predefinedMappings[classifier]
                if (existingMapping != null) {
                    return TypeScriptType.single(predefinedMappings[classifier]!!, kType.isMarkedNullable, voidType)
                }
                if (!shouldIgnoreSuperclass(classifier))
                    dependentTypes.add(classifier)
            }


            val classifierTsType =
                if (classifier is KClass<*>) {
                    predefinedMappings.getOrDefault(
                        classifier,
                        if (classifier.isSubclassOf(Iterable::class)
                            || classifier.javaObjectType.isArray
                        )
                            arrayFromKType(kType)
                        else if (classifier.isSubclassOf(Map::class))
                            mapFromKType(kType)
                        else
                            nonPrimitiveFromKType(kType)
                    )
                } else if (classifier is KTypeParameter)
                    classifier.name
                else
                    "UNKNOWN" // giving up

            return TypeScriptType.single(classifierTsType, kType.isMarkedNullable, voidType)
        }

        private fun nonPrimitiveFromKType(kType: KType): String =
            // Use class name, with or without template parameters
            (kType.classifier as KClass<*>).simpleName!! + if (kType.arguments.isNotEmpty()) {
                "<" + kType.arguments
                    .map { arg -> formatKType(arg.type ?: KotlinAnyOrNull).formatWithoutParenthesis() }
                    .joinToString(", ") + ">"
            } else ""

        private fun getIterableElementType(kType: KType): KType? {
            // Traverse supertypes to find `Iterable<T>`
            val classifier = kType.classifier as? KClass<*> ?: return null
            val iterableSupertype = classifier.supertypes
                .firstOrNull { it.classifier == Iterable::class } ?: return null

            // Extract the type argument of `Iterable<T>`
            return iterableSupertype.arguments.firstOrNull()?.type
        }

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
                else -> {
                    getIterableElementType(kType) ?: kType.arguments.singleOrNull()?.type ?: KotlinAnyOrNull
                }
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
                .filter {
                    if (it.classifier is KClass<*>) !isSameClass(
                        it.classifier as KClass<*>,
                        Any::class
                    ) else true
                }

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
                        .filter {
                            try {
                                !isFunctionType(it.returnType.javaType)
                            } catch (_: kotlin.reflect.jvm.internal.KotlinReflectionInternalError) {
                                false
                            }
                        }
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

    }

    private val modules = mutableMapOf<KClass<*>, TypeScriptModule>()

    private val pipeline = ClassTransformerPipeline(classTransformers)

    private val ignoredSuperclasses = setOf<KClass<*>>(
    ).plus(ignoreSuperclasses)

    private val predefinedMappings =
        mapOf(
            Boolean::class to "boolean",
            String::class to "string",
            Char::class to "string",

            Int::class to intTypeName,
            Long::class to intTypeName,
            Short::class to intTypeName,
            Byte::class to intTypeName,

            Float::class to "number",
            Double::class to "number",

            Any::class to "any"
        ).plus(mappings) // mappings has a higher priority

    private val shouldIgnoreSuperclass: (KClass<*>) -> Boolean = { klass: KClass<*> ->
        klass.isSubclassOf(Iterable::class) || klass.javaObjectType.isArray || klass.isSubclassOf(Map::class)
    }


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

    private fun isSameClass(klassLhs: KClass<*>, klassRhs: KClass<*>): Boolean =
        klassLhs.qualifiedName == klassRhs.qualifiedName

    private fun visitClass(klass: KClass<*>) {
        if (ignoredSuperclasses.count {
                isSameClass(
                    klass,
                    it
                )
            } > 0 || shouldIgnoreSuperclass(klass) || modules.keys.find { module ->
                isSameClass(
                    module,
                    klass
                )
            } != null)
            return

        val module = TypeScriptModule(klass)
        modules[klass] = module
        module.dependentTypes.forEach { visitClass(it) }
    }


    private fun isFunctionType(javaType: Type): Boolean {
        return javaType is KCallable<*>
                || javaType.typeName.startsWith("kotlin.jvm.functions.")
                || (javaType is ParameterizedType && isFunctionType(javaType.rawType))
    }


    // Public API:

    @Suppress("unused")
    val definitionsAsModules: Map<String, String>
        get() = modules.map {
            it.value.path to it.value.moduleText
        }.toMap()

    @Suppress("unused")
    val definitionsText: String
        get() = modules.map { it.value.definition }.joinToString("\n\n")

    @Suppress("unused")
    val individualDefinitions: Set<String>
        get() = modules.map { it.value.definition }.toSet()
}