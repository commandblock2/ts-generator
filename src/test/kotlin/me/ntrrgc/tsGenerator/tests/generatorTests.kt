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

import com.winterbe.expekt.should
import me.ntrrgc.tsGenerator.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.it
import java.beans.Introspector
import java.io.Serializable
import java.time.Instant
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.withNullability
import kotlin.reflect.jvm.kotlinFunction

fun assertGeneratedCode(klass: KClass<*>,
                        expectedOutput: Set<String>,
                        mappings: Map<KClass<*>, String> = mapOf(),
                        mappingsKtToTs: Map<KClass<*>, String> = mapOf(),
                        classTransformers: List<ClassTransformer> = listOf(),
                        ignoreSuperclasses: Set<KClass<*>> = setOf(),
                        voidType: VoidType = VoidType.NULL,
                        flags: List<Boolean> = listOf(true,true),
                        interfacesPrefixes: String = "")
{
    val generator = TypeScriptGenerator(listOf(klass), mappings, mappingsKtToTs, classTransformers,
        ignoreSuperclasses, intTypeName = "int", voidType = voidType, flags = flags, interfacesPrefixes = interfacesPrefixes)

    val expected = expectedOutput
        .map(TypeScriptDefinitionFactory::fromCode)
        .toSet()
    val actual = generator.individualDefinitions
        .map(TypeScriptDefinitionFactory::fromCode)
        .toSet()

    actual.should.equal(expected)
}

class PrefixeTestInterface()
class WithPrefixes(
    val interfaceI: PrefixeTestInterface
)



class ClassWithEmbeddedEnum(
    var storage: Storage = Storage.RAM,
) : ClassExtendsFromEmbedded() {
    enum class Storage { SSD, RAM }
}
abstract class ClassExtendsFromEmbedded : ExtendsEmbedded()
open class ExtendsEmbedded(
    override var type: String? = null,
) : IExtendsEmbedded, Serializable
interface IExtendsEmbedded{
    var type: String?
}

data class Range<out T>(val start: T? = null, val stop: T? = null) : Serializable

class CRange(
    val aRange: Range<String>
)

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
class ClassWithSet(
    val aSet: Set<String>
)
class ClassWithSetMap(
    val aSetMap: Set<Map<String,String>>
)
class ClassWithMapSet(
    val aMapSet: Map<String,Set<String>>
)
open class GenericClass<A, out B, out C: List<Any>>(
    val a: A,
    val b: List<B?>,
    val c: C,
    private val privateMember: A
)
open class BaseClass(val a: Int)
class DerivedClass(val b: List<String>): BaseClass(4)
class GenericDerivedClass<B>(a: Empty, b: List<B?>, c: ArrayList<String>): GenericClass<Empty, B, ArrayList<String>>(a, b, c, a)
class ClassWithMethods(val propertyMethod: () -> Int) {
    fun regularMethod() = 4
}
abstract class AbstractClass(val concreteProperty: String) {
    abstract val abstractProperty: Int
    abstract fun abstractMethod()
}

enum class Direction {
    NORTH,
    WEST,
    SOUTH,
    EAST
}

class ClassWithEnum(val direction: Direction)
data class DataClass(val prop: String)
class ClassWithAny(val required: Any, val optional: Any?)
class ClassWithMap(val values: Map<String, String>)
class ClassWithEnumMap(val values: Map<Direction, String>)

class Tests: Spek({
    it("handles Prefixes Class") {
        assertGeneratedCode(WithPrefixes::class, setOf("""
    interface IPrefixeTestInterface {
    }
    ""","""
    interface IWithPrefixes {
         interfaceI: IPrefixeTestInterface
    }
    """), interfacesPrefixes = "I" )
    }


    it("handles empty class") {
        assertGeneratedCode(Empty::class, setOf("""
interface Empty {
}
"""))
    }

    it("handles classes with a single member") {
        assertGeneratedCode(ClassWithMember::class, setOf("""
interface ClassWithMember {
    a: string
}
"""))
    }

    it("handles SimpleTypes") {
        assertGeneratedCode(SimpleTypes::class, setOf("""
    interface SimpleTypes {
        aString: string
        anInt: int
        aDouble: number
    }
    """))
    }

    it("handles ClassWithLists") {
        assertGeneratedCode(ClassWithLists::class, setOf("""
    interface ClassWithLists {
        aList: string[]
        anArrayList: string[]
    }
    """))
    }

    it("handles ClassWithArray") {
        assertGeneratedCode(ClassWithArray::class, setOf("""
    interface ClassWithArray {
        items: string[]
    }
    """))
    }

    val widget = """
    interface Widget {
        name: string
        value: int
    }
    """

    it("handles ClassWithDependencies") {
        assertGeneratedCode(ClassWithDependencies::class, setOf("""
    interface ClassWithDependencies {
        widget: Widget
    }
    """, widget))
    }

    it("handles ClassWithNullables") {
        assertGeneratedCode(ClassWithNullables::class, setOf("""
    interface ClassWithNullables {
        widget: Widget | null
    }
    """, widget))
    }

    it("handles ClassWithMixedNullables using mapping") {
        assertGeneratedCode(ClassWithMixedNullables::class, setOf("""
    interface ClassWithMixedNullables {
        count: int
        time: string | null
    }
    """), mappings = mapOf(Instant::class to "string"))
    }

    it("handles ClassWithMixedNullables using mapping and VoidTypes") {
        assertGeneratedCode(ClassWithMixedNullables::class, setOf("""
    interface ClassWithMixedNullables {
        count: int
        time: string | undefined
    }
    """), mappings = mapOf(Instant::class to "string"), voidType = VoidType.UNDEFINED)
    }

    it("handles ClassWithComplexNullables") {
        assertGeneratedCode(ClassWithComplexNullables::class, setOf("""
    interface ClassWithComplexNullables {
        maybeWidgets: (string | null)[] | null
        maybeWidgetsArray: (string | null)[] | null
    }
    """))
    }

    it("handles ClassWithNullableList") {
        assertGeneratedCode(ClassWithNullableList::class, setOf("""
    interface ClassWithNullableList {
        strings: string[] | null
    }
    """))
    }

    it("handles GenericClass") {
        assertGeneratedCode(GenericClass::class, setOf("""
    interface GenericClass<A, B, C extends any[]> {
        a: A
        b: (B | null)[]
        c: C
    }
    """))
    }

    it("handles DerivedClass") {
        assertGeneratedCode(DerivedClass::class, setOf("""
    interface DerivedClass extends BaseClass {
        b: string[]
    }
    """, """
    interface BaseClass {
        a: int
    }
    """))
    }

    it("handles GenericDerivedClass") {
        assertGeneratedCode(GenericDerivedClass::class, setOf("""
    interface GenericClass<A, B, C extends any[]> {
        a: A
        b: (B | null)[]
        c: C
    }
    ""","""
    interface Empty {
    }
    ""","""
    interface GenericDerivedClass<B> extends GenericClass<Empty, B, string[]> {
    }
    """))
    }

    it("handles ClassWithMethods") {
        assertGeneratedCode(ClassWithMethods::class, setOf("""
    interface ClassWithMethods {
    }
    """))
    }

    it("handles AbstractClass") {
        assertGeneratedCode(AbstractClass::class, setOf("""
    interface AbstractClass {
        concreteProperty: string
        abstractProperty: int
    }
    """))
    }

    it("handles ClassWithEnum") {
        assertGeneratedCode(ClassWithEnum::class, setOf("""
    interface ClassWithEnum {
        direction: Direction
    }
    """, """enum Direction {
    NORTH = 'NORTH',
    WEST = 'WEST',
    SOUTH = 'SOUTH',
    EAST = 'EAST',
}"""))
    }

    it("handles DataClass") {
        assertGeneratedCode(DataClass::class, setOf("""
    interface DataClass {
        prop: string
    }
    """))
    }

    it("handles ClassWithAny") {
        // Note: in TypeScript any includes null and undefined.
        assertGeneratedCode(ClassWithAny::class, setOf("""
    interface ClassWithAny {
        required: any
        optional: any
    }
    """))
    }

    it("handles ClassWithSet") {
        assertGeneratedCode(ClassWithSet::class, setOf("""
    interface ClassWithSet {
        aSet: Set<string>
    }
    """))
    }

    it("handles ClassWithSetMap") {
        assertGeneratedCode(ClassWithSetMap::class, setOf("""
    interface ClassWithSetMap {
        aSetMap: Set<{ [key: string]: string }>
    }
    """))
    }

    it("handles ClassWithMapSet") {
        assertGeneratedCode(ClassWithMapSet::class, setOf("""
    interface ClassWithMapSet {
        aMapSet: { [key: string]: Set<string> }
    }
    """))
    }

    it("handles ClassRange with Custom Name Generate") {
        assertGeneratedCode(Range::class, setOf("""
    interface CustomRange<T> {
        start: T | null
        stop: T | null
    }
    """), mappingsKtToTs = mapOf(Range::class to "CustomRange"))
    }

    it("handles ClassWithCustomClassNameProperties using mappingKtToTs") {
        assertGeneratedCode(CRange::class, setOf("""interface CustomRange<T> {
    start: T | null
    stop: T | null
}""", """interface CRange {
    aRange: CustomRange<string>
}"""), mappingsKtToTs = mapOf(Range::class to "CustomRange"))
    }

    it("handles DataStorage using mapping") {
        assertGeneratedCode(ClassWithEmbeddedEnum::class, setOf("""
           interface IExtendsEmbedded {
            type: string | null
        }""", """interface ExtendsEmbedded extends IExtendsEmbedded {
            type: string | null
        }""", """interface ClassExtendsFromEmbedded extends ExtendsEmbedded {
        }""", """enum DataStorage {
    SSD = 'SSD',
    RAM = 'RAM',
}""", """interface ClassWithEmbeddedEnum extends ClassExtendsFromEmbedded {
            storage: DataStorage
        }"""), mappingsKtToTs = mapOf(ClassWithEmbeddedEnum.Storage::class to "DataStorage"))
    }

    it("supports type mapping for classes") {
        assertGeneratedCode(ClassWithDependencies::class, setOf("""
interface ClassWithDependencies {
    widget: CustomWidget
}
"""), mappings = mapOf(Widget::class to "CustomWidget"))
    }

    it("supports type mapping for basic types") {
        assertGeneratedCode(DataClass::class, setOf("""
    interface DataClass {
        prop: CustomString
    }
    """), mappings = mapOf(String::class to "CustomString"))
    }

    it("supports transforming property names") {
        assertGeneratedCode(DataClass::class, setOf("""
    interface DataClass {
        PROP: string
    }
    """), classTransformers = listOf(
            object: ClassTransformer {
                /**
                 * Returns the property name that will be included in the
                 * definition.
                 *
                 * If it returns null, the value of the next class transformer
                 * in the pipeline is used.
                 */
                override fun transformPropertyName(propertyName: String, property: KProperty<*>, klass: KClass<*>): String {
                    return propertyName.toUpperCase()
                }
            }
        ))
    }

    it("supports transforming only some classes") {
        assertGeneratedCode(ClassWithDependencies::class, setOf("""
interface ClassWithDependencies {
    widget: Widget
}
""", """
interface Widget {
    NAME: string
    VALUE: int
}
"""), classTransformers = listOf(
            object : ClassTransformer {
                override fun transformPropertyName(propertyName: String, property: KProperty<*>, klass: KClass<*>): String {
                    return propertyName.toUpperCase()
                }
            }.onlyOnSubclassesOf(Widget::class)
        ))
    }

    it("supports transforming types") {
        assertGeneratedCode(DataClass::class, setOf("""
    interface DataClass {
        prop: int | null
    }
    """), classTransformers = listOf(
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

    it("supports filtering properties") {
        assertGeneratedCode(SimpleTypes::class, setOf("""
    interface SimpleTypes {
        aString: string
        aDouble: number
    }
    """), classTransformers = listOf(
            object : ClassTransformer {
                override fun transformPropertyList(properties: List<KProperty<*>>, klass: KClass<*>): List<KProperty<*>> {
                    return properties.filter { it.name != "anInt" }
                }
            }
        ))
    }

    it("supports filtering subclasses") {
        assertGeneratedCode(DerivedClass::class, setOf("""
    interface DerivedClass extends BaseClass {
        B: string[]
    }
    """, """
    interface BaseClass {
        A: int
    }
    """), classTransformers = listOf(
            object : ClassTransformer {
                override fun transformPropertyName(propertyName: String, property: KProperty<*>, klass: KClass<*>): String {
                    return propertyName.toUpperCase()
                }
            }.onlyOnSubclassesOf(BaseClass::class)
        ))
    }

    it("uses all transformers in pipeline") {
        assertGeneratedCode(SimpleTypes::class, setOf("""
    interface SimpleTypes {
        aString12: string
        aDouble12: number
        anInt12: int
    }
    """), classTransformers = listOf(
            object : ClassTransformer {
                override fun transformPropertyName(propertyName: String, property: KProperty<*>, klass: KClass<*>): String {
                    return propertyName + "1"
                }
            },
            object : ClassTransformer {
            },
            object : ClassTransformer {
                override fun transformPropertyName(propertyName: String, property: KProperty<*>, klass: KClass<*>): String {
                    return propertyName + "2"
                }
            }
        ))
    }

    it("handles JavaClass") {
        assertGeneratedCode(JavaClass::class, setOf("""
    interface JavaClass {
        name: string
        results: int[]
        multidimensional: string[][]
        finished: boolean
    }
    """))
    }

    it("handles JavaClassWithNullables") {
        assertGeneratedCode(JavaClassWithNullables::class, setOf("""
    interface JavaClassWithNullables {
        name: string
        results: int[]
        nextResults: int[] | null
    }
    """))
    }

    it("handles JavaClassWithNonnullAsDefault") {
        assertGeneratedCode(JavaClassWithNonnullAsDefault::class, setOf("""
    interface JavaClassWithNonnullAsDefault {
        name: string
        results: int[]
        nextResults: int[] | null
    }
    """))
    }

    it("handles JavaClassWithOptional") {
        assertGeneratedCode(JavaClassWithOptional::class, setOf("""
    interface JavaClassWithOptional {
        name: string
        surname: string | null
    }
    """), classTransformers = listOf(
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

    it("handles ClassWithComplexNullables when serializing as undefined") {
        assertGeneratedCode(ClassWithComplexNullables::class, setOf("""
    interface ClassWithComplexNullables {
        maybeWidgets: (string | undefined)[] | undefined
        maybeWidgetsArray: (string | undefined)[] | undefined
    }
    """), voidType = VoidType.UNDEFINED)
    }

    it("transforms ClassWithMap") {
        assertGeneratedCode(ClassWithMap::class, setOf("""
    interface ClassWithMap {
        values: { [key: string]: string }
    }
    """))
    }

    it("transforms ClassWithEnumMap") {
        assertGeneratedCode(ClassWithEnumMap::class, setOf("""enum Direction {
    NORTH = 'NORTH',
    WEST = 'WEST',
    SOUTH = 'SOUTH',
    EAST = 'EAST',
}""", """
    interface ClassWithEnumMap {
        values: { [key in Direction]: string }
    }
    """))
    }
})
