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

package me.ntrrgc.tsGenerator.tests

import me.ntrrgc.tsGenerator.ClassTransformer
import me.ntrrgc.tsGenerator.TypeScriptGenerator
import me.ntrrgc.tsGenerator.VoidType
import me.ntrrgc.tsGenerator.onlyOnSubclassesOf

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import java.beans.Introspector
import java.time.Instant
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.withNullability
import kotlin.reflect.jvm.kotlinFunction

fun assertGeneratedCode(
    klass: KClass<*>,
    expectedOutput: Set<String>,
    mappings: Map<KClass<*>, String> = mapOf(),
    classTransformers: List<ClassTransformer> = listOf(),
    ignoreSuperclasses: Set<KClass<*>> = setOf(),
    voidType: VoidType = VoidType.NULL
) {
    val generator = TypeScriptGenerator(
        listOf(klass), mappings, classTransformers,
        ignoreSuperclasses, intTypeName = "int", voidType = voidType
    )

    val expected = expectedOutput
        .map(TypeScriptDefinitionFactory::fromCode)
        .toSet()
    val actual = generator.individualDefinitions
        .map(TypeScriptDefinitionFactory::fromCode)
        .toSet()

    actual shouldBe expected
}

fun assertGeneratedModule(
    klass: KClass<*>,
    expectedOutput: Map<String, String>,
    mappings: Map<KClass<*>, String> = mapOf(),
    classTransformers: List<ClassTransformer> = listOf(),
    ignoreSuperclasses: Set<KClass<*>> = setOf(),
    voidType: VoidType = VoidType.NULL
) {
    val generator = TypeScriptGenerator(
        listOf(klass), mappings, classTransformers,
        ignoreSuperclasses, intTypeName = "int", voidType = voidType
    )

    val modules = generator.definitionsAsModules

    modules.keys shouldBe expectedOutput.keys


    expectedOutput.forEach {

        val generatedCode = modules[it.key]!!

        val actual = generatedCode
        val expected = it.value

        actual shouldBe expected
    }


}

class Empty
class ClassWithMember(val a: String)
class SimpleTypes(
    val aString: String,
    var anInt: Int,
    val aDouble: Double,
    private val privateMember: String
)

class ClassWithLists(
    val aList: List<String>,
    val anArrayList: ArrayList<String>
)

class ClassWithArray(
    val items: Array<String>
)

class Widget(
    val name: String,
    val value: Int
)

class ClassWithDependencies(
    val widget: Widget
)

class ClassWithNestedDependencies(
    val widget: Widget,
    val classWithDependencies: ClassWithDependencies
)

class ClassWithMixedNullables(
    val count: Int,
    val time: Instant?
)

class ClassWithNullables(
    val widget: Widget?
)

class ClassWithComplexNullables(
    val maybeWidgets: List<String?>?,
    val maybeWidgetsArray: Array<String?>?
)

class ClassWithNullableList(
    val strings: List<String>?
)

open class GenericClass<A, out B, out C : List<Any>>(
    val a: A,
    val b: List<B?>,
    val c: C,
    private val privateMember: A
)

class ClassWithNestedGenericMembers(val xD: List<List<List<Int>>>, val xDD: Result<Result<Result<Int>>>)
open class BaseClass(val a: Int)
class DerivedClass(val b: List<String>) : BaseClass(4)
class GenericDerivedClass<B>(a: Empty, b: List<B?>, c: ArrayList<String>) :
    GenericClass<Empty, B, ArrayList<String>>(a, b, c, a)

class ClassWithMethods(
    val propertyMethod: () -> Int,
    val propertyMethodReturnsMightNull: () -> Int?,
    val propertyMethodTakesMightNull: (Int?) -> Unit
) {
    fun regularMethod() = 4
    fun regularMethodReturnsMightNull(): Int? = null
    fun regularMethodTakesMightNull(x: Int?) {}
}

abstract class AbstractClass(val concreteProperty: String) {
    abstract val abstractProperty: Int
    abstract fun abstractMethod()
}

enum class Direction {
    North,
    West,
    South,
    East
}

class ClassWithEnum(val direction: Direction)
data class DataClass(val prop: String)
class ClassWithAny(val required: Any, val optional: Any?)
class ClassWithMap(val values: Map<String, String>)
class ClassWithEnumMap(val values: Map<Direction, String>)

class Tests : StringSpec({
    "handles empty class" {
        assertGeneratedCode(
            Empty::class, setOf(
                """
interface Empty {
}
"""
            )
        )
    }

    "handles classes with a single member" {
        assertGeneratedCode(
            ClassWithMember::class, setOf(
                """
interface ClassWithMember {
    a: string;
}
"""
            )
        )
    }

    "handles SimpleTypes" {
        assertGeneratedCode(
            SimpleTypes::class, setOf(
                """
    interface SimpleTypes {
        aString: string;
        anInt: int;
        aDouble: number;
    }
    """
            )
        )
    }

    "handles ClassWithLists" {
        assertGeneratedCode(
            ClassWithLists::class, setOf(
                """
    interface ClassWithLists {
        aList: string[];
        anArrayList: string[];
    }
    """
            )
        )
    }

    "handles ClassWithArray" {
        assertGeneratedCode(
            ClassWithArray::class, setOf(
                """
    interface ClassWithArray {
        items: string[];
    }
    """
            )
        )
    }

    val widget = """
    interface Widget {
        name: string;
        value: int;
    }
    """

    val classWithDependencies = """
    interface ClassWithDependencies {
        widget: Widget;
    }
    """

    "handles ClassWithDependencies" {
        assertGeneratedCode(ClassWithDependencies::class, setOf(classWithDependencies, widget))
    }

    "handles ClassWithNestedDependencies" {
        assertGeneratedCode(
            ClassWithNestedDependencies::class, setOf(
                """
    interface ClassWithNestedDependencies {
        classWithDependencies: ClassWithDependencies;
        widget: Widget;
    }
    """, classWithDependencies, widget
            )
        )
    }

    "handles ClassWithNullables" {
        assertGeneratedCode(
            ClassWithNullables::class, setOf(
                """
    interface ClassWithNullables {
        widget: Widget | null;
    }
    """, widget
            )
        )
    }

    "handles ClassWithMixedNullables using mapping" {
        assertGeneratedCode(
            ClassWithMixedNullables::class, setOf(
                """
    interface ClassWithMixedNullables {
        count: int;
        time: string | null;
    }
    """
            ), mappings = mapOf(Instant::class to "string")
        )
    }

    "handles ClassWithMixedNullables using mapping and VoidTypes" {
        assertGeneratedCode(
            ClassWithMixedNullables::class, setOf(
                """
    interface ClassWithMixedNullables {
        count: int;
        time: string | undefined;
    }
    """
            ), mappings = mapOf(Instant::class to "string"), voidType = VoidType.UNDEFINED
        )
    }

    "handles ClassWithComplexNullables" {
        assertGeneratedCode(
            ClassWithComplexNullables::class, setOf(
                """
    interface ClassWithComplexNullables {
        maybeWidgets: (string | null)[] | null;
        maybeWidgetsArray: (string | null)[] | null;
    }
    """
            )
        )
    }

    "handles ClassWithNullableList" {
        assertGeneratedCode(
            ClassWithNullableList::class, setOf(
                """
    interface ClassWithNullableList {
        strings: string[] | null;
    }
    """
            )
        )
    }

    "handles GenericClass" {
        assertGeneratedCode(
            GenericClass::class, setOf(
                """
    interface GenericClass<A, B, C extends any[]> {
        a: A;
        b: (B | null)[];
        c: C;
    }
    """
            )
        )
    }

    "handles ClassWithNestedGenericMembers" {
        assertGeneratedCode(
            ClassWithNestedGenericMembers::class, setOf(
                """
    interface ClassWithNestedGenericMembers {
        xD: int[][][];
        xDD: Result<Result<Result<int>>>;
    }
    """,
                """
        interface Result<T> {
        isFailure: boolean;
        isSuccess: boolean;
    }
    """
            )
        )
    }

    "handles DerivedClass" {
        assertGeneratedCode(
            DerivedClass::class, setOf(
                """
    interface DerivedClass extends BaseClass {
        b: string[];
    }
    """, """
    interface BaseClass {
        a: int;
    }
    """
            )
        )
    }

    "handles GenericDerivedClass" {
        assertGeneratedCode(
            GenericDerivedClass::class, setOf(
                """
    interface GenericClass<A, B, C extends any[]> {
        a: A;
        b: (B | null)[];
        c: C;
    }
    """, """
    interface Empty {
    }
    """, """
    interface GenericDerivedClass<B> extends GenericClass<Empty, B, string[]> {
    }
    """
            )
        )
    }

    "handles ClassWithMethods" {
        assertGeneratedCode(
            ClassWithMethods::class, setOf(
                """
    interface ClassWithMethods {
    }
    """
            )
        )
    }

    "handles AbstractClass" {
        assertGeneratedCode(
            AbstractClass::class, setOf(
                """
    interface AbstractClass {
        concreteProperty: string;
        abstractProperty: int;
    }
    """
            )
        )
    }

    "handles ClassWithEnum" {
        assertGeneratedCode(
            ClassWithEnum::class, setOf(
                """
    interface ClassWithEnum {
        direction: Direction;
    }
    """, """type Direction = "North" | "West" | "South" | "East";"""
            )
        )
    }

    "handles DataClass" {
        assertGeneratedCode(
            DataClass::class, setOf(
                """
    interface DataClass {
        prop: string;
    }
    """
            )
        )
    }

    "handles ClassWithAny" {
        // Note: in TypeScript any includes null and undefined.
        assertGeneratedCode(
            ClassWithAny::class, setOf(
                """
    interface ClassWithAny {
        required: any;
        optional: any;
    }
    """
            )
        )
    }

    "supports type mapping for classes" {
        assertGeneratedCode(
            ClassWithDependencies::class, setOf(
                """
interface ClassWithDependencies {
    widget: CustomWidget;
}
"""
            ), mappings = mapOf(Widget::class to "CustomWidget")
        )
    }

    "supports type mapping for basic types" {
        assertGeneratedCode(
            DataClass::class, setOf(
                """
    interface DataClass {
        prop: CustomString;
    }
    """
            ), mappings = mapOf(String::class to "CustomString")
        )
    }

    "supports transforming property names" {
        assertGeneratedCode(DataClass::class, setOf(
            """
    interface DataClass {
        PROP: string;
    }
    """
        ), classTransformers = listOf(
            object : ClassTransformer {
                /**
                 * Returns the property name that will be included in the
                 * definition.
                 *
                 * If it returns null, the value of the next class transformer
                 * in the pipeline is used.
                 */
                override fun transformPropertyName(
                    propertyName: String,
                    property: KProperty<*>,
                    klass: KClass<*>
                ): String {
                    return propertyName.toUpperCase()
                }
            }
        ))
    }

    "supports transforming only some classes" {
        assertGeneratedCode(
            ClassWithDependencies::class, setOf(
                """
interface ClassWithDependencies {
    widget: Widget;
}
""", """
interface Widget {
    NAME: string;
    VALUE: int;
}
"""
            ), classTransformers = listOf(
                object : ClassTransformer {
                    override fun transformPropertyName(
                        propertyName: String,
                        property: KProperty<*>,
                        klass: KClass<*>
                    ): String {
                        return propertyName.toUpperCase()
                    }
                }.onlyOnSubclassesOf(Widget::class)
            )
        )
    }

    "supports transforming types" {
        assertGeneratedCode(DataClass::class, setOf(
            """
    interface DataClass {
        prop: int | null;
    }
    """
        ), classTransformers = listOf(
            object : ClassTransformer {
                override fun transformPropertyType(type: KType, property: KProperty<*>, klass: KClass<*>): KType {
                    if (klass == DataClass::class && property.name == "prop") {
                        return Int::class.createType(nullable = true)
                    } else {
                        return type
                    }
                }
            }
        ))
    }

    "supports filtering properties" {
        assertGeneratedCode(SimpleTypes::class, setOf(
            """
    interface SimpleTypes {
        aString: string;
        aDouble: number;
    }
    """
        ), classTransformers = listOf(
            object : ClassTransformer {
                override fun transformPropertyList(
                    properties: List<KProperty<*>>,
                    klass: KClass<*>
                ): List<KProperty<*>> {
                    return properties.filter { it.name != "anInt" }
                }
            }
        ))
    }

    "supports filtering subclasses" {
        assertGeneratedCode(
            DerivedClass::class, setOf(
                """
    interface DerivedClass extends BaseClass {
        B: string[];
    }
    """, """
    interface BaseClass {
        A: int;
    }
    """
            ), classTransformers = listOf(
                object : ClassTransformer {
                    override fun transformPropertyName(
                        propertyName: String,
                        property: KProperty<*>,
                        klass: KClass<*>
                    ): String {
                        return propertyName.toUpperCase()
                    }
                }.onlyOnSubclassesOf(BaseClass::class)
            )
        )
    }

    "uses all transformers in pipeline" {
        assertGeneratedCode(SimpleTypes::class, setOf(
            """
    interface SimpleTypes {
        aString12: string;
        aDouble12: number;
        anInt12: int;
    }
    """
        ), classTransformers = listOf(
            object : ClassTransformer {
                override fun transformPropertyName(
                    propertyName: String,
                    property: KProperty<*>,
                    klass: KClass<*>
                ): String {
                    return propertyName + "1"
                }
            },
            object : ClassTransformer {
            },
            object : ClassTransformer {
                override fun transformPropertyName(
                    propertyName: String,
                    property: KProperty<*>,
                    klass: KClass<*>
                ): String {
                    return propertyName + "2"
                }
            }
        ))
    }

    "handles JavaClass" {
        assertGeneratedCode(
            JavaClass::class, setOf(
                """
    interface JavaClass {
        name: string;
        results: int[];
        multidimensional: string[][];
        finished: boolean;
    }
    """
            )
        )
    }

    "handles JavaClassWithOptional" {
        assertGeneratedCode(JavaClassWithOptional::class, setOf(
            """
    interface JavaClassWithOptional {
        name: string;
        surname: string | null;
    }
    """
        ), classTransformers = listOf(
            object : ClassTransformer {
                override fun transformPropertyType(
                    type: KType,
                    property: KProperty<*>,
                    klass: KClass<*>
                ): KType {
                    val bean = Introspector.getBeanInfo(klass.java)
                        .propertyDescriptors
                        .find { it.name == property.name }

                    val getterReturnType = bean?.readMethod?.kotlinFunction?.returnType
                    if (getterReturnType?.classifier == Optional::class) {
                        val wrappedType = getterReturnType.arguments.first().type!!
                        return wrappedType.withNullability(true)
                    } else {
                        return type
                    }
                }
            }
        ))
    }

    "handles ClassWithComplexNullables when serializing as undefined" {
        assertGeneratedCode(
            ClassWithComplexNullables::class, setOf(
                """
    interface ClassWithComplexNullables {
        maybeWidgets: (string | undefined)[] | undefined;
        maybeWidgetsArray: (string | undefined)[] | undefined;
    }
    """
            ), voidType = VoidType.UNDEFINED
        )
    }

    "transforms ClassWithMap" {
        assertGeneratedCode(
            ClassWithMap::class, setOf(
                """
    interface ClassWithMap {
        values: { [key: string]: string };
    }
    """
            )
        )
    }

    "transforms ClassWithEnumMap" {
        assertGeneratedCode(
            ClassWithEnumMap::class, setOf(
                """
    type Direction = "North" | "West" | "South" | "East";
    """, """
    interface ClassWithEnumMap {
        values: { [key in Direction]: string };
    }
    """
            )
        )
    }
})


class ModuleOutput : StringSpec({

    // TODO: format support for the types
    "handles Module Output" {
        assertGeneratedModule(
            ClassWithNestedGenericMembers::class, mapOf(
                "me/ntrrgc/tsGenerator/tests/ClassWithNestedGenericMembers.d.ts" to
                        """
    import { Result } from './kotlin/Result.d.ts'
    export interface ClassWithNestedGenericMembers {
        xD: int[][][];
        xDD: Result<Result<Result<int>>>;
    }
                """.trimIndent(),
                "kotlin/Result.d.ts" to
                        """
    
    export interface Result<T> {
        isFailure: boolean;
        isSuccess: boolean;
    }
                """.trimIndent()
            )
        )
    }
})